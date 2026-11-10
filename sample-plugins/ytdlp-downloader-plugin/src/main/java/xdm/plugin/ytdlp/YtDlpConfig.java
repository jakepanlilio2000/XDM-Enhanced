package xdm.plugin.ytdlp;

import xdm.plugin.api.PluginConfig;
import java.io.File;

/**
 * Manages persistent configuration for the yt-dlp downloader plugin.
 */
public class YtDlpConfig {
    public static final String KEY_EXECUTABLE_PATH = "executable.path";
    public static final String KEY_FORMAT_PREFERENCE = "format.preference";
    public static final String KEY_OUTPUT_DIRECTORY = "output.directory";
    public static final String KEY_TIMEOUT_SECONDS = "timeout.seconds";
    public static final String KEY_EXTRACT_AUDIO = "extract.audio";
    public static final String KEY_AUDIO_FORMAT = "audio.format";

    public static final String DEFAULT_FORMAT = "bestvideo+bestaudio/best";
    public static final int DEFAULT_TIMEOUT_SECONDS = 3600;

    private final PluginConfig config;

    public YtDlpConfig(PluginConfig config) {
        this.config = config;
    }

    public String getExecutablePath() {
        String path = config.getString(KEY_EXECUTABLE_PATH, "yt-dlp");
        return (path != null && !path.trim().isEmpty()) ? path.trim() : "yt-dlp";
    }

    public void setExecutablePath(String path) {
        config.setString(KEY_EXECUTABLE_PATH, path != null ? path.trim() : "yt-dlp");
    }

    public String getFormatPreference() {
        return config.getString(KEY_FORMAT_PREFERENCE, DEFAULT_FORMAT);
    }

    public void setFormatPreference(String format) {
        config.setString(KEY_FORMAT_PREFERENCE, format != null ? format : DEFAULT_FORMAT);
    }

    public String getOutputDirectory() {
        return config.getString(KEY_OUTPUT_DIRECTORY, "");
    }

    public void setOutputDirectory(String dir) {
        config.setString(KEY_OUTPUT_DIRECTORY, dir != null ? dir.trim() : "");
    }

    public int getTimeoutSeconds() {
        return config.getInt(KEY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS);
    }

    public void setTimeoutSeconds(int timeout) {
        config.setInt(KEY_TIMEOUT_SECONDS, Math.max(30, timeout));
    }

    public boolean isExtractAudio() {
        return config.getBoolean(KEY_EXTRACT_AUDIO, false);
    }

    public void setExtractAudio(boolean extractAudio) {
        config.setBoolean(KEY_EXTRACT_AUDIO, extractAudio);
    }

    public String getAudioFormat() {
        return config.getString(KEY_AUDIO_FORMAT, "mp3");
    }

    public void setAudioFormat(String audioFormat) {
        config.setString(KEY_AUDIO_FORMAT, audioFormat != null ? audioFormat : "mp3");
    }

    public void save() {
        config.save();
    }
}

