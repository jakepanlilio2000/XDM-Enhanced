package xdm.app.ui.components

import com.formdev.flatlaf.FlatClientProperties
import xdm.app.AppContext
import xdm.app.I8N.text
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.openFolderExternal
import xdm.app.utils.px
import xdm.core.downloaders.DownloadType
import xdm.core.util.Logger
import xdm.manager.DbRecord
import xdm.manager.DownloadCategory
import xdm.manager.RecordStatus
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class CategorySidebar(
    private val onFilterChanged: (FilterState, DownloadCategory?) -> Unit
) : JPanel(BorderLayout()) {

    companion object {
        val OTHER_CATEGORY = DownloadCategory(
            id = "__OTHER__",
            name = "Other",
            extensions = emptySet(),
            folder = ""
        )
    }

    data class SidebarNodeData(
        val title: String,
        var icon: Icon,
        val filterState: FilterState,
        val categoryId: String? = null,
        val isOther: Boolean = false,
        var count: Int = 0
    ) {
        override fun toString(): String = title
    }

    private val rootNode = DefaultMutableTreeNode("Root")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = JTree(treeModel)
    private val countTimer = Timer(250) { recount() }.apply { isRepeats = false }

    private val allDownloadsNode: DefaultMutableTreeNode
    private val compressedNode: DefaultMutableTreeNode
    private val documentsNode: DefaultMutableTreeNode
    private val musicNode: DefaultMutableTreeNode
    private val programsNode: DefaultMutableTreeNode
    private val videosNode: DefaultMutableTreeNode
    private val imagesNode: DefaultMutableTreeNode
    private val otherNode: DefaultMutableTreeNode
    private val unfinishedNode: DefaultMutableTreeNode
    private val completedNode: DefaultMutableTreeNode
    private val torrentsNode: DefaultMutableTreeNode
    private val queuesNode: DefaultMutableTreeNode

    private val window: Window?
        get() = SwingUtilities.windowForComponent(this)

    init {
        background = AppTheme.sidebarBackground
        border = BorderFactory.createMatteBorder(0, 0, 0, 1, AppTheme.sidebarBorder)

        // Build nodes
        allDownloadsNode = DefaultMutableTreeNode(
            SidebarNodeData("All Downloads", FontIcon(RemixIcon.ARROW_DOWN_CIRCLE_LINE, 18.px), FilterState.All)
        )
        compressedNode = DefaultMutableTreeNode(
            SidebarNodeData("Compressed", FontIcon(RemixIcon.FILE_ZIP_LINE, 18.px, Color(210, 153, 34)), FilterState.All, categoryId = "CAT_COMPRESSED")
        )
        documentsNode = DefaultMutableTreeNode(
            SidebarNodeData("Documents", FontIcon(RemixIcon.FILE_TEXT_LINE, 18.px, Color(88, 166, 255)), FilterState.All, categoryId = "CAT_DOCUMENTS")
        )
        musicNode = DefaultMutableTreeNode(
            SidebarNodeData("Music", FontIcon(RemixIcon.MUSIC_2_LINE, 18.px, Color(240, 136, 62)), FilterState.All, categoryId = "CAT_MUSIC")
        )
        videosNode = DefaultMutableTreeNode(
            SidebarNodeData("Videos", FontIcon(RemixIcon.MOVIE_LINE, 18.px, Color(163, 113, 247)), FilterState.All, categoryId = "CAT_VIDEOS")
        )
        programsNode = DefaultMutableTreeNode(
            SidebarNodeData("Programs", FontIcon(RemixIcon.TERMINAL_BOX_LINE, 18.px, Color(230, 80, 80)), FilterState.All, categoryId = "CAT_PROGRAMS")
        )
        imagesNode = DefaultMutableTreeNode(
            SidebarNodeData("Images", FontIcon(RemixIcon.IMAGE_LINE, 18.px, Color(56, 189, 248)), FilterState.All, categoryId = "CAT_IMAGES")
        )
        otherNode = DefaultMutableTreeNode(
            SidebarNodeData("Other", FontIcon(RemixIcon.FILE_LINE, 18.px, Color.GRAY), FilterState.All, isOther = true)
        )

        allDownloadsNode.add(compressedNode)
        allDownloadsNode.add(documentsNode)
        allDownloadsNode.add(musicNode)
        allDownloadsNode.add(programsNode)
        allDownloadsNode.add(videosNode)
        allDownloadsNode.add(imagesNode)
        allDownloadsNode.add(otherNode)

        unfinishedNode = DefaultMutableTreeNode(
            SidebarNodeData("Unfinished", FontIcon(RemixIcon.PROGRESS_2_LINE, 18.px, Color(56, 139, 253)), FilterState.Incomplete)
        )
        completedNode = DefaultMutableTreeNode(
            SidebarNodeData("Completed", FontIcon(RemixIcon.CHECKBOX_CIRCLE_LINE, 18.px, Color(46, 160, 67)), FilterState.Completed)
        )
        torrentsNode = DefaultMutableTreeNode(
            SidebarNodeData("Torrents", FontIcon(RemixIcon.DOWNLOAD_2_LINE, 18.px, Color(56, 139, 253)), FilterState.Torrents)
        )
        queuesNode = DefaultMutableTreeNode(
            SidebarNodeData("Queues", FontIcon(RemixIcon.LIST_CHECK_2, 18.px, Color(210, 153, 34)), FilterState.Queues)
        )

        rootNode.add(allDownloadsNode)
        rootNode.add(unfinishedNode)
        rootNode.add(completedNode)
        rootNode.add(torrentsNode)
        rootNode.add(queuesNode)

        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.rowHeight = 28.px
        tree.isOpaque = false
        tree.background = AppTheme.sidebarBackground
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.putClientProperty(FlatClientProperties.TREE_WIDE_SELECTION, true)

        tree.cellRenderer = CategoryTreeRenderer()

        tree.addTreeSelectionListener { e ->
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val data = node.userObject as? SidebarNodeData ?: return@addTreeSelectionListener
            val category = when {
                data.isOther -> OTHER_CATEGORY
                data.categoryId != null -> AppContext.config.categories.firstOrNull { it.id == data.categoryId }
                else -> null
            }
            onFilterChanged(data.filterState, category)
        }

        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = handlePopup(e)
            override fun mouseReleased(e: MouseEvent) = handlePopup(e)

            private fun handlePopup(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    val path = tree.getPathForLocation(e.x, e.y) ?: return
                    tree.selectionPath = path
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    val data = node.userObject as? SidebarNodeData ?: return
                    showSidebarContextMenu(data, e.x, e.y)
                }
            }
        })

        val scrollPane = JScrollPane(tree).apply {
            border = BorderFactory.createEmptyBorder()
            viewportBorder = BorderFactory.createEmptyBorder()
            isOpaque = false
            viewport.isOpaque = false
        }

        add(scrollPane, BorderLayout.CENTER)

        // Expand All Downloads by default and select it
        tree.expandPath(TreePath(allDownloadsNode.path))
        tree.selectionPath = TreePath(allDownloadsNode.path)

        recount()
    }

    private fun showSidebarContextMenu(data: SidebarNodeData, x: Int, y: Int) {
        val menu = JPopupMenu()
        when (data.filterState) {
            FilterState.Torrents -> {
                menu.add(JMenuItem("Resume All Torrents", FontIcon(RemixIcon.PLAY_CIRCLE_LINE, 16.px)).apply {
                    addActionListener {
                        AppContext.db.filter { it.downloadType == DownloadType.Torrent && it.status.isStopped }
                            .forEach { AppMenuHandler.resumeDownload(it) }
                    }
                })
                menu.add(JMenuItem("Pause All Torrents", FontIcon(RemixIcon.PAUSE_CIRCLE_LINE, 16.px)).apply {
                    addActionListener {
                        AppContext.db.filter { it.downloadType == DownloadType.Torrent && it.status == RecordStatus.DOWNLOADING }
                            .forEach { AppMenuHandler.pauseDownload(it) }
                    }
                })
            }
            FilterState.Queues -> {
                menu.add(JMenuItem("Start Queue", FontIcon(RemixIcon.PLAY_CIRCLE_LINE, 16.px)).apply {
                    addActionListener { AppContext.scheduler.start() }
                })
                menu.add(JMenuItem("Stop Queue", FontIcon(RemixIcon.CLOSE_CIRCLE_LINE, 16.px)).apply {
                    addActionListener { AppContext.scheduler.stop() }
                })
                menu.addSeparator()
                menu.add(JMenuItem("Open Scheduler...", FontIcon(RemixIcon.LIST_CHECK_2, 16.px)).apply {
                    addActionListener { AppContext.app.showSchedulerWindow(0) }
                })
            }
            else -> {
                val cat = if (data.isOther) OTHER_CATEGORY else data.categoryId?.let { id ->
                    AppContext.config.categories.firstOrNull { it.id == id }
                }
                menu.add(JMenuItem("Resume All in Category", FontIcon(RemixIcon.PLAY_CIRCLE_LINE, 16.px)).apply {
                    addActionListener {
                        AppContext.db.filter { rec ->
                            (cat == null || rec.categoryId == cat.id) && rec.status.isStopped
                        }.forEach { AppMenuHandler.resumeDownload(it) }
                    }
                })
                menu.add(JMenuItem("Pause All in Category", FontIcon(RemixIcon.PAUSE_CIRCLE_LINE, 16.px)).apply {
                    addActionListener {
                        AppContext.db.filter { rec ->
                            (cat == null || rec.categoryId == cat.id) && rec.status == RecordStatus.DOWNLOADING
                        }.forEach { AppMenuHandler.pauseDownload(it) }
                    }
                })
                menu.addSeparator()
                menu.add(JMenuItem("Open Folder", FontIcon(RemixIcon.FOLDER_LINE, 16.px)).apply {
                    addActionListener {
                        val folder = cat?.folder?.ifBlank { null } ?: AppContext.config.defaultDownloadFolder
                        try {
                            openFolderExternal(null, folder)
                        } catch (e: Throwable) {
                            Logger.error("Failed to open category folder", e)
                        }
                    }
                })
            }
        }
        menu.show(tree, x, y)
    }

    fun updateTheme() {
        background = AppTheme.sidebarBackground
        border = BorderFactory.createMatteBorder(0, 0, 0, 1, AppTheme.sidebarBorder)
        tree.background = AppTheme.sidebarBackground

        fun setIcon(node: DefaultMutableTreeNode, icon: Icon) {
            (node.userObject as? SidebarNodeData)?.icon = icon
        }

        val isDark = com.formdev.flatlaf.FlatLaf.isLafDark()
        val accent = AppTheme.accent
        setIcon(allDownloadsNode, FontIcon(RemixIcon.ARROW_DOWN_CIRCLE_LINE, 18.px, accent))
        setIcon(compressedNode, FontIcon(RemixIcon.FILE_ZIP_LINE, 18.px, UIManager.getColor("XDM.category.CAT_COMPRESSED") ?: Color(210, 153, 34)))
        setIcon(documentsNode, FontIcon(RemixIcon.FILE_TEXT_LINE, 18.px, UIManager.getColor("XDM.category.CAT_DOCUMENTS") ?: Color(88, 166, 255)))
        setIcon(musicNode, FontIcon(RemixIcon.MUSIC_2_LINE, 18.px, UIManager.getColor("XDM.category.CAT_MUSIC") ?: Color(240, 136, 62)))
        setIcon(videosNode, FontIcon(RemixIcon.MOVIE_LINE, 18.px, UIManager.getColor("XDM.category.CAT_VIDEOS") ?: Color(163, 113, 247)))
        setIcon(programsNode, FontIcon(RemixIcon.TERMINAL_BOX_LINE, 18.px, UIManager.getColor("XDM.category.CAT_PROGRAMS") ?: Color(230, 80, 80)))
        setIcon(imagesNode, FontIcon(RemixIcon.IMAGE_LINE, 18.px, UIManager.getColor("XDM.category.CAT_IMAGES") ?: Color(56, 189, 248)))
        setIcon(otherNode, FontIcon(RemixIcon.FILE_LINE, 18.px, AppTheme.sidebarIcon))
        setIcon(unfinishedNode, FontIcon(RemixIcon.PROGRESS_2_LINE, 18.px, accent))
        setIcon(completedNode, FontIcon(RemixIcon.CHECKBOX_CIRCLE_LINE, 18.px, if (isDark) Color(63, 185, 80) else Color(26, 127, 55)))
        setIcon(torrentsNode, FontIcon(RemixIcon.DOWNLOAD_2_LINE, 18.px, accent))
        setIcon(queuesNode, FontIcon(RemixIcon.LIST_CHECK_2, 18.px, UIManager.getColor("XDM.category.CAT_MUSIC") ?: Color(210, 153, 34)))

        tree.cellRenderer = CategoryTreeRenderer()
        recount()
        repaint()
    }

    fun scheduleRecount() {
        if (!countTimer.isRunning) countTimer.start()
    }

    fun recount() {
        val records = AppContext.db.filter { true }
        val categoryIds = AppContext.config.categories.map { it.id }.toSet()

        fun updateNode(node: DefaultMutableTreeNode, count: Int) {
            val data = node.userObject as? SidebarNodeData ?: return
            data.count = count
        }

        updateNode(allDownloadsNode, records.size)
        updateNode(compressedNode, records.count { it.categoryId == "CAT_COMPRESSED" })
        updateNode(documentsNode, records.count { it.categoryId == "CAT_DOCUMENTS" })
        updateNode(musicNode, records.count { it.categoryId == "CAT_MUSIC" })
        updateNode(videosNode, records.count { it.categoryId == "CAT_VIDEOS" })
        updateNode(programsNode, records.count { it.categoryId == "CAT_PROGRAMS" })
        updateNode(imagesNode, records.count { it.categoryId == "CAT_IMAGES" })
        updateNode(otherNode, records.count { it.categoryId == null || it.categoryId !in categoryIds })
        updateNode(unfinishedNode, records.count { it.status != RecordStatus.FINISHED })
        updateNode(completedNode, records.count { it.status == RecordStatus.FINISHED })
        updateNode(torrentsNode, records.count { it.downloadType == DownloadType.Torrent })
        updateNode(queuesNode, records.count { it.status == RecordStatus.READY || it.status == RecordStatus.PAUSED })

        tree.repaint()
    }

    fun getNodeCount(title: String): Int? {
        fun find(node: DefaultMutableTreeNode): Int? {
            val data = node.userObject as? SidebarNodeData
            if (data?.title == title) return data.count
            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
                val res = find(child)
                if (res != null) return res
            }
            return null
        }
        return find(rootNode)
    }

    private class CategoryTreeRenderer : DefaultTreeCellRenderer() {
        private val countBadge = JLabel().apply {
            font = font.deriveFont(Font.BOLD, 10f.px)
            foreground = Color.GRAY
        }

        private val panel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = ScaledEmptyBorder(0, 4, 0, 8)
        }

        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): Component {
            val defaultComp = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus) as JLabel
            val node = value as? DefaultMutableTreeNode
            val data = node?.userObject as? SidebarNodeData

            if (data != null) {
                defaultComp.text = data.title
                defaultComp.icon = data.icon
                countBadge.text = "${data.count}"
                countBadge.foreground = if (sel) AppTheme.sidebarSelectedCount else AppTheme.sidebarCount

                panel.removeAll()
                panel.add(defaultComp, BorderLayout.WEST)
                panel.add(countBadge, BorderLayout.EAST)
                return panel
            }
            return defaultComp
        }
    }
}

