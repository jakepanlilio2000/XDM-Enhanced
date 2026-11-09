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
import org.junit.jupiter.api.io.TempDir
import xdm.app.AppConfig
import xdm.app.AppContext
import xdm.app.rules.PostDownloadActionEngine
import xdm.app.rules.PostDownloadRule
import java.io.File
import java.nio.file.Path

class PostDownloadActionTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        PostDownloadActionEngine.resetExecutionHistory()
        AppContext.config = AppConfig(tempDir.toString())
    }

    @AfterEach
    fun tearDown() {
        PostDownloadActionEngine.resetExecutionHistory()
    }

    @Test
    fun testVariableSubstitutionWithSpacesAndUnicode() {
        val testFile = File("C:\\Downloads\\日本語 フォルダ\\Sample Video (2026).mp4")

        // %file%
        assertEquals(
            testFile.path,
            PostDownloadActionEngine.substituteVariables("%file%", testFile)
        )

        // Legacy [file]
        assertEquals(
            testFile.path,
            PostDownloadActionEngine.substituteVariables("[file]", testFile)
        )

        // %file_name%
        assertEquals(
            "Sample Video (2026).mp4",
            PostDownloadActionEngine.substituteVariables("%file_name%", testFile)
        )

        // %file_extension%
        assertEquals(
            "mp4",
            PostDownloadActionEngine.substituteVariables("%file_extension%", testFile)
        )

        // %file_directory%
        assertEquals(
            testFile.parent,
            PostDownloadActionEngine.substituteVariables("%file_directory%", testFile)
        )

        // %absolute_file_path%
        assertEquals(
            testFile.absoluteFile.normalize().path,
            PostDownloadActionEngine.substituteVariables("%absolute_file_path%", testFile)
        )

        // %absolute_directory_path%
        assertEquals(
            testFile.absoluteFile.parentFile.normalize().path,
            PostDownloadActionEngine.substituteVariables("%absolute_directory_path%", testFile)
        )

        // Combined token
        val combined = "%file_directory%\\converted_%file_name%"
        val expected = "${testFile.parent}\\converted_Sample Video (2026).mp4"
        assertEquals(expected, PostDownloadActionEngine.substituteVariables(combined, testFile))
    }

    @Test
    fun testFileWithoutExtension() {
        val testFile = File(tempDir.toFile(), "README_WITHOUT_EXTENSION")

        assertEquals(
            "",
            PostDownloadActionEngine.substituteVariables("%file_extension%", testFile)
        )
        assertEquals(
            "README_WITHOUT_EXTENSION",
            PostDownloadActionEngine.substituteVariables("%file_name%", testFile)
        )
    }

    @Test
    fun testArgumentTokenizationAndQuoting() {
        val testFile = File(tempDir.toFile(), "my video with spaces.mkv")
        val template = """"C:\Program Files\ffmpeg.exe" -i "%file%" -c copy "%file_directory%\out.mp4""""

        val args = PostDownloadActionEngine.buildArguments(template, testFile)
        assertEquals(6, args.size)
        assertEquals("C:\\Program Files\\ffmpeg.exe", args[0])
        assertEquals("-i", args[1])
        assertEquals(testFile.path, args[2])
        assertEquals("-c", args[3])
        assertEquals("copy", args[4])
        assertEquals("${testFile.parent}\\out.mp4", args[5])
    }

    @Test
    fun testUnquotedPlaceholderWithSpacesRemainsSingleArg() {
        val testFile = File(tempDir.toFile(), "my video with spaces.mkv")
        // Even if user forgot quotes around %file%, it should NOT be split into multiple argv items
        val template = "ffprobe -show_format %file%"

        val args = PostDownloadActionEngine.buildArguments(template, testFile)
        assertEquals(3, args.size)
        assertEquals("ffprobe", args[0])
        assertEquals("-show_format", args[1])
        assertEquals(testFile.path, args[2])
    }

    @Test
    fun testTemplateWithoutPlaceholdersAppendsFile() {
        val testFile = File(tempDir.toFile(), "document.pdf")
        val template = "acrobat.exe /p"

        val args = PostDownloadActionEngine.buildArguments(template, testFile)
        assertEquals(3, args.size)
        assertEquals("acrobat.exe", args[0])
        assertEquals("/p", args[1])
        assertEquals(testFile.path, args[2])
    }

    @Test
    fun testExtensionMatching() {
        val rule = PostDownloadRule(
            name = "Videos",
            extensions = listOf("mp4", "mkv", "tar.gz"),
            command = "ffmpeg -i %file%",
            enabled = true
        )

        // Case insensitivity
        assertTrue(rule.matches("clip.MP4"))
        assertTrue(rule.matches("MOVIE.MKV"))

        // Compound extension
        assertTrue(rule.matches("archive.tar.gz"))
        assertTrue(rule.matches("ARCHIVE.TAR.GZ"))

        // Non-matches
        assertFalse(rule.matches("clip.mp3"))
        assertFalse(rule.matches("archive.gz")) // not tar.gz
        assertFalse(rule.matches("test.p4")) // partial substring must not match

        // Wildcard rule
        val wildcardRule = PostDownloadRule(
            name = "All",
            extensions = listOf("*"),
            command = "logger %file%",
            enabled = true
        )
        assertTrue(wildcardRule.matches("anything.xyz"))
        assertTrue(wildcardRule.matches("no_extension"))

        // Empty extensions rule matches all
        val emptyRule = PostDownloadRule(
            name = "Empty",
            extensions = emptyList(),
            command = "logger %file%",
            enabled = true
        )
        assertTrue(emptyRule.matches("file.dat"))
    }

    @Test
    fun testRuleParsingAndFormatting() {
        val parsed = PostDownloadRule.parseExtensions("mp4, .mkv; avi\ttar.gz \n webm")
        assertEquals(listOf("mp4", "mkv", "avi", "tar.gz", "webm"), parsed)

        val formatted = PostDownloadRule.formatExtensions(parsed)
        assertEquals("mp4, mkv, avi, tar.gz, webm", formatted)
    }

    @Test
    fun testRuleOrderingAndFallback() {
        val rule1 = PostDownloadRule(
            name = "MP4 Rule",
            extensions = listOf("mp4"),
            command = "run_mp4 %file%",
            enabled = true
        )
        val rule2 = PostDownloadRule(
            name = "Video Rule",
            extensions = listOf("mp4", "mkv"),
            command = "run_video %file%",
            enabled = true
        )
        val rule3 = PostDownloadRule(
            name = "Disabled Rule",
            extensions = listOf("avi"),
            command = "run_avi %file%",
            enabled = false
        )

        val rules = listOf(rule1, rule2, rule3)
        val mp4File = File("movie.mp4")
        val mkvFile = File("movie.mkv")
        val aviFile = File("movie.avi")
        val zipFile = File("data.zip")

        // First matching rule wins
        assertEquals("run_mp4 %file%", PostDownloadActionEngine.resolveCommand(mp4File, rules, "default_cmd"))
        assertEquals("run_video %file%", PostDownloadActionEngine.resolveCommand(mkvFile, rules, "default_cmd"))

        // Disabled rule skipped, falls back to default
        assertEquals("default_cmd", PostDownloadActionEngine.resolveCommand(aviFile, rules, "default_cmd"))

        // No match falls back to default
        assertEquals("default_cmd", PostDownloadActionEngine.resolveCommand(zipFile, rules, "default_cmd"))

        // No match and no default returns null
        assertNull(PostDownloadActionEngine.resolveCommand(zipFile, rules, ""))
    }

    @Test
    fun testSecurityConstraintsDirectExecutableLaunchBlocked() {
        val downloadedExe = File(tempDir.toFile(), "malware.exe")
        downloadedExe.writeText("binary content")

        // Direct executable name
        assertThrows(SecurityException::class.java) {
            PostDownloadActionEngine.validateCommand(listOf(downloadedExe.path, "arg1"), downloadedExe)
        }

        // Target file name
        assertThrows(SecurityException::class.java) {
            PostDownloadActionEngine.validateCommand(listOf("malware.exe", "arg1"), downloadedExe)
        }

        // Empty command
        assertThrows(IllegalArgumentException::class.java) {
            PostDownloadActionEngine.validateCommand(emptyList(), downloadedExe)
        }
    }

    @Test
    fun testExecutionDeduplication() {
        AppContext.config.runCommand = true
        AppContext.config.customCommand = "echo test"

        val sampleFile = File(tempDir.toFile(), "done.txt")
        sampleFile.writeText("finished")

        val future1 = PostDownloadActionEngine.execute(sampleFile, downloadId = 42L)
        assertNotNull(future1)

        // Second call with same downloadId must be skipped
        val future2 = PostDownloadActionEngine.execute(sampleFile, downloadId = 42L)
        assertFalse(future2.get())
    }

    @Test
    fun testAppConfigPersistence() {
        val config = AppContext.config as AppConfig
        config.runCommand = true
        config.customCommand = "fallback_cmd %file%"

        val rule1 = PostDownloadRule(
            id = "r1",
            name = "Convert Audio",
            extensions = listOf("mp3", "flac"),
            command = "ffmpeg -i %file% -c:a aac %file_directory%\\out.m4a",
            enabled = true
        )
        val rule2 = PostDownloadRule(
            id = "r2",
            name = "Extract",
            extensions = listOf("zip", "7z"),
            command = "7z x %file%",
            enabled = false
        )
        config.postDownloadRules = listOf(rule1, rule2)
        config.save()

        // Reload fresh config from disk
        val loaded = AppConfig(tempDir.toString())
        loaded.load()

        assertTrue(loaded.runCommand)
        assertEquals("fallback_cmd %file%", loaded.customCommand)
        assertEquals(2, loaded.postDownloadRules.size)

        val loadedR1 = loaded.postDownloadRules[0]
        assertEquals("r1", loadedR1.id)
        assertEquals("Convert Audio", loadedR1.name)
        assertEquals(listOf("mp3", "flac"), loadedR1.extensions)
        assertEquals("ffmpeg -i %file% -c:a aac %file_directory%\\out.m4a", loadedR1.command)
        assertTrue(loadedR1.enabled)

        val loadedR2 = loaded.postDownloadRules[1]
        assertEquals("r2", loadedR2.id)
        assertEquals("Extract", loadedR2.name)
        assertEquals(listOf("zip", "7z"), loadedR2.extensions)
        assertEquals("7z x %file%", loadedR2.command)
        assertFalse(loadedR2.enabled)
    }
}

