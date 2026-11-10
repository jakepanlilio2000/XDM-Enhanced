package xdm.plugin.ytdlp;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Validates the configured yt-dlp executable using strictly structured process arguments.
 * Avoids any unsafe shell evaluation.
 */
public class YtDlpExecutableValidator {

    public static class ValidationResult {
        private final boolean valid;
        private final String version;
        private final String errorMessage;

        public ValidationResult(boolean valid, String version, String errorMessage) {
            this.valid = valid;
            this.version = version != null ? version : "";
            this.errorMessage = errorMessage != null ? errorMessage : "";
        }

        public boolean isValid() {
            return valid;
        }

        public String getVersion() {
            return version;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public String toString() {
            return valid ? "yt-dlp version " + version : "Invalid executable: " + errorMessage;
        }
    }

    /**
     * Validates that the specified path resolves to a working yt-dlp executable.
     * Uses structured arguments without invoking cmd.exe or /bin/sh.
     *
     * @param executablePath Path or binary name
     * @return ValidationResult indicating success, version, or error
     */
    public static ValidationResult validate(String executablePath) {
        if (executablePath == null || executablePath.trim().isEmpty()) {
            return new ValidationResult(false, null, "Executable path cannot be blank.");
        }

        String path = executablePath.trim();

        // If an explicit file path is provided, verify it exists
        if (path.contains("/") || path.contains("\\")) {
            File file = new File(path);
            if (!file.exists()) {
                return new ValidationResult(false, null, "File does not exist: " + file.getAbsolutePath());
            }
            if (file.isDirectory()) {
                return new ValidationResult(false, null, "Path points to a directory, not an executable file: " + file.getAbsolutePath());
            }
        }

        List<String> command = new ArrayList<>();
        command.add(path);
        command.add("--version");

        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) output.append("\n");
                    output.append(line.trim());
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ValidationResult(false, null, "Timeout waiting for yt-dlp --version");
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                String version = output.toString().trim();
                return new ValidationResult(true, version, null);
            } else {
                return new ValidationResult(false, null, "yt-dlp exited with code " + exitCode + ": " + output);
            }
        } catch (Exception e) {
            return new ValidationResult(false, null, "Failed to execute yt-dlp: " + e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}

