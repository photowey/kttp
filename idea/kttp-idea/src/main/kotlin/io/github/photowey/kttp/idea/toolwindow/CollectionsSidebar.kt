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

package io.github.photowey.kttp.idea.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import io.github.photowey.kttp.idea.gui.GuiDraft
import io.github.photowey.kttp.idea.gui.GuiDraftService
import io.github.photowey.kttp.idea.gui.GuiMode
import io.github.photowey.kttp.idea.path.KttpFileNavigation
import io.github.photowey.kttp.idea.script.KttpRequestDeclaration
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.SwingConstants
import javax.swing.Timer
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Collections 侧边栏：显示 Drafts / Scripts / Responses。
 * @param onRequestSelected (declaration, sourceFile, declarationIndex) 请求选中时加载到右侧编辑器；用于脚本变更时联动
 * @param onDraftSelected Draft 选中时加载到右侧编辑器
 * @param onScriptFileChanged 脚本文件内容变更时回调，用于刷新右侧编辑器（脚本 ↔ UI 联动）
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/21
 */
class CollectionsSidebar(
    private val project: Project,
    private val onRequestSelected: (KttpRequestDeclaration?, VirtualFile?, Int?, Boolean) -> Unit,
    private val onDraftSelected: (GuiDraft) -> Unit,
    private val onScriptFileChanged: (VirtualFile) -> Unit = {},
    private val onAddRequestToScript: (VirtualFile?, (VirtualFile) -> Unit) -> Unit = { _, _ -> }
) : JPanel(BorderLayout()) {

    private val draftService = project.getService(GuiDraftService::class.java)

    /**
     * 外部调用：通知脚本文件已变更（用于 UI 同步回脚本后刷新侧边栏）
     */
    fun notifyScriptFileChanged(file: VirtualFile) {
        onScriptFileChanged(file)
    }

    private val rootNode = DefaultMutableTreeNode("Collections")
    private val treeModel = DefaultTreeModel(rootNode)

    private val parseCache = SidebarParseCache(project)
    private val discovery = SidebarFileDiscovery(project)
    private val responses = SidebarResponses(project, discovery)

    private val tree = Tree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        rowHeight = 0
        cellRenderer = SidebarRequestTreeRenderer(parseCache::summary)
    }

    private val emptyLabel = JLabel("Nothing to Show", SwingConstants.CENTER)
    private val emptyPanel = JPanel(BorderLayout()).apply {
        add(emptyLabel, BorderLayout.CENTER)
    }

    private val cardLayout = CardLayout()
    private val requestCardPanel = JPanel(cardLayout).apply {
        add(emptyPanel, "empty")
        add(JBScrollPane(tree).apply { border = JBUI.Borders.empty() }, "tree")
    }

    private val searchControls = SidebarSearchControls()
    private val collectionRefreshTimer = Timer(180) { loadCollectionsAsync() }.apply {
        isRepeats = false
    }
    private var selectedScriptFile: VirtualFile? = null
    private val collectionLoadSequence = AtomicInteger()
    private var collectionsLoaded = false
    private var pendingSelectionKey: String? = null
    private val selection = SidebarTreeSelection(tree, rootNode) { selectedScriptFile = it }
    private val menus = SidebarContextMenus(
        project, draftService, onDraftSelected,
        { onRequestSelected(null, null, null, false) }, ::refreshCollections,
        { pendingSelectionKey = it }, tree::clearSelection, { invalidateCache(it) },
        onScriptFileChanged, { reloadFileNode(it) })
    private val sourcesHeader = JLabel("Sources", SwingConstants.LEADING)

    /**
     * 占位子节点：使文件节点显示展开箭头，展开后替换为真实请求节点
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/21
     */
    internal data object PlaceholderNode {
        override fun toString(): String = "..."
    }

    init {
        preferredSize = JBUI.size(280, 0)
        border = JBUI.Borders.empty()

        val buttonPanel = JPanel(BorderLayout(4, 4)).apply {
            val draftActions = JPanel(BorderLayout(4, 0)).apply {
                add(JButton("New Draft", AllIcons.General.Add).apply {
                    toolTipText = "Create a new detached GUI draft"
                    addActionListener { onRequestSelected(null, null, null, false) }
                }, BorderLayout.CENTER)
                add(JButton(AllIcons.Actions.Refresh).apply {
                    isFocusable = false
                    toolTipText = "Refresh drafts and scripts"
                    addActionListener { refreshCollections() }
                }, BorderLayout.EAST)
            }
            add(draftActions, BorderLayout.NORTH)
            add(JButton("Add Request", AllIcons.General.Add).apply {
                toolTipText = "Append a request to the selected script, or create a temporary script"
                addActionListener { addRequestToScript() }
            }, BorderLayout.SOUTH)
        }
        val controlsPanel = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            border = JBUI.Borders.emptyBottom(8)
            add(buttonPanel)
            add(searchControls.apply { border = JBUI.Borders.emptyTop(8) })
        }

        // 上：Drafts + Scripts 树
        val requestTopPanel = JPanel(BorderLayout()).apply {
            add(controlsPanel, BorderLayout.NORTH)
            add(requestCardPanel, BorderLayout.CENTER)
        }
        sourcesHeader.apply {
            useBoldUiFont()
            border = JBUI.Borders.empty(2, 2, 10, 2)
        }
        val requestSection = JPanel(BorderLayout()).apply {
            add(sourcesHeader, BorderLayout.NORTH)
            add(requestTopPanel, BorderLayout.CENTER)
        }


        // 左侧上下分栏：上请求、下响应
        add(WorkspaceUi.split(
            true, "Kttp.SourcesResponses.Proportion", 0.64f,
            WorkspaceUi.surface(requestSection), WorkspaceUi.surface(responses.component)
        ), BorderLayout.CENTER)

        // 初始显示空状态（等待文件扫描）
        cardLayout.show(requestCardPanel, "empty")
        emptyLabel.text = "Loading..."

        // 事件监听
        setupTreeListeners()
        searchControls.search.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = collectionRefreshTimer.restart()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = collectionRefreshTimer.restart()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = collectionRefreshTimer.restart()
        })
        searchControls.filter.addActionListener { collectionRefreshTimer.restart() }

        // 文件变化监听
        setupFileChangeListener()

        // 在非 Dumb 模式下再加载树，避免 Analyzing Project / 索引时读 PSI 导致侧边栏空白或异常
        DumbService.getInstance(project).runWhenSmart {
            loadCollectionsAsync()
            refreshResponseCollections()
        }
        Disposer.register(project, Disposable {
            ApplicationManager.getApplication().invokeLater { collectionRefreshTimer.stop() }
        })
    }

    private fun setupFileChangeListener() {
        SidebarVfsListener(project, discovery, { invalidateCache(it) }, { file ->
            reloadFileNode(file)
            onScriptFileChanged(file)
        }, ::refreshCollections, ::refreshResponseCollections).install()
    }

    /**
     * 在分组下查找对应的文件节点并重新解析；解析完成后在 EDT 调用 [onReloaded]（用于展开并选中新节点）。
     * @param sync 为 true 时在当前线程同步解析并更新树（Add Request 后立即刷新）；为 false 时后台解析。
     */
    private fun reloadFileNode(file: VirtualFile, onReloaded: (() -> Unit)? = null, sync: Boolean = false) {
        val node = selection.find("file:${file.path}")?.lastPathComponent as? DefaultMutableTreeNode ?: return
        if (sync) {
            parseRequestsForFileSync(node, file)
            onReloaded?.invoke()
        } else parseRequestsForFile(node, file, onReloaded)
    }

    /** 重新扫描并刷新侧边栏树（新建临时文件后调用） */
    fun refreshCollections() = onOwner {
        collectionRefreshTimer.stop()
        loadCollectionsAsync()
    }

    private fun addRequestToScript() {
        val selectedFile = selectedScriptFile?.takeIf { it.isValid && it.name.endsWith(".kttp.kts") }
        onAddRequestToScript(selectedFile) { file ->
            if (project.isDisposed) return@onAddRequestToScript
            pendingSelectionKey = "file:${file.path}"
            KttpFileNavigation.open(project, file)
            refreshCollections()
        }
    }

    private fun setupTreeListeners() {
        tree.addTreeSelectionListener {
            if (selection.restoring) return@addTreeSelectionListener
            val path = tree.selectionPath ?: run {
                selectedScriptFile = null
                return@addTreeSelectionListener
            }
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            when (val userObject = node.userObject) {
                is DraftNode -> {
                    selectedScriptFile = null
                    onDraftSelected(userObject.draft)
                }

                is FileNode -> selectedScriptFile = userObject.file
                is RequestNode -> {
                    val fileNode = path.parentPath?.lastPathComponent as? DefaultMutableTreeNode
                    val sourceFile = (fileNode?.userObject as? FileNode)?.file
                    selectedScriptFile = sourceFile
                    val index = fileNode?.getIndex(node)?.takeIf { it >= 0 }
                    onRequestSelected(userObject.declaration, sourceFile, index, true)
                }

                else -> selectedScriptFile = null
            }
        }

        // Double-click a script to open its editor; row selection is handled by TreeSelectionListener.
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val path = tree.getPathForLocation(e.x, e.y) ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    val userObject = node.userObject
                    if (userObject is FileNode) {
                        tree.expandPath(path)
                        KttpFileNavigation.open(project, userObject.file)
                    }
                }
            }
        })

        // 展开节点时：若为文件节点且仅有占位子节点，则移除占位并解析请求（树形：文件 → GET/POST）
        tree.addTreeExpansionListener(object : javax.swing.event.TreeExpansionListener {
            override fun treeExpanded(event: javax.swing.event.TreeExpansionEvent) {
                val path = event.path
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val userObject = node.userObject
                if (userObject is FileNode) {
                    if (node.childCount == 1 && (node.getChildAt(0) as? DefaultMutableTreeNode)?.userObject == PlaceholderNode) {
                        parseRequestsForFile(node, userObject.file)
                    }
                }
            }

            override fun treeCollapsed(event: javax.swing.event.TreeExpansionEvent) {}
        })

        // 右键文件节点：显示“删除”菜单
        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) showTreeContextMenu(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) showTreeContextMenu(e)
            }
        })
    }

    private fun showTreeContextMenu(event: MouseEvent) {
        val path = tree.getPathForLocation(event.x, event.y) ?: return
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        tree.selectionPath = path
        JPopupMenu().apply {
            menus.addItems(this, node.userObject)
            if (componentCount > 0) show(tree, event.x, event.y)
        }
    }

    internal fun addDraftContextMenuItems(menu: JPopupMenu, draft: GuiDraft) {
        menus.addDraftItems(menu, draft)
    }

    private fun loadCollectionsAsync() {
        if (project.isDisposed) return
        val query = searchControls.search.text.trim().lowercase()
        val filter = searchControls.filter.selectedItem as? FileProjectionFilter ?: FileProjectionFilter.ALL
        val sequence = collectionLoadSequence.incrementAndGet()
        val version = parseCache.captureVersion()
        ApplicationSidebarTaskScheduler.background {
            try {
                if (project.isDisposed) return@background
                val drafts =
                    draftService.listDrafts().filter { query.isBlank() || it.title.lowercase().contains(query) }
                val discovered = discovery.scripts()
                fun matching(files: List<VirtualFile>) =
                    files.filter { query.isBlank() || it.name.lowercase().contains(query) }

                val files = SidebarFileGroups(matching(discovered.project), matching(discovered.kttp))
                val parsed = (files.project + files.kttp).distinct().mapNotNull { parseCache.read(it, version) }
                ApplicationSidebarTaskScheduler.publish {
                    if (!project.isDisposed && sequence == collectionLoadSequence.get()) {
                        parsed.forEach { parseCache.publish(it) }
                        applyCollections(drafts, files, filter, query)
                    }
                }
            } catch (error: IOException) {
                ApplicationSidebarTaskScheduler.publish {
                    if (!project.isDisposed && sequence == collectionLoadSequence.get()) {
                        Messages.showErrorDialog(
                            project,
                            error.message ?: "Unable to load Draft storage",
                            "Draft storage error"
                        )
                    }
                }
            }
        }
    }

    private fun applyCollections(
        drafts: List<GuiDraft>,
        files: SidebarFileGroups,
        filter: FileProjectionFilter,
        query: String
    ) {
        val state = selection.capture()
        val groups = SidebarFileGroups(
            sortAndFilterFiles(files.project.filter { it.isValid }, filter),
            sortAndFilterFiles(files.kttp.filter { it.isValid }, filter)
        )
        sourcesHeader.text = "Sources (${drafts.size + groups.project.size + groups.kttp.size})"
        rootNode.removeAllChildren()
        if (drafts.isEmpty() && groups.project.isEmpty() && groups.kttp.isEmpty()) {
            treeModel.reload(rootNode)
            tree.clearSelection()
            selectedScriptFile = null
            cardLayout.show(requestCardPanel, "empty")
            emptyLabel.text = if (query.isNotBlank() || filter != FileProjectionFilter.ALL) {
                "No matches. Adjust the search or filter."
            } else "No drafts or .kttp.kts files found"
        } else {
            val groupNodes = createCollectionGroups(drafts, groups)
            treeModel.reload(rootNode)
            groupNodes.forEach { (group, node) ->
                if (!collectionsLoaded || group in state.expandedGroups) tree.expandPath(TreePath(node.path))
            }
            state.expandedFiles.forEach { path -> selection.find("file:$path")?.let(tree::expandPath) }
            SidebarPresentation.preferredSelectionKey(state.selectedKey, pendingSelectionKey)?.let { key ->
                selection.find(key)?.let {
                    selection.restore(it)
                    if (key == pendingSelectionKey) pendingSelectionKey = null
                }
            }
            cardLayout.show(requestCardPanel, "tree")
        }
        collectionsLoaded = true
    }

    private fun createCollectionGroups(
        drafts: List<GuiDraft>,
        groups: SidebarFileGroups
    ): Map<GroupNode, DefaultMutableTreeNode> {
        val draftsNode = DefaultMutableTreeNode(GroupNode.DRAFTS).apply {
            drafts.forEach { add(DefaultMutableTreeNode(DraftNode(it))) }
        }
        val projectNode = DefaultMutableTreeNode(GroupNode.PROJECT).apply {
            groups.project.forEach { add(createFileTreeNode(it)) }
        }
        val kttpNode = DefaultMutableTreeNode(GroupNode.KTPP).apply {
            groups.kttp.forEach { add(createFileTreeNode(it)) }
        }
        return linkedMapOf(GroupNode.DRAFTS to draftsNode, GroupNode.PROJECT to projectNode, GroupNode.KTPP to kttpNode)
            .also { nodes -> nodes.values.forEach(rootNode::add) }
    }

    private fun createFileTreeNode(file: VirtualFile): DefaultMutableTreeNode {
        val fileNode = DefaultMutableTreeNode(FileNode(file))
        val cachedRequests = parseCache.requests(file.path)
        if (cachedRequests == null) {
            fileNode.add(DefaultMutableTreeNode(PlaceholderNode))
        } else {
            cachedRequests.forEach { fileNode.add(createRequestTreeNode(it)) }
        }
        return fileNode
    }

    private fun sortAndFilterFiles(files: List<VirtualFile>, filter: FileProjectionFilter): List<VirtualFile> {
        return files
            .filter { file ->
                val summary = parseCache.summary(file.path) ?: FileProjectionSummary()
                RequestProjectionPresentation.matchesFilter(summary, filter)
            }
            .sortedWith(
                compareBy<VirtualFile> { file ->
                    RequestProjectionPresentation.sortWeight(parseCache.summary(file.path) ?: FileProjectionSummary())
                }.thenBy { file ->
                    file.name.lowercase()
                }
            )
    }

    fun refreshResponseCollections() = onOwner { responses.refresh() }

    /**
     * Identifies the draft, project, and console groups in the sidebar tree.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/21
     */
    internal enum class GroupNode(val displayName: String) {
        DRAFTS("Drafts"), PROJECT("Project"), KTPP("Kttp");

        override fun toString(): String = displayName
    }

    /**
     * 同步解析文件中的请求并更新树（当前线程，用于 Add Request 后立即刷新侧边栏）。
     */
    private fun parseRequestsForFileSync(fileNode: DefaultMutableTreeNode, file: VirtualFile) {
        replaceRequests(fileNode, parseCache.parseSync(file))
    }

    private fun parseRequestsForFile(
        fileNode: DefaultMutableTreeNode,
        file: VirtualFile,
        onParsed: (() -> Unit)? = null
    ) {
        parseCache.request(file) { displayInfo ->
            if (fileNode.root === rootNode && (fileNode.userObject as? FileNode)?.file == file) {
                replaceRequests(fileNode, displayInfo)
                onParsed?.invoke()
            }
        }
    }

    private fun replaceRequests(fileNode: DefaultMutableTreeNode, requests: List<SidebarRequestDisplayInfo>) {
        val path = TreePath(fileNode.path)
        val expanded = tree.isExpanded(path)
        val selectedKey = selection.capture().selectedKey
        fileNode.removeAllChildren()
        requests.forEach { fileNode.add(createRequestTreeNode(it)) }
        treeModel.reload(fileNode)
        if (expanded) tree.expandPath(path)
        selectedKey?.let(selection::find)?.let(selection::restore)
    }

    private fun createRequestTreeNode(info: SidebarRequestDisplayInfo): DefaultMutableTreeNode =
        DefaultMutableTreeNode(RequestNode(info.declaration, info.method, info.url, info.mode, info.tooltip))

    /**
     * 清除文件缓存（文件变化时调用）
     */
    fun invalidateCache(filePath: String? = null) = onOwner { parseCache.invalidate(filePath) }

    private fun onOwner(action: () -> Unit) {
        if (ApplicationManager.getApplication().isDispatchThread) {
            if (!project.isDisposed) action()
        } else ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) action() }
    }

    /**
     * Wraps a saved response file for display in the sidebar tree.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/21
     */
    internal data class ResponseFileNode(val file: VirtualFile) {
        override fun toString(): String = file.name
    }

    /**
     * Wraps a saved GUI draft for display in the sidebar tree.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/21
     */
    data class DraftNode(val draft: GuiDraft) {
        override fun toString(): String = draft.title
    }

    /**
     * Wraps a script file for display in the sidebar tree.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/21
     */
    data class FileNode(val file: VirtualFile) {
        override fun toString(): String = file.name
    }

    /**
     * declaration 用于点击回调，displayMethod/displayUrl 用于渲染（避免在 EDT 上访问 PSI）
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/21
     */
    data class RequestNode(
        val declaration: KttpRequestDeclaration,
        val displayMethod: String,
        val displayUrl: String,
        val mode: GuiMode,
        val tooltip: String
    ) {
        override fun toString(): String = "$displayMethod $displayUrl"
    }
}
