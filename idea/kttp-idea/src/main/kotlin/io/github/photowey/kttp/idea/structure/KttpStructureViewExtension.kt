/*
 * Copyright (c) 2026-present The Kttp Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.photowey.kttp.idea.structure

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import io.github.photowey.kttp.idea.icons.KttpIcons
import io.github.photowey.kttp.idea.lang.KttpFileType
import java.util.regex.Pattern
import javax.swing.Icon

/**
 * Provides a lightweight regex-based structure view for .kttp.kts method/URL text matches.
 * It does not evaluate URLs or filter pseudo-calls inside comments and strings.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpStructureViewExtension : PsiStructureViewFactory {

    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (!psiFile.name.endsWith("." + KttpFileType.INSTANCE.defaultExtension)) return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                KttpStructureViewModel(psiFile, editor)
        }
    }
}

private val REQUEST_REGEX = Pattern.compile("""(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s*\(\s*["']([^"']+)["']\s*\)""")

/**
 * Connects the script structure root to IDEA's structure-view model.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpStructureViewModel(psiFile: PsiFile, editor: Editor?) :
    com.intellij.ide.structureView.TextEditorBasedStructureViewModel(editor, psiFile),
    StructureViewModel.ElementInfoProvider {

    override fun getRoot(): StructureViewTreeElement = KttpStructureViewRoot(psiFile)
    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false
    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean = element is KttpRequestTreeElement
}

/**
 * Extracts request method and URL text matches for the script structure tree.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
private class KttpStructureViewRoot(private val psiFile: PsiFile) : StructureViewTreeElement {
    override fun getValue(): Any = psiFile
    override fun getChildren(): Array<TreeElement> {
        val text = psiFile.text ?: return emptyArray()
        val matcher = REQUEST_REGEX.matcher(text)
        val items = mutableListOf<KttpRequestTreeElement>()
        while (matcher.find()) {
            items.add(
                KttpRequestTreeElement(
                    psiFile,
                    matcher.group(1)!!.uppercase(),
                    matcher.group(2)!!.trim(),
                    matcher.start()
                )
            )
        }
        return items.toTypedArray()
    }

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = psiFile.name
        override fun getLocationString(): String? = null
        override fun getIcon(unused: Boolean): Icon? = null
    }

    override fun navigate(requestFocus: Boolean) {}
    override fun canNavigate(): Boolean = false
    override fun canNavigateToSource(): Boolean = false
}

/**
 * Displays a matched request and navigates to its source offset.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
private class KttpRequestTreeElement(
    private val psiFile: PsiFile,
    private val method: String,
    private val url: String,
    private val offset: Int
) : StructureViewTreeElement {
    override fun getValue(): Any = this
    override fun getChildren(): Array<TreeElement> = emptyArray()
    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = "$method $url"
        override fun getLocationString(): String? = null
        override fun getIcon(unused: Boolean): Icon? = KttpIcons.iconForMethod(method)
    }

    override fun navigate(requestFocus: Boolean) {
        psiFile.virtualFile?.let { vf ->
            com.intellij.ide.actions.OpenFileAction.openFile(vf, psiFile.project)
            psiFile.project.getService(com.intellij.openapi.fileEditor.FileEditorManager::class.java)
                ?.selectedTextEditor?.caretModel?.moveToOffset(offset)
        }
    }

    override fun canNavigate(): Boolean = true
    override fun canNavigateToSource(): Boolean = true
}
