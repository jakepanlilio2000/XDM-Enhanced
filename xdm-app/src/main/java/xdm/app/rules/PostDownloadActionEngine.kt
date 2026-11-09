package xdm.app.rules

import xdm.app.AppContext
import xdm.core.util.Logger
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Executes post-download actions based on file extensions, placeholder substitution,
 * and security constraints.
 */
object PostDownloadActionEngine {
    const val VAR_FILE = "%file%"
    const val VAR_FILE_LEGACY = "[file]"
    const val VAR_FILE_NAME = "%file_name%"
    const val VAR_FILE_EXTENSION = "%file_extension%"
    const val VAR_FILE_DIRECTORY = "%file_directory%"
    const val VAR_ABSOLUTE_FILE_PATH = "%absolute_file_path%"
    const val VAR_ABSOLUTE_DIRECTORY_PATH = "%absolute_directory_path%"

    val ALL_VARIABLES = listOf(
        VAR_FILE,
        VAR_FILE_NAME,
        VAR_FILE_EXTENSION,
        VAR_FILE_DIRECTORY,
        VAR_ABSOLUTE_FILE_PATH,
        VAR_ABSOLUTE_DIRECTORY_PATH
    )

    private val executedDownloadIds = Collections.synchronizedSet(HashSet<Long>())
    private val actionExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "PostDownloadActionWorker").apply { isDaemon = true }
    }

    /**
     * Clears tracked download IDs (primarily for testing and restart scenarios).
     */
    fun resetExecutionHistory() {
        executedDownloadIds.clear()
    }

    /**
     * Splits a command template into distinct argv tokens, honoring quotes and escapes.
     */
    fun tokenizeCommand(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inDoubleQuote = false
        var inSingleQuote = false
        var i = 0
        val trimmed = input.trim()

        while (i < trimmed.length) {
            val ch = trimmed[i]
            when {
                ch == '\\' && i + 1 < trimmed.length && !inSingleQuote -> {
                    val next = trimmed[i + 1]
                    if (next == '"' || next == '\\') {
                        current.append(next)
                        i++
                    } else {
                        current.append(ch)
                    }
                }
                ch == '"' && !inSingleQuote -> {
                    inDoubleQuote = !inDoubleQuote
                }
                ch == '\'' && !inDoubleQuote -> {
                    inSingleQuote = !inSingleQuote
                }
                ch.isWhitespace() && !inDoubleQuote && !inSingleQuote -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.setLength(0)
                    }
                }
                else -> current.append(ch)
            }
            i++
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }

    /**
     * Substitutes all supported placeholders in a single argument string.
     */
    fun substituteVariables(token: String, targetFile: File): String {
        val rawPath = targetFile.path
        val fileName = targetFile.name
        val fileExt = targetFile.extension // empty string if no extension
        val fileDir = targetFile.parent ?: ""
        val absPath = targetFile.absoluteFile.normalize().path
        val absDir = targetFile.absoluteFile.parentFile?.normalize()?.path ?: ""

        var s = token
        s = s.replace(VAR_FILE, rawPath)
        s = s.replace(VAR_FILE_LEGACY, rawPath)
        s = s.replace(VAR_FILE_NAME, fileName)
        s = s.replace(VAR_FILE_EXTENSION, fileExt)
        s = s.replace(VAR_FILE_DIRECTORY, fileDir)
        s = s.replace(VAR_ABSOLUTE_FILE_PATH, absPath)
        s = s.replace(VAR_ABSOLUTE_DIRECTORY_PATH, absDir)
        return s
    }

    /**
     * Builds structured argv arguments from a command template and a target file.
     * If no placeholder is present in the template, appends the target file path.
     */
    fun buildArguments(commandTemplate: String, targetFile: File): List<String> {
        val trimmed = commandTemplate.trim()
        if (trimmed.isEmpty()) return emptyList()

        val rawTokens = tokenizeCommand(trimmed)
        if (rawTokens.isEmpty()) return emptyList()

        val hasPlaceholder = rawTokens.any { t ->
            t.contains(VAR_FILE) || t.contains(VAR_FILE_LEGACY) ||
            t.contains(VAR_FILE_NAME) || t.contains(VAR_FILE_EXTENSION) ||
            t.contains(VAR_FILE_DIRECTORY) || t.contains(VAR_ABSOLUTE_FILE_PATH) ||
            t.contains(VAR_ABSOLUTE_DIRECTORY_PATH)
        }

        val substituted = rawTokens.map { substituteVariables(it, targetFile) }.toMutableList()
        if (!hasPlaceholder) {
            substituted.add(targetFile.path)
        }
        return substituted
    }

    /**
     * Validates that the command adheres to security constraints:
     * - Must not be empty.
     * - Never launch downloaded executables directly as the program executable.
     */
    fun validateCommand(argv: List<String>, targetFile: File) {
        if (argv.isEmpty()) {
            throw IllegalArgumentException("Command cannot be empty")
        }
        val program = argv[0].trim()
        if (program.isEmpty()) {
            throw IllegalArgumentException("Program executable cannot be blank")
        }

        val targetNorm = targetFile.absoluteFile.normalize().path
        val progFile = File(program)
        val progNorm = progFile.absoluteFile.normalize().path

        // Check if program is directly the target file
        if (progNorm.equals(targetNorm, ignoreCase = true) ||
            program.equals(targetFile.name, ignoreCase = true) ||
            program.equals(targetFile.path, ignoreCase = true)
        ) {
            throw SecurityException("Security restriction: Running downloaded files directly as executable programs is prohibited: $program")
        }
    }

    /**
     * Selects the command to execute for a target file from an ordered list of rules,
     * falling back to [defaultCommand] if no rule matches.
     */
    fun resolveCommand(
        targetFile: File,
        rules: List<PostDownloadRule>,
        defaultCommand: String?
    ): String? {
        val matchingRule = rules.firstOrNull { it.enabled && it.matches(targetFile.name) }
        if (matchingRule != null) {
            val cmd = matchingRule.command.trim()
            if (cmd.isNotEmpty()) return cmd
        }
        val fallback = defaultCommand?.trim() ?: ""
        if (fallback.isNotEmpty()) {
            return fallback
        }
        return null
    }

    /**
     * Executes the post-download action asynchronously.
     * Guarantees:
     * - Runs at most once per [downloadId].
     * - Does not launch if [AppContext.config.runCommand] is false.
     * - Catches all exceptions so download status is never marked as failed.
     */
    fun execute(targetFile: File, downloadId: Long = -1L): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        if (!AppContext.config.runCommand) {
            Logger.info("Post-download action skipped: feature disabled in configuration")
            future.complete(false)
            return future
        }

        if (downloadId > 0 && !executedDownloadIds.add(downloadId)) {
            Logger.info("Post-download action skipped: already executed for download ID $downloadId")
            future.complete(false)
            return future
        }

        actionExecutor.submit {
            try {
                val commandTemplate = resolveCommand(
                    targetFile,
                    AppContext.config.postDownloadRules,
                    AppContext.config.customCommand
                )

                if (commandTemplate.isNullOrBlank()) {
                    Logger.info("No matching post-download rule or default command for: ${targetFile.name}")
                    future.complete(false)
                    return@submit
                }

                val argv = buildArguments(commandTemplate, targetFile)
                validateCommand(argv, targetFile)

                Logger.info("Executing post-download action: $argv")
                val pb = ProcessBuilder(argv)
                targetFile.parentFile?.let { if (it.exists()) pb.directory(it) }

                val process = pb.start()
                // Drain streams in background to prevent buffer deadlock
                actionExecutor.submit {
                    runCatching { process.inputStream.transferTo(java.io.OutputStream.nullOutputStream()) }
                }
                actionExecutor.submit {
                    runCatching { process.errorStream.transferTo(java.io.OutputStream.nullOutputStream()) }
                }

                future.complete(true)
            } catch (t: Throwable) {
                Logger.error("Error executing post-download action for file: ${targetFile.name}", t)
                // Failure must NEVER affect download success
                future.complete(false)
            }
        }

        return future
    }

    data class TestExecutionResult(
        val success: Boolean,
        val commandLine: List<String>,
        val exitCode: Int?,
        val output: String,
        val errorMessage: String? = null
    )

    /**
     * Executes a manual test of a command template against [sampleFile], capturing output and exit code.
     */
    fun testCommand(commandTemplate: String, sampleFile: File): TestExecutionResult {
        return try {
            val argv = buildArguments(commandTemplate, sampleFile)
            validateCommand(argv, sampleFile)

            val pb = ProcessBuilder(argv)
            sampleFile.parentFile?.let { if (it.exists()) pb.directory(it) }
            pb.redirectErrorStream(true)

            val process = pb.start()
            val outputBuilder = StringBuilder()

            val readerThread = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            outputBuilder.appendLine(line)
                        }
                    }
                }
            }
            readerThread.start()

            val finished = process.waitFor(8, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                TestExecutionResult(
                    success = false,
                    commandLine = argv,
                    exitCode = null,
                    output = outputBuilder.toString(),
                    errorMessage = "Command execution timed out after 8 seconds"
                )
            } else {
                readerThread.join(1000)
                val code = process.exitValue()
                TestExecutionResult(
                    success = (code == 0),
                    commandLine = argv,
                    exitCode = code,
                    output = outputBuilder.toString(),
                    errorMessage = if (code != 0) "Process exited with code $code" else null
                )
            }
        } catch (t: Throwable) {
            TestExecutionResult(
                success = false,
                commandLine = runCatching { buildArguments(commandTemplate, sampleFile) }.getOrDefault(emptyList()),
                exitCode = null,
                output = "",
                errorMessage = t.message ?: t.javaClass.simpleName
            )
        }
    }
}

