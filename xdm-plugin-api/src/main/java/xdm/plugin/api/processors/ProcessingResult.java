package xdm.plugin.api.processors;

import java.io.File;

/**
 * Result returned by a PostDownloadProcessor execution.
 */
public class ProcessingResult {

    public enum Status {
        SUCCESS,
        MODIFIED,
        REJECTED,
        SKIPPED
    }

    private final Status status;
    private final File resultingFile;
    private final String message;

    public ProcessingResult(Status status, File resultingFile, String message) {
        this.status = status != null ? status : Status.SUCCESS;
        this.resultingFile = resultingFile;
        this.message = message != null ? message : "";
    }

    public static ProcessingResult success() {
        return new ProcessingResult(Status.SUCCESS, null, "Processing completed successfully");
    }

    public static ProcessingResult success(String message) {
        return new ProcessingResult(Status.SUCCESS, null, message);
    }

    public static ProcessingResult modified(File newFile, String message) {
        return new ProcessingResult(Status.MODIFIED, newFile, message);
    }

    public static ProcessingResult rejected(String reason) {
        return new ProcessingResult(Status.REJECTED, null, reason);
    }

    public static ProcessingResult skipped(String reason) {
        return new ProcessingResult(Status.SKIPPED, null, reason);
    }

    public Status getStatus() {
        return status;
    }

    public File getResultingFile() {
        return resultingFile;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccessful() {
        return status == Status.SUCCESS || status == Status.MODIFIED;
    }

    @Override
    public String toString() {
        return "ProcessingResult{" +
                "status=" + status +
                ", resultingFile=" + (resultingFile != null ? resultingFile.getName() : "null") +
                ", message='" + message + '\'' +
                '}';
    }
}
