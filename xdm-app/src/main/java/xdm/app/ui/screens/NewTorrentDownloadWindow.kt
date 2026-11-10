package xdm.app.ui.screens

import com.formdev.flatlaf.FlatClientProperties
import xdm.app.AppContext
import xdm.app.I8N.text
import xdm.app.skins.SkinRegion
import xdm.app.skins.SkinStyleResolver
import xdm.app.ui.components.CategoryStyle
import xdm.app.ui.components.MessageBox
import xdm.app.ui.components.SkinBackgroundPanel
import xdm.app.ui.components.ThemeManager
import xdm.app.utils.FontIcon
import xdm.app.utils.RemixIcon
import xdm.app.utils.ScaledEmptyBorder
import xdm.app.utils.captureCategory
import xdm.app.utils.px
import xdm.app.utils.windowIconImages
import xdm.core.downloaders.DownloadType
import xdm.core.util.CoreUtils
import xdm.core.util.FormatHelper
import xdm.core.util.Logger
import xdm.manager.DbRecord
import xdm.manager.DownloadCategory
import xdm.manager.RecordStatus
import xdm.torrent.TorrentFileEntry
import xdm.torrent.TorrentMetadata
import xdm.torrent.TorrentMetadataParser
import xdm.torrent.TorrentSource
import xdm.torrent.TorrentExistingDataDetector
import xdm.torrent.ExistingTorrentDataCheck
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import java.io.File
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import kotlin.math.max

class NewTorrentDownloadWindow(
    val source: TorrentSource,
    val initialMetadata: TorrentMetadata? = null,
    parent: Window? = null,
    val initialSaveDir: File? = null
) : JDialog(parent) {

    private var metadata: TorrentMetadata? = initialMetadata
    private val fileList = mutableListOf<SelectableFileEntry>()
    private val themeListener = ThemeManager.ThemeChangeListener {
        applySkinStyles()
        repaint()
    }

    private fun applySkinStyles() {
        val btnArc = SkinStyleResolver.resolveButtonArc(SkinRegion.TORRENT_MODAL)
        btnStart.putClientProperty("Button.arc", btnArc)
        btnQueue.putClientProperty("Button.arc", btnArc)
        btnCancel.putClientProperty("Button.arc", btnArc)
    }

    private val txtSource = JTextField().apply {
        isEditable = false
        putClientProperty(FlatClientProperties.STYLE, "arc: 8")
        text = when (source) {
            is TorrentSource.TorrentFile -> source.file.absolutePath
            is TorrentSource.MagnetUri -> source.uri
            is TorrentSource.TorrentBytes -> source.torrentName
        }
    }

    private val txtName = JTextField().apply {
        putClientProperty(FlatClientProperties.STYLE, "arc: 8")
        text = metadata?.name ?: source.name
    }

    val txtFolder = JTextField().apply {
        putClientProperty(FlatClientProperties.STYLE, "arc: 8")
        text = initialSaveDir?.absolutePath ?: AppContext.config.defaultDownloadFolder
    }

    private val cmbCategory = JComboBox<String>().apply {
        addItem("None")
        AppContext.config.categories.forEach { addItem(it.name) }
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (c is JLabel) {
                    val catName = value as? String
                    val cat = AppContext.config.categories.firstOrNull { it.name == catName }
                    c.icon = CategoryStyle.createCategoryIcon(cat?.id, downloadType = if (cat == null) DownloadType.Torrent else null, size = 16)
                    c.iconTextGap = 6.px
                }
                return c
            }
        }
    }

    private val lblStatus = JLabel().apply {
        font = font.deriveFont(Font.BOLD, 11f.px)
    }

    val lblSummary = JLabel().apply {
        font = font.deriveFont(Font.BOLD, 11f.px)
        foreground = UIManager.getColor("Label.foreground")
    }

    private val lblDiskSpace = JLabel().apply {
        font = font.deriveFont(11f.px)
        foreground = Color.GRAY
    }

    val pnlExistingData = JPanel(BorderLayout(8.px, 4.px)).apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(56, 139, 253, 140), 1, true),
            ScaledEmptyBorder(6, 10, 6, 10)
        )
        isVisible = false
    }

    val chkVerifyExisting = JCheckBox("Existing files detected. Verify and resume available data.").apply {
        isSelected = true
        font = font.deriveFont(Font.BOLD, 11f.px)
    }

    val lblExistingDetails = JLabel().apply {
        font = font.deriveFont(11f.px)
        foreground = Color.GRAY
    }

    val btnStart = JButton("Start Download", FontIcon(RemixIcon.DOWNLOAD_2_LINE, 16.px)).apply {
        putClientProperty("JButton.buttonType", "default")
        addActionListener { startDownload(startNow = true) }
    }

    val btnQueue = JButton("Download Later", FontIcon(RemixIcon.LIST_CHECK_2, 16.px)).apply {
        addActionListener { startDownload(startNow = false) }
    }

    val btnCancel = JButton("Cancel").apply {
        addActionListener { dispose() }
    }

    private val tableModel = TorrentFilesTableModel()
    val table = JTable(tableModel)

    data class SelectableFileEntry(
        val index: Int,
        val path: String,
        val size: Long,
        var isSelected: Boolean = true
    )

    init {
        title = "Add BitTorrent Download"
        isModal = true
        iconImages = windowIconImages
        defaultCloseOperation = DISPOSE_ON_CLOSE

        if (metadata == null && source is TorrentSource.TorrentFile && source.file.exists()) {
            try {
                metadata = TorrentMetadataParser.parse(source.file)
                txtName.text = metadata?.name ?: source.name
            } catch (e: Throwable) {
                Logger.error("Failed to parse metadata", e)
            }
        }

        metadata?.files?.forEach { f ->
            fileList.add(SelectableFileEntry(f.index, f.path, f.size, f.selected))
        }

        initUI()
        autoSelectCategory(txtName.text)
        updateDiskSpace()
        checkExistingFiles()
        updateSummary()
    }

    private var populatingCategory = false
    private var userPickedCategory = false

    override fun dispose() {
        ThemeManager.removeThemeListener(themeListener)
        super.dispose()
    }

    private fun autoSelectCategory(name: String) {
        if (userPickedCategory) return
        val matched = captureCategory(name, null, null) ?: return
        populatingCategory = true
        try {
            for (i in 0 until cmbCategory.itemCount) {
                if (cmbCategory.getItemAt(i) == matched.name) {
                    cmbCategory.selectedIndex = i
                    if (matched.folder.isNotBlank() && initialSaveDir == null) {
                        txtFolder.text = matched.folder
                        updateDiskSpace()
                        checkExistingFiles()
                    }
                    break
                }
            }
        } finally {
            populatingCategory = false
        }
    }

    private fun initUI() {
        val root = JPanel(BorderLayout(0, 10.px)).apply {
            border = ScaledEmptyBorder(12, 16, 12, 16)
            isOpaque = false
        }

        // Top Form Panel
        val formPanel = JPanel(GridBagLayout()).apply {
            isOpaque = false
        }
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(3.px, 4.px, 3.px, 4.px)
        }

        // Row 0: Source
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        formPanel.add(JLabel("Source:"), gbc)
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0; gbc.gridwidth = 2
        formPanel.add(txtSource, gbc)

        // Row 1: Status
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0; gbc.gridwidth = 1
        formPanel.add(JLabel("Status:"), gbc)
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0; gbc.gridwidth = 2
        if (metadata != null) {
            lblStatus.text = "Metadata Ready (${metadata!!.files.size} files, ${FormatHelper.formatSize(metadata!!.totalSize.toDouble())})"
            lblStatus.foreground = Color(46, 160, 67)
        } else {
            lblStatus.text = "Magnet link (Metadata will resolve from swarm)"
            lblStatus.foreground = Color(210, 153, 34)
        }
        formPanel.add(lblStatus, gbc)

        // Row 2: Name
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0; gbc.gridwidth = 1
        formPanel.add(JLabel("Name:"), gbc)
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0; gbc.gridwidth = 2
        formPanel.add(txtName, gbc)

        // Row 3: Destination
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0; gbc.gridwidth = 1
        formPanel.add(JLabel("Save To:"), gbc)
        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 1.0; gbc.gridwidth = 1
        formPanel.add(txtFolder, gbc)
        gbc.gridx = 2; gbc.gridy = 3; gbc.weightx = 0.0
        val btnBrowse = JButton("Browse...", FontIcon(RemixIcon.FOLDER_LINE, 16.px)).apply {
            addActionListener { browseDestination() }
        }
        formPanel.add(btnBrowse, gbc)

        // Row 4: Category & Disk Space
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0
        formPanel.add(JLabel("Category:"), gbc)
        gbc.gridx = 1; gbc.gridy = 4; gbc.weightx = 0.5
        formPanel.add(cmbCategory, gbc)
        cmbCategory.addActionListener {
            if (populatingCategory) return@addActionListener
            userPickedCategory = true
            val catName = cmbCategory.selectedItem as? String
            val cat = AppContext.config.categories.firstOrNull { it.name == catName }
            if (cat != null && cat.folder.isNotBlank()) {
                txtFolder.text = cat.folder
                updateDiskSpace()
                checkExistingFiles()
            }
        }
        txtFolder.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { updateDiskSpace(); checkExistingFiles() }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { updateDiskSpace(); checkExistingFiles() }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) { updateDiskSpace(); checkExistingFiles() }
        })
        txtName.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = autoSelectCategory(txtName.text)
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = autoSelectCategory(txtName.text)
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = autoSelectCategory(txtName.text)
        })
        gbc.gridx = 2; gbc.gridy = 4; gbc.weightx = 0.5
        formPanel.add(lblDiskSpace, gbc)

        // Row 5: Existing Data Check
        val boxExisting = Box.createVerticalBox().apply {
            add(chkVerifyExisting)
            add(Box.createVerticalStrut(2.px))
            add(lblExistingDetails)
        }
        pnlExistingData.add(boxExisting, BorderLayout.CENTER)
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 1.0; gbc.gridwidth = 3
        formPanel.add(pnlExistingData, gbc)

        root.add(formPanel, BorderLayout.NORTH)

        // Center: File selection table or Magnet notice
        if (fileList.isNotEmpty()) {
            val centerPanel = JPanel(BorderLayout(0, 6.px))
            val headerBox = Box.createHorizontalBox().apply {
                add(lblSummary)
                add(Box.createHorizontalGlue())
                val btnAll = JButton("Select All").apply {
                    font = font.deriveFont(11f.px)
                    addActionListener { setAllFilesSelected(true) }
                }
                val btnNone = JButton("Deselect All").apply {
                    font = font.deriveFont(11f.px)
                    addActionListener { setAllFilesSelected(false) }
                }
                add(btnAll)
                add(Box.createHorizontalStrut(6.px))
                add(btnNone)
            }
            centerPanel.add(headerBox, BorderLayout.NORTH)

            table.rowHeight = 24.px
            table.columnModel.getColumn(0).maxWidth = 40.px
            table.columnModel.getColumn(0).minWidth = 35.px
            table.columnModel.getColumn(2).maxWidth = 100.px
            table.columnModel.getColumn(2).minWidth = 80.px
            table.columnModel.getColumn(2).cellRenderer = object : DefaultTableCellRenderer() {
                init { horizontalAlignment = SwingConstants.RIGHT }
                override fun setValue(value: Any?) {
                    text = if (value is Number) FormatHelper.formatSize(value.toDouble()) else (value?.toString() ?: "")
                }
            }

            val scroll = JScrollPane(table).apply {
                preferredSize = Dimension(540.px, 200.px)
            }
            centerPanel.add(scroll, BorderLayout.CENTER)
            root.add(centerPanel, BorderLayout.CENTER)
        } else {
            val emptyPanel = JPanel(BorderLayout()).apply {
                border = ScaledEmptyBorder(20, 20, 20, 20)
                background = UIManager.getColor("Table.background")
                val box = Box.createVerticalBox()
                box.add(Box.createVerticalGlue())
                box.add(JLabel("Magnet link: Individual file selection will be available once metadata is fetched.", SwingConstants.CENTER).apply {
                    alignmentX = Component.CENTER_ALIGNMENT
                    foreground = Color.GRAY
                })
                box.add(Box.createVerticalGlue())
                add(box, BorderLayout.CENTER)
            }
            root.add(emptyPanel, BorderLayout.CENTER)
        }

        // Bottom Button Bar
        val bottomBar = JPanel(BorderLayout()).apply {
            border = ScaledEmptyBorder(8, 0, 0, 0)
            val btnBox = Box.createHorizontalBox().apply {
                add(Box.createHorizontalGlue())
                add(btnCancel)
                add(Box.createHorizontalStrut(8.px))
                add(btnQueue)
                add(Box.createHorizontalStrut(8.px))
                add(btnStart)
            }
            add(btnBox, BorderLayout.EAST)
        }
        root.add(bottomBar, BorderLayout.SOUTH)

        contentPane = SkinBackgroundPanel(BorderLayout(), region = SkinRegion.TORRENT_MODAL).apply {
            add(root)
        }
        applySkinStyles()
        ThemeManager.addThemeListener(themeListener)
        rootPane.defaultButton = btnStart
        rootPane.registerKeyboardAction(
            { dispose() },
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        )
        pack()
        size = Dimension(max(width, 580.px), max(height, 420.px))
        setLocationRelativeTo(parent)
    }

    private fun browseDestination() {
        val fc = JFileChooser().apply {
            dialogTitle = "Select Destination Directory"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            selectedFile = File(txtFolder.text)
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            txtFolder.text = fc.selectedFile.absolutePath
            updateDiskSpace()
            checkExistingFiles()
        }
    }

    fun checkExistingFiles() {
        val meta = metadata
        val folder = File(txtFolder.text.trim())
        if (meta == null || !folder.exists() || !folder.isDirectory) {
            pnlExistingData.isVisible = false
            return
        }

        val check = TorrentExistingDataDetector.detect(folder, meta)
        if (check.hasExistingData) {
            val sizeStr = FormatHelper.formatSize(check.existingBytes.toDouble())
            val totalStr = FormatHelper.formatSize(check.totalExpectedBytes.toDouble())
            chkVerifyExisting.text = "Existing files detected. Verify and resume available data."
            lblExistingDetails.text = "Found ${check.existingFilesCount} of ${check.totalFilesCount} files ($sizeStr / $totalStr on disk). Will verify piece hashes."
            chkVerifyExisting.isSelected = true
            pnlExistingData.isVisible = true
        } else {
            pnlExistingData.isVisible = false
        }
        revalidate()
        repaint()
    }

    private fun updateDiskSpace() {
        try {
            val f = File(txtFolder.text)
            val free = f.freeSpace
            lblDiskSpace.text = "Free Space: ${FormatHelper.formatSize(free.toDouble())}"
        } catch (_: Throwable) {
            lblDiskSpace.text = ""
        }
    }

    private fun updateSummary() {
        if (fileList.isEmpty()) {
            lblSummary.text = "All files will be downloaded"
            btnStart.isEnabled = true
            btnQueue.isEnabled = true
            return
        }
        val selectedCount = fileList.count { it.isSelected }
        val selectedBytes = fileList.filter { it.isSelected }.sumOf { it.size }
        val totalBytes = fileList.sumOf { it.size }

        lblSummary.text = "Selected: $selectedCount of ${fileList.size} files (${FormatHelper.formatSize(selectedBytes.toDouble())} / ${FormatHelper.formatSize(totalBytes.toDouble())})"
        val canStart = selectedCount > 0
        btnStart.isEnabled = canStart
        btnQueue.isEnabled = canStart
    }

    private fun setAllFilesSelected(selected: Boolean) {
        fileList.forEach { it.isSelected = selected }
        tableModel.fireTableDataChanged()
        updateSummary()
    }

    private fun startDownload(startNow: Boolean) {
        val name = txtName.text.trim()
        if (name.isBlank()) {
            MessageBox.show(this, "Validation Error", "Please provide a valid download name.")
            return
        }

        val destFolder = File(txtFolder.text.trim())
        if (!destFolder.exists() && !destFolder.mkdirs()) {
            MessageBox.show(this, "Destination Error", "Cannot create destination directory: ${destFolder.absolutePath}")
            return
        }
        if (destFolder.exists() && !destFolder.isDirectory) {
            MessageBox.show(this, "Destination Error", "Destination is not a directory: ${destFolder.absolutePath}")
            return
        }

        if (fileList.isNotEmpty() && fileList.none { it.isSelected }) {
            MessageBox.show(this, "File Selection Error", "Please select at least one file to download.")
            return
        }

        val fileSelection = if (fileList.isNotEmpty()) fileList.map { it.isSelected } else null
        val catName = cmbCategory.selectedItem as? String
        val categoryId = if (catName != null && catName != "None") {
            AppContext.config.categories.firstOrNull { it.name == catName }?.id
        } else null

        val enteredName = txtName.text.trim()
        val initialDefaultName = metadata?.name ?: source.name
        val isCustom = enteredName.isNotBlank() &&
                enteredName != initialDefaultName &&
                !TorrentMetadataParser.isPlaceholderName(enteredName)
        val customName = if (isCustom) enteredName else null

        val id = CoreUtils.uniqueId()
        val verifyExisting = pnlExistingData.isVisible && chkVerifyExisting.isSelected
        try {
            val task = AppContext.torrentManager.createTask(
                id = id,
                source = source,
                saveDir = destFolder,
                fileSelection = fileSelection,
                categoryId = categoryId,
                customName = customName,
                verifyExisting = verifyExisting
            )
            TorrentUiBridge.attachTaskListener(task)

            val totalSize = metadata?.totalSize ?: 0L
            val rec = DbRecord(
                id = id,
                size = totalSize,
                downloaded = 0L,
                progress = 0,
                date = System.currentTimeMillis(),
                fileName = task.name,
                eta = 0L,
                speed = 0f,
                selected = false,
                status = if (startNow) RecordStatus.DOWNLOADING else RecordStatus.PAUSED,
                downloadType = DownloadType.Torrent,
                categoryId = categoryId
            )
            AppContext.db.add(rec)
            AppContext.app.addDownloadInView(id)

            if (startNow) {
                task.resume()
                AppContext.app.showProgressWindow(id, task.name)
            }

            dispose()
        } catch (e: Throwable) {
            Logger.error("Failed to start BitTorrent task", e)
            MessageBox.show(this, "Error", "Failed to start BitTorrent task: ${e.message}")
        }
    }

    private inner class TorrentFilesTableModel : AbstractTableModel() {
        private val colNames = arrayOf(" ", "File", "Size")

        override fun getRowCount(): Int = fileList.size
        override fun getColumnCount(): Int = colNames.size
        override fun getColumnName(col: Int): String = colNames[col]

        override fun getColumnClass(col: Int): Class<*> = when (col) {
            0 -> java.lang.Boolean::class.java
            1 -> String::class.java
            2 -> java.lang.Long::class.java
            else -> Any::class.java
        }

        override fun isCellEditable(row: Int, col: Int): Boolean = col == 0

        override fun getValueAt(row: Int, col: Int): Any? {
            if (row !in fileList.indices) return null
            val entry = fileList[row]
            return when (col) {
                0 -> entry.isSelected
                1 -> entry.path
                2 -> entry.size
                else -> null
            }
        }

        override fun setValueAt(value: Any?, row: Int, col: Int) {
            if (col == 0 && row in fileList.indices) {
                fileList[row].isSelected = value as? Boolean ?: true
                fireTableCellUpdated(row, col)
                updateSummary()
            }
        }
    }
}

