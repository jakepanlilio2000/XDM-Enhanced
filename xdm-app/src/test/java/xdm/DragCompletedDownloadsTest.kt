package xdm

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xdm.app.AppContext
import xdm.app.DesktopEngineHost
import xdm.app.ui.components.DownloadListTransferHandler
import xdm.core.downloaders.DownloadType
import xdm.core.downloaders.HttpDownloadTaskInfo
import xdm.core.downloaders.TaskInfoDB
import xdm.manager.DbRecord
import xdm.manager.EngineContext
import xdm.manager.RecordStatus
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.nio.file.Files
import javax.swing.TransferHandler

/**
 * Regression tests for Issue #1383:
 * Drag completed downloads to external file-manager targets using native file-transfer behavior.
 * Enforces prevention of dragging incomplete files, missing files, accidental deletions, or moves.
 */
class DragCompletedDownloadsTest {

    private lateinit var tempDir: File
    private lateinit var taskDb: TaskInfoDB

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("xdm-drag-test").toFile()
        taskDb = TaskInfoDB(tempDir.absolutePath)
        AppContext.taskInfoDB = taskDb
        EngineContext.host = DesktopEngineHost
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun createHttpRecord(
        id: Long,
        fileName: String,
        status: RecordStatus,
        createPhysicalFile: Boolean = true
    ): DbRecord {
        val targetFolder = tempDir.absolutePath
        val physicalFile = File(targetFolder, fileName)
        if (createPhysicalFile) {
            physicalFile.writeText("sample downloaded content for $fileName")
        }

        val httpTask = HttpDownloadTaskInfo(
            id = id,
            url = "http://example.com/$fileName",
            fileName = fileName,
            respectFileName = false,
            cookie = null,
            headers = null,
            origin = null,
            categoryId = null,
            targetFolder = targetFolder,
            maxPiece = 8,
            authInfo = null,
            knownFileSize = physicalFile.length()
        )
        taskDb.saveHttpTask(httpTask)

        return DbRecord(
            id = id,
            size = physicalFile.length(),
            downloaded = physicalFile.length(),
            progress = if (status == RecordStatus.FINISHED) 100 else 50,
            date = System.currentTimeMillis(),
            fileName = fileName,
            eta = 0,
            speed = 0.0f,
            selected = false,
            status = status,
            downloadType = DownloadType.Http,
            movePhase = null,
            failedFiles = 0,
            categoryId = null,
            allocating = -1
        )
    }

    @Test
    fun sourceActionIsStrictlyCopy() {
        val handler = DownloadListTransferHandler { emptyList() }
        assertEquals(TransferHandler.COPY, handler.getSourceActions(null),
            "Drag source action must be COPY to prevent external file managers from moving or deleting source files")
    }

    @Test
    fun completedDownloadWithExistingFile_createsTransferable() {
        val record = createHttpRecord(101L, "report.pdf", RecordStatus.FINISHED, createPhysicalFile = true)
        val handler = DownloadListTransferHandler { listOf(record) }

        val transferable = handler.createTransferable(null)
        assertNotNull(transferable, "Completed download with existing file on disk must be draggable")

        assertTrue(transferable!!.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
        val data = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
        assertNotNull(data)
        assertEquals(1, data!!.size)
        val file = data[0] as File
        assertEquals("report.pdf", file.name)
        assertTrue(file.exists())
    }

    @Test
    fun incompleteDownloads_cannotBeDragged() {
        val downloading = createHttpRecord(102L, "movie.mp4", RecordStatus.DOWNLOADING, createPhysicalFile = true)
        val paused = createHttpRecord(103L, "archive.zip", RecordStatus.PAUSED, createPhysicalFile = true)
        val error = createHttpRecord(104L, "music.mp3", RecordStatus.ERROR, createPhysicalFile = true)
        val assembling = createHttpRecord(105L, "game.iso", RecordStatus.ASSEMBLING, createPhysicalFile = true)
        val ready = createHttpRecord(106L, "setup.exe", RecordStatus.READY, createPhysicalFile = true)

        listOf(downloading, paused, error, assembling, ready).forEach { incomplete ->
            val handler = DownloadListTransferHandler { listOf(incomplete) }
            val transferable = handler.createTransferable(null)
            assertNull(transferable, "Download with status ${incomplete.status} must NOT be draggable")
        }
    }

    @Test
    fun missingSourceFile_handledGracefullyWithoutDrag() {
        // Finished according to record, but file is missing on disk (e.g. deleted by user or moved)
        val missing = createHttpRecord(107L, "deleted.txt", RecordStatus.FINISHED, createPhysicalFile = false)
        val handler = DownloadListTransferHandler { listOf(missing) }

        val transferable = handler.createTransferable(null)
        assertNull(transferable, "Finished download whose file does not exist on disk must not initiate a drag")
    }

    @Test
    fun multiSelection_dragsOnlyValidCompletedFiles() {
        val finishedValid1 = createHttpRecord(201L, "doc1.pdf", RecordStatus.FINISHED, createPhysicalFile = true)
        val finishedValid2 = createHttpRecord(202L, "doc2.pdf", RecordStatus.FINISHED, createPhysicalFile = true)
        val inProgress = createHttpRecord(203L, "in-progress.zip", RecordStatus.DOWNLOADING, createPhysicalFile = true)
        val missing = createHttpRecord(204L, "missing.png", RecordStatus.FINISHED, createPhysicalFile = false)

        val handler = DownloadListTransferHandler {
            listOf(finishedValid1, inProgress, finishedValid2, missing)
        }

        val transferable = handler.createTransferable(null)
        assertNotNull(transferable)

        @Suppress("UNCHECKED_CAST")
        val files = transferable!!.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
        assertEquals(2, files.size, "Only the 2 valid existing completed files should be included in the transfer")
        assertTrue(files.any { it.name == "doc1.pdf" })
        assertTrue(files.any { it.name == "doc2.pdf" })
        assertFalse(files.any { it.name == "in-progress.zip" })
        assertFalse(files.any { it.name == "missing.png" })
    }

    @Test
    fun transferableFlavorSupport_andRejection() {
        val file = File(tempDir, "test.txt").apply { writeText("hello") }
        val transferable = DownloadListTransferHandler.FileListTransferable(listOf(file))

        assertTrue(transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
        assertFalse(transferable.isDataFlavorSupported(DataFlavor.stringFlavor))
        assertFalse(transferable.isDataFlavorSupported(DataFlavor.imageFlavor))

        assertThrows(UnsupportedFlavorException::class.java) {
            transferable.getTransferData(DataFlavor.stringFlavor)
        }
    }
}

