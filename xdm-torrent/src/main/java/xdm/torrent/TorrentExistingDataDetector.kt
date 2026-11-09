package xdm.torrent

import xdm.core.util.FileUtils
import java.io.File
import java.nio.file.Files

/**
 * Result of detecting existing files on disk for a given torrent entry.
 */
data class ExistingFileInfo(
    val fileIndex: Int,
    val relativePath: String,
    val expectedSize: Long,
    val actualSize: Long,
    val exists: Boolean,
    val isSafe: Boolean
)

/**
 * Comprehensive report of existing torrent data in a target destination folder.
 */
data class ExistingTorrentDataCheck(
    val hasExistingData: Boolean,
    val existingFilesCount: Int,
    val totalFilesCount: Int,
    val existingBytes: Long,
    val totalExpectedBytes: Long,
    val isSizeComplete: Boolean,
    val files: List<ExistingFileInfo> = emptyList()
) {
    val hasUnsafePaths: Boolean get() = files.any { !it.isSafe }
}

/**
 * Safely inspects the chosen destination directory to detect whether any or all of the
 * torrent's files already exist on disk prior to starting download or re-checking.
 *
 * Validates all candidate paths against directory traversal and symbolic link escapes
 * so that files outside the chosen destination are never accessed, verified, or modified.
 */
object TorrentExistingDataDetector {

    /**
     * Checks whether files belonging to [metadata] already exist in [saveDir].
     */
    fun detect(saveDir: File, metadata: TorrentMetadata): ExistingTorrentDataCheck {
        return detect(saveDir, metadata.files, metadata.totalSize)
    }

    /**
     * Checks whether files in [fileEntries] already exist in [saveDir].
     */
    fun detect(saveDir: File, fileEntries: List<TorrentFileEntry>, totalExpectedSize: Long): ExistingTorrentDataCheck {
        if (!saveDir.exists() || !saveDir.isDirectory) {
            return ExistingTorrentDataCheck(
                hasExistingData = false,
                existingFilesCount = 0,
                totalFilesCount = fileEntries.size,
                existingBytes = 0L,
                totalExpectedBytes = totalExpectedSize,
                isSizeComplete = false,
                files = emptyList()
            )
        }

        val canonicalSaveDir = try {
            saveDir.canonicalFile.toPath().normalize()
        } catch (_: Throwable) {
            return ExistingTorrentDataCheck(
                hasExistingData = false,
                existingFilesCount = 0,
                totalFilesCount = fileEntries.size,
                existingBytes = 0L,
                totalExpectedBytes = totalExpectedSize,
                isSizeComplete = false,
                files = emptyList()
            )
        }

        val results = ArrayList<ExistingFileInfo>(fileEntries.size)
        var existingCount = 0
        var existingBytes = 0L

        for (entry in fileEntries) {
            val rawPath = entry.path
            val hasTraversalSegment = rawPath.split('/', '\\').any { it == ".." || it == "." }
            val isAbsolute = rawPath.startsWith("/") || rawPath.startsWith("\\") || Regex("^[a-zA-Z]:").containsMatchIn(rawPath)

            val sanitized = TorrentMetadataParser.sanitizeTorrentFilePath(entry.path, entry.index)
            val targetFile = File(saveDir, sanitized)

            var isSafe = false
            var exists = false
            var actualSize = 0L

            if (!hasTraversalSegment && !isAbsolute) {
                try {
                    val canonicalTarget = targetFile.canonicalFile.toPath().normalize()
                    // Path traversal protection: target must reside strictly inside canonicalSaveDir
                    if (canonicalTarget.startsWith(canonicalSaveDir)) {
                        val targetPath = targetFile.toPath()
                        // Symbolic link protection: ensure target symlink does not escape canonicalSaveDir
                        val symlinkSafe = if (Files.isSymbolicLink(targetPath)) {
                            val linkTarget = Files.readSymbolicLink(targetPath)
                            val resolved = targetPath.parent?.resolve(linkTarget)?.normalize()
                            resolved != null && resolved.toFile().canonicalFile.toPath().normalize().startsWith(canonicalSaveDir)
                        } else {
                            true
                        }

                        if (symlinkSafe) {
                            isSafe = true
                            if (targetFile.exists() && targetFile.isFile) {
                                exists = true
                                actualSize = targetFile.length()
                                existingCount++
                                existingBytes += actualSize
                            }
                        }
                    }
                } catch (_: Throwable) {
                    isSafe = false
                }
            }

            results.add(
                ExistingFileInfo(
                    fileIndex = entry.index,
                    relativePath = sanitized,
                    expectedSize = entry.size,
                    actualSize = actualSize,
                    exists = exists,
                    isSafe = isSafe
                )
            )
        }

        val hasData = existingCount > 0 && existingBytes > 0L
        val sizeComplete = existingCount == fileEntries.size && totalExpectedSize > 0L && existingBytes >= totalExpectedSize

        return ExistingTorrentDataCheck(
            hasExistingData = hasData,
            existingFilesCount = existingCount,
            totalFilesCount = fileEntries.size,
            existingBytes = existingBytes,
            totalExpectedBytes = totalExpectedSize,
            isSizeComplete = sizeComplete,
            files = results
        )
    }
}

