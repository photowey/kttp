"""Validate Kttp release inputs and package the distributions produced by CI."""

import argparse
import hashlib
import io
from pathlib import Path
import re
import shutil
import tarfile
import xml.etree.ElementTree as ET
import zipfile

VERSION = r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)'
CLI_MAIN = 'io.github.photowey.kttp.cli.command.KttpCommandKt'
CLI_ENTRIES = {
    CLI_MAIN.replace('.', '/') + '.class',
    'kotlin/Unit.class',
    'okhttp3/OkHttpClient.class',
    'kotlin/script/experimental/jvmhost/BasicJvmScriptingHost.class',
    'org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment.class',
}


def property_value(root: Path, name: str) -> str:
    """Read a single authoritative root Gradle property; ambiguous declarations fail."""
    text = (root / 'gradle.properties').read_text(encoding='utf-8')
    values = re.findall(r'^\s*' + re.escape(name) + r'\s*=\s*(.*?)\s*$', text, re.MULTILINE)
    if len(values) != 1 or not values[0]:
        raise ValueError(f'Expected exactly one nonempty {name} in gradle.properties')
    return values[0]


def project_version(root: Path) -> str:
    """Mirror Gradle's existing stable artifact version without rewriting project files."""
    version = property_value(root, 'project.version').removesuffix('-SNAPSHOT')
    if re.fullmatch(VERSION, version) is None:
        raise ValueError('project.version must be X.Y.Z or X.Y.Z-SNAPSHOT without leading zeroes')
    return version


def validate_tag(root: Path, tag: str) -> str:
    """Require a matching stable version and maintained release notes before publishing."""
    if re.fullmatch('v' + VERSION, tag) is None:
        raise ValueError('Release tags must use stable vX.Y.Z versions without leading zeroes')
    version = project_version(root)
    if tag != 'v' + version:
        raise ValueError(f'Tag {tag} does not match the Gradle artifact version {version}')
    notes = root / 'docs/releases' / f'{tag}.md'
    if not notes.is_file() or not notes.read_text(encoding='utf-8').strip():
        raise ValueError(f'Missing or empty release notes: {notes}')
    return version


def sha256(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def validate_distributions(root: Path, plugin: Path, cli: Path, version: str) -> None:
    """Check runtime dependencies, plugin identity/version and the exact bundled CLI bytes."""
    try:
        with zipfile.ZipFile(cli) as jar:
            manifest = jar.read('META-INF/MANIFEST.MF').decode('utf-8')
            if CLI_ENTRIES - set(jar.namelist()) or f'Main-Class: {CLI_MAIN}' not in manifest.splitlines():
                raise ValueError('The CLI must be the executable shadowJar with compiler and runtime dependencies')
        with zipfile.ZipFile(plugin) as archive:
            names = archive.namelist()
            roots = {name.split('/')[0] for name in names}
            if len(roots) != 1 or len(names) != len(set(names)):
                raise ValueError('Plugin ZIP must have a single plugin root and no duplicate entries')
            plugin_root = roots.pop()
            with archive.open(f'{plugin_root}/cli/kttp-cli.jar') as stream:
                if hashlib.file_digest(stream, 'sha256').hexdigest() != sha256(cli):
                    raise ValueError('Plugin bundled CLI differs from the standalone shadowJar')
            plugin_jar = archive.read(f'{plugin_root}/lib/kttp-idea.jar')
        with zipfile.ZipFile(io.BytesIO(plugin_jar)) as jar:
            descriptor = ET.fromstring(jar.read('META-INF/plugin.xml'))
        if descriptor.findtext('id', '').strip() != property_value(root, 'intellij.idea.plugin.id'):
            raise ValueError('Plugin ID does not match gradle.properties')
        if descriptor.findtext('version', '').strip() != version:
            raise ValueError('Plugin ZIP version does not match the Gradle artifact version')
    except (OSError, KeyError, zipfile.BadZipFile, ET.ParseError, UnicodeDecodeError) as error:
        raise ValueError(f'Invalid or incomplete plugin/CLI distribution: {error}') from error


def empty_output(output: Path) -> None:
    """Reject stale assets instead of deleting caller data or accidentally publishing it."""
    if output.exists() and (not output.is_dir() or any(output.iterdir())):
        raise ValueError(f'Output directory must be empty: {output}')
    output.mkdir(parents=True, exist_ok=True)


def stage(root: Path, output: Path) -> None:
    """Copy validated Gradle outputs to the flat artifact interface shared by CI and Release."""
    version = project_version(root)
    plugin = root / 'idea/kttp-idea/build/distributions/kttp-idea.zip'
    cli = root / f'core/kttp-cli/build/libs/kttp-cli-{version}.jar'
    validate_distributions(root, plugin, cli, version)
    empty_output(output)
    shutil.copyfile(plugin, output / f'kttp-idea-{version}.zip')
    shutil.copyfile(cli, output / f'kttp-cli-{version}.jar')


def assemble(root: Path, tag: str, distributions: Path, site: Path, output: Path) -> None:
    """Release only validated CI packages and a complete bilingual documentation build."""
    version = validate_tag(root, tag)
    expected = {f'kttp-idea-{version}.zip', f'kttp-cli-{version}.jar'}
    if not distributions.is_dir() or {p.name for p in distributions.iterdir()} != expected:
        raise ValueError('The distribution artifact must contain exactly the versioned plugin ZIP and CLI JAR')
    plugin, cli = distributions / f'kttp-idea-{version}.zip', distributions / f'kttp-cli-{version}.jar'
    validate_distributions(root, plugin, cli, version)
    for index in [site / 'index.html', site / 'zh/index.html']:
        if not index.is_file() or index.stat().st_size == 0:
            raise ValueError(f'Missing bilingual documentation output: {index}')
    empty_output(output)
    shutil.copyfile(plugin, output / plugin.name)
    shutil.copyfile(cli, output / cli.name)
    with tarfile.open(output / f'kttp-docs-{version}.tar.gz', 'w:gz') as archive:
        archive.add(site, arcname=f'kttp-docs-{version}')
    assets = sorted(output.iterdir())
    (output / 'SHA256SUMS').write_text(
        ''.join(f'{sha256(asset)}  {asset.name}\n' for asset in assets), encoding='utf-8'
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    commands = parser.add_subparsers(dest='command', required=True)
    validation = commands.add_parser('validate', help='Validate stable tag, Gradle version and release notes')
    validation.add_argument('--tag', required=True)
    staging = commands.add_parser('stage', help='Validate and stage real Gradle package outputs')
    staging.add_argument('--output', required=True, type=Path)
    packaging = commands.add_parser('assemble', help='Assemble CI packages, docs archive and checksums')
    packaging.add_argument('--tag', required=True)
    packaging.add_argument('--distribution-dir', required=True, type=Path)
    packaging.add_argument('--site-dir', required=True, type=Path)
    packaging.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    try:
        if args.command == 'validate':
            print(validate_tag(args.root, args.tag))
        elif args.command == 'stage':
            stage(args.root, args.output)
        else:
            assemble(args.root, args.tag, args.distribution_dir, args.site_dir, args.output)
    except (ValueError, OSError) as error:
        parser.error(str(error))


if __name__ == '__main__':
    main()
