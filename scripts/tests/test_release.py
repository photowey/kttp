"""Release gates exercise malformed inputs and installable archive contents."""

import hashlib
import io
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import release


def jar_bytes(entries):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as archive:
        for name, value in entries.items():
            archive.writestr(name, value)
    return buffer.getvalue()


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / 'gradle.properties').write_text(
            'project.version=1.0.0-SNAPSHOT\nintellij.idea.plugin.id=io.github.photowey.kttp\n'
        )
        notes = self.root / 'docs/releases/v1.0.0.md'
        notes.parent.mkdir(parents=True)
        notes.write_text('# Kttp 1.0.0\n\nInitial release.\n')
        self.cli = self.root / 'core/kttp-cli/build/libs/kttp-cli-1.0.0.jar'
        self.cli.parent.mkdir(parents=True)
        self.cli_data = jar_bytes({
            'META-INF/MANIFEST.MF': 'Manifest-Version: 1.0\r\nMain-Class: io.github.photowey.kttp.cli.command.KttpCommandKt\r\n\r\n',
            'io/github/photowey/kttp/cli/command/KttpCommandKt.class': b'command',
            'kotlin/Unit.class': b'unit',
            'okhttp3/OkHttpClient.class': b'okhttp',
            'kotlin/script/experimental/jvmhost/BasicJvmScriptingHost.class': b'host',
            'org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment.class': b'compiler',
        })
        self.cli.write_bytes(self.cli_data)
        self.plugin = self.root / 'idea/kttp-idea/build/distributions/kttp-idea.zip'
        self.plugin.parent.mkdir(parents=True)
        self.make_plugin()
        self.site = self.root / 'site'
        (self.site / 'zh').mkdir(parents=True)
        (self.site / 'index.html').write_text('English')
        (self.site / 'zh/index.html').write_text('中文')

    def make_plugin(self, version='1.0.0', bundled_cli=None, plugin_id='io.github.photowey.kttp'):
        descriptor = f'<idea-plugin><id>{plugin_id}</id><version>{version}</version></idea-plugin>'
        self.plugin.write_bytes(jar_bytes({
            'kttp-idea/lib/kttp-idea.jar': jar_bytes({'META-INF/plugin.xml': descriptor}),
            'kttp-idea/cli/kttp-cli.jar': self.cli_data if bundled_cli is None else bundled_cli,
        }))

    def test_valid_tag_matches_the_normalized_gradle_artifact_version(self):
        self.assertEqual('1.0.0', release.validate_tag(self.root, 'v1.0.0'))

    def test_invalid_and_mismatched_tags_are_rejected(self):
        for tag in ['1.0.0', 'v01.0.0', 'v1.00.0', 'v1.0.01', 'v1.0', 'v1.0.0-rc.1',
                    'v1.0.0+build', 'v1.0.0\n', 'v1.0.1', 'v1.0.0/../../main']:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.validate_tag(self.root, tag)

    def test_missing_and_blank_release_notes_are_rejected(self):
        notes = self.root / 'docs/releases/v1.0.0.md'
        notes.unlink()
        with self.assertRaises(ValueError):
            release.validate_tag(self.root, 'v1.0.0')
        notes.write_text(' \n\t')
        with self.assertRaises(ValueError):
            release.validate_tag(self.root, 'v1.0.0')

    def test_duplicate_or_nonstable_project_versions_are_rejected(self):
        for text in ['project.version=1.0.0\nproject.version=1.0.0\n',
                     'project.version=1.0.0-rc.1\n', 'project.version=01.0.0\n']:
            (self.root / 'gradle.properties').write_text(text)
            with self.subTest(text=text), self.assertRaises(ValueError):
                release.project_version(self.root)

    def test_stage_copies_only_valid_installable_distributions(self):
        output = self.root / 'staged'
        release.stage(self.root, output)
        self.assertEqual({'kttp-idea-1.0.0.zip', 'kttp-cli-1.0.0.jar'}, {p.name for p in output.iterdir()})
        self.assertEqual(self.cli_data, (output / 'kttp-cli-1.0.0.jar').read_bytes())

    def test_plain_or_corrupted_cli_is_rejected(self):
        for data in [b'not a jar', jar_bytes({'META-INF/MANIFEST.MF': 'Main-Class: Wrong\n'})]:
            self.cli.write_bytes(data)
            self.make_plugin(bundled_cli=data)
            with self.subTest(data=data[:20]), self.assertRaises(ValueError):
                release.stage(self.root, self.root / 'staged')

    def test_plugin_must_have_matching_id_version_and_exact_bundled_cli(self):
        for kwargs in [{'version': '9.9.9'}, {'plugin_id': 'another.plugin'}, {'bundled_cli': b'stale cli'}]:
            self.make_plugin(**kwargs)
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                release.stage(self.root, self.root / 'staged')

    def test_missing_plugin_cli_and_truncated_plugin_are_rejected(self):
        for data in [b'corrupt archive', jar_bytes({'kttp-idea/lib/kttp-idea.jar': b'not a jar'})]:
            self.plugin.write_bytes(data)
            with self.subTest(data=data[:20]), self.assertRaises(ValueError):
                release.stage(self.root, self.root / 'staged')

    def test_release_contains_docs_and_correct_checksums_for_exact_assets(self):
        staged, output = self.root / 'staged', self.root / 'release'
        release.stage(self.root, staged)
        release.assemble(self.root, 'v1.0.0', staged, self.site, output)
        expected = {'kttp-idea-1.0.0.zip', 'kttp-cli-1.0.0.jar', 'kttp-docs-1.0.0.tar.gz'}
        lines = (output / 'SHA256SUMS').read_text().splitlines()
        digests = {name: digest for digest, name in (line.split('  ') for line in lines)}
        self.assertEqual(expected, set(digests))
        for name, digest in digests.items():
            self.assertEqual(hashlib.sha256((output / name).read_bytes()).hexdigest(), digest)
        with tarfile.open(output / 'kttp-docs-1.0.0.tar.gz') as archive:
            self.assertIn('kttp-docs-1.0.0/index.html', archive.getnames())
            self.assertIn('kttp-docs-1.0.0/zh/index.html', archive.getnames())

    def test_missing_translation_and_unexpected_staged_assets_are_rejected(self):
        staged = self.root / 'staged'
        release.stage(self.root, staged)
        (self.site / 'zh/index.html').unlink()
        with self.assertRaises(ValueError):
            release.assemble(self.root, 'v1.0.0', staged, self.site, self.root / 'release')
        (self.site / 'zh/index.html').write_text('中文')
        (staged / 'leftover.zip').write_bytes(b'stale')
        with self.assertRaises(ValueError):
            release.assemble(self.root, 'v1.0.0', staged, self.site, self.root / 'release')

    def test_existing_output_files_are_never_silently_published_or_removed(self):
        output = self.root / 'staged'
        output.mkdir()
        (output / 'user-data').write_text('keep')
        with self.assertRaises(ValueError):
            release.stage(self.root, output)
        self.assertEqual('keep', (output / 'user-data').read_text())


if __name__ == '__main__':
    unittest.main()
