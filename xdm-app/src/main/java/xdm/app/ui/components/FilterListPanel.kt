package xdm.app.ui.components


import xdm.app.AppContext
import xdm.app.I8N.text
import xdm.app.displayName
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.createIcon
import xdm.app.utils.openInDefaultBrowser
import xdm.app.utils.px
import xdm.manager.DbRecord
import xdm.manager.DownloadCategory
import xdm.manager.RecordStatus
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import javax.swing.*
import javax.swing.border.MatteBorder

/**
 * The main window's sidebar: a "Library" section filtering by download state and a "File types"
 * section filtering by category, each row showing how many downloads it matches.
 */
class FilterListPanel(
    val stateChanged: (FilterState) -> Unit,
    val categoryChanged: (DownloadCategory?) -> Unit
) :
    JPanel() {
    private val jsp: JScrollPane
    private val root: JPanel
    private val catFilterModel = DefaultListModel<FilterItem>()
    private val catFilterList: JList<FilterItem>
    private val stateFilterModel = DefaultListModel<FilterItem>()
    private val stateFilterList: JList<FilterItem>
    private val box: FilterBox
    private val secLibrary: JLabel
    private val secTypes: JLabel
    private val stayInTouchLabel: JLabel
    private val stayInTouchIcon: JLabel
    private val footerContainer: JComponent

    private var stateCounts = emptyMap<FilterState, Int>()

    /** Keyed by category id; null is "All types". */
    private var categoryCounts = emptyMap<String?, Int>()
    private var countSource: (() -> List<DbRecord>)? = null

    /** Downloads report progress many times a second; recount at most a few times a second. */
    private val countTimer = Timer(250) { recount() }.apply { isRepeats = false }

    /** The config's categories as the rows were last built from; the config listener compares against it. */
    private var shownCategories = emptyList<DownloadCategory>()

    init {
        stateFilterList = sidebarList(stateFilterModel)
        fillStateItems()

        fillCategories()
        catFilterList = sidebarList(catFilterModel)

        secLibrary = sectionLabel(text("CAT_SECTION_LIBRARY"))
        secTypes = sectionLabel(text("CAT_SECTION_TYPES"))
        stayInTouchLabel = JLabel(text("SIDEBAR_STAY_IN_TOUCH")).apply {
            font = font.deriveFont(Font.BOLD, font.size2D - 1f)
            foreground = AppTheme.sidebarForeground
        }
        stayInTouchIcon = JLabel(createIcon(RemixIcon.USER_HEART_FILL, 16, AppTheme.accent))
        footerContainer = stayInTouchLink()

        box = FilterBox().apply {
            add(secLibrary)
            add(stateFilterList)
            add(Box.createVerticalStrut(14.px))
            add(secTypes)
            add(catFilterList)
            border = ScaledEmptyBorder(14, 2, 14, 2)
            isOpaque = true
            background = AppTheme.sidebarBackground
        }

        jsp = JScrollPane(box).apply {
            border = BorderFactory.createEmptyBorder()
            isOpaque = true
            background = AppTheme.sidebarBackground
            viewport.isOpaque = true
            viewport.background = AppTheme.sidebarBackground
        }

        root = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val painted = xdm.app.skins.SkinStyleResolver.paintRegionBackground(
                    g, width, height, xdm.app.skins.SkinRegion.SIDEBAR
                )
                if (!painted) super.paintComponent(g)
            }
        }.apply {
            add(jsp, BorderLayout.CENTER)
            add(footerContainer, BorderLayout.SOUTH)
            border = MatteBorder(0, 0, 0, 1, AppTheme.sidebarBorder)
            background = AppTheme.sidebarBackground
        }

        stateFilterList.selectedIndex = 0
        catFilterList.selectedIndex = 0

        stateFilterList.addListSelectionListener {
            val index = stateFilterList.selectedIndex
            if (index != -1) {
                val state = stateFilterModel[index] as FilterItem.State
                stateChanged(state.state)
            }
        }

        catFilterList.addListSelectionListener {
            val index = catFilterList.selectedIndex
            if (index != -1) {
                val category = catFilterModel[index] as FilterItem.Category
                categoryChanged(category.category)
            }
        }

        // Categories change from Settings and from a download dialog's + button; follow the config
        // rather than either caller. The listener may fire on any thread, and fires for every config
        // change, so only rebuild when the category list itself differs. The main window lives as
        // long as the app, so the listener is never removed.
        AppContext.config.addChangeListener {
            SwingUtilities.invokeLater {
                if (AppContext.config.categories != shownCategories) reloadCategories()
            }
        }
    }

    /**
     * Sets where the row counts come from and counts once; [scheduleRecount] then refreshes them
     * whenever the list changes. Call on the EDT.
     */
    fun countsFrom(source: () -> List<DbRecord>) {
        countSource = source
        recount()
    }

    fun scheduleRecount() {
        if (!countTimer.isRunning) countTimer.start()
    }

    private fun recount() {
        val records = countSource?.invoke() ?: return
        val finished = records.count { it.status == RecordStatus.FINISHED }
        stateCounts = mapOf(
            FilterState.All to records.size,
            FilterState.Incomplete to records.size - finished,
            FilterState.Completed to finished
        )
        val counts = HashMap<String?, Int>()
        counts[null] = records.size
        for (cat in AppContext.config.categories) {
            counts[cat.id] = records.count { it.categoryId == cat.id }
        }
        categoryCounts = counts
        stateFilterList.repaint()
        catFilterList.repaint()
    }

    private fun countOf(item: FilterItem): Int? = when (item) {
        is FilterItem.State -> stateCounts[item.state]
        is FilterItem.Category -> categoryCounts[item.category?.id]
    }

    private fun fillStateItems() {
        val sel = stateFilterList.selectedIndex.takeIf { it >= 0 } ?: 0
        stateFilterModel.clear()
        stateFilterModel.addElement(
            FilterItem.State(
                FilterState.All, text("CAT_ALL"),
                makeIcon(RemixIcon.ARROW_DOWN_CIRCLE_LINE, false),
                makeIcon(RemixIcon.ARROW_DOWN_CIRCLE_FILL, true)
            )
        )
        stateFilterModel.addElement(
            FilterItem.State(
                FilterState.Incomplete,
                text("CAT_INCOMPLETE"),
                makeIcon(RemixIcon.PROGRESS_2_LINE, false),
                makeIcon(RemixIcon.PROGRESS_2_FILL, true)
            )
        )
        stateFilterModel.addElement(
            FilterItem.State(
                FilterState.Completed,
                text("CAT_FINISHED"),
                makeIcon(RemixIcon.CHECKBOX_CIRCLE_LINE, false),
                makeIcon(RemixIcon.CHECKBOX_CIRCLE_FILL, true)
            )
        )
        stateFilterList.selectedIndex = sel.coerceIn(0, stateFilterModel.size() - 1)
    }

    fun updateTheme() {
        val hasWp = xdm.app.skins.SkinManager.currentSkin.hasWallpaper() ||
            xdm.app.skins.SkinManager.currentSkin.hasRegionWallpaper(xdm.app.skins.SkinRegion.SIDEBAR)
        box.isOpaque = !hasWp
        jsp.isOpaque = !hasWp
        jsp.viewport.isOpaque = !hasWp
        box.background = AppTheme.sidebarBackground
        jsp.background = AppTheme.sidebarBackground
        jsp.viewport.background = AppTheme.sidebarBackground
        root.background = AppTheme.sidebarBackground
        root.border = MatteBorder(0, 0, 0, 1, AppTheme.sidebarBorder)
        secLibrary.foreground = AppTheme.sidebarSection
        secTypes.foreground = AppTheme.sidebarSection
        stayInTouchLabel.foreground = AppTheme.sidebarForeground
        stayInTouchIcon.icon = createIcon(RemixIcon.USER_HEART_FILL, 16, AppTheme.accent)
        fillStateItems()
        val prevCat = (catFilterList.selectedValue as? FilterItem.Category)?.category?.id
        fillCategories()
        val idx = (0 until catFilterModel.size())
            .firstOrNull { (catFilterModel[it] as FilterItem.Category).category?.id == prevCat } ?: 0
        catFilterList.selectedIndex = idx
        recount()
        root.revalidate()
        root.repaint()
    }

    private fun fillCategories() {
        shownCategories = AppContext.config.categories
        catFilterModel.clear()
        val allDot = CategoryDotIcon(AppTheme.tileHue(null))
        catFilterModel.addElement(FilterItem.Category(null, text("CAT_ALL_TYPES"), allDot, allDot))
        for (cat in AppContext.config.categories) {
            val dot = CategoryDotIcon(AppTheme.categoryColor(cat))
            catFilterModel.addElement(FilterItem.Category(cat, cat.displayName, dot, dot))
        }
    }

    /**
     * Rebuilds the category rows from the config after its category list changes.
     * Keeps the current selection when that category still exists, otherwise falls back
     * to "All types" so the list never shows a filter the user can no longer see.
     */
    private fun reloadCategories() {
        val selectedId = (catFilterList.selectedValue as? FilterItem.Category)?.category?.id
        fillCategories()
        // The hovered row may have moved or gone; the next mouse move sets it again.
        catFilterList.putClientProperty(FilterListRenderer.HOVER_INDEX, -1)
        val index = (0 until catFilterModel.size())
            .firstOrNull { (catFilterModel[it] as FilterItem.Category).category?.id == selectedId }
            ?: 0
        catFilterList.selectedIndex = index
        recount()
        // Notify unconditionally: when the index is unchanged the selection listener does
        // not fire, but the category behind it may have been edited, so the list still
        // needs to re-filter and repaint its icons.
        categoryChanged((catFilterModel[index] as FilterItem.Category).category)
    }

    /**
     * A sidebar list: reports an unbounded maximum width but its natural height. Inside the
     * vertical [FilterBox] a plain JList would be capped at its preferred width, leaving the
     * selection pill stopping short of the sidebar's edge; the height stays preferred so the
     * two stacked lists do not share out the leftover vertical space between them. Tracks the
     * row under the mouse for the renderer's hover pill.
     */
    private fun sidebarList(model: DefaultListModel<FilterItem>): JList<FilterItem> =
        object : JList<FilterItem>(model) {
            override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
        }.apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            isOpaque = false
            cellRenderer = FilterListRenderer(::countOf)
            alignmentX = 0f
            // Side padding so the pill floats clear of the panel edges.
            border = ScaledEmptyBorder(0, 10, 0, 10)
            val hover = object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) = setHover(locationToIndex(e.point).takeIf {
                    it != -1 && getCellBounds(it, it).contains(e.point)
                } ?: -1)

                override fun mouseExited(e: MouseEvent) = setHover(-1)

                private fun setHover(index: Int) {
                    if (getClientProperty(FilterListRenderer.HOVER_INDEX) != index) {
                        putClientProperty(FilterListRenderer.HOVER_INDEX, index)
                        repaint()
                    }
                }
            }
            addMouseListener(hover)
            addMouseMotionListener(hover)
        }

    /** The small spaced-out uppercase caption above each section, lined up with the row text. */
    private fun sectionLabel(title: String): JLabel = JLabel(title.uppercase()).apply {
        font = font.deriveFont(Font.BOLD, 9.0f.px).deriveFont(mapOf(TextAttribute.TRACKING to 0.08f))
        foreground = AppTheme.sidebarSection
        alignmentX = 0f
        border = ScaledEmptyBorder(0, 18, 8, 10)
    }

    /**
     * The "Stay in touch" link at the foot of the sidebar: opens the GitHub page on supporting XDM.
     * Uses the same inset and padding as a selected row's pill, so it lines up with the rows.
     * The label truncates with an ellipsis instead of widening the sidebar.
     */
    private fun stayInTouchLink(): JComponent {
        val label = stayInTouchLabel
        val card = JPanel(BorderLayout(8.px, 0)).apply {
            isOpaque = false
            border = ScaledEmptyBorder(9, 8, 9, 10)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = SUPPORT_URL
            add(stayInTouchIcon, BorderLayout.WEST)
            add(label, BorderLayout.CENTER)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e)) openInDefaultBrowser(SUPPORT_URL)
                }

                override fun mouseEntered(e: MouseEvent) {
                    label.foreground = AppTheme.accent
                }

                override fun mouseExited(e: MouseEvent) {
                    label.foreground = AppTheme.sidebarForeground
                }
            })
        }
        // 12px sides: the lists' 10px padding plus the box's 2px, so the card lines up with the pills.
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = ScaledEmptyBorder(6, 12, 6, 12)
            add(card)
        }
    }

    /** State rows draw the outlined glyph muted, and the filled one in the accent when selected. */
    private fun makeIcon(icon: RemixIcon, selected: Boolean): Icon =
        createIcon(icon, 16, if (selected) AppTheme.accent else AppTheme.sidebarIcon)

    /**
     * Stretches to the scroll pane's width instead of to the lists' preferred width, so a
     * selected row's pill spans the whole sidebar (less the lists' own side padding) rather
     * than stopping at the end of the longest label.
     */
    private class FilterBox : JPanel(), Scrollable {
        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = 24.px
        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
            visibleRect.height

        override fun getScrollableTracksViewportWidth(): Boolean = true

        /** Fill the viewport when the rows are shorter than it, but scroll when they are not. */
        override fun getScrollableTracksViewportHeight(): Boolean =
            (parent as? JViewport)?.let { it.height > preferredSize.height } ?: false
    }

    val component: Component
        get() = this.root

    private companion object {
        const val SUPPORT_URL = "https://github.com/jakepanlilio2000/XDM-Enhanced/blob/master/SUPPORT.md"
    }
}
