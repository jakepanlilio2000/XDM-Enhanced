# Plugin Installation & Management Guide

XDM Enhanced features an extensible Java Plugin Platform (SPI v2) that allows developers and users to add third-party download extractors, checksum tools, and custom protocol handlers.

---

## 1. Official Sample Plugins (`v1.0.0-beta.6`)

The following official sample plugins are published alongside desktop releases:

| Plugin Name | Package Filename | Capabilities | Description |
|:---|:---|:---:|:---|
| **Checksum Verifier** | `checksum-verifier.xdmplugin` | `INTEGRITY_VERIFIER`, `SETTINGS_PANEL` | Automatically calculates MD5, SHA-1, and SHA-256 hashes upon download completion. |
| **yt-dlp Downloader** | `ytdlp-downloader.xdmplugin` | `DOWNLOAD_PROVIDER`, `SETTINGS_PANEL` | Integrates `yt-dlp` media extraction with the central XDM download scheduler. |

---

## 2. Installing a Plugin (`.xdmplugin`)

1. Download a verified `.xdmplugin` file (e.g. from [GitHub Releases](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.0.0-beta.6)).
2. Launch XDM Enhanced &rarr; open **Settings** (⚙️) &rarr; select **Plugins**.
3. Click **Install Plugin** &rarr; browse and select your downloaded `.xdmplugin` file.
4. XDM Enhanced validates the archive manifest (`plugin.json`), checks API version compatibility, and installs the plugin into `~/.xdm/plugins/`.
5. The plugin appears immediately in the active plugins table with its capability badges.

---

## 3. Configuring the yt-dlp Downloader Plugin

The `ytdlp-downloader.xdmplugin` delegates media parsing to a local `yt-dlp` binary on your system:

### Prerequisites:
- Ensure `yt-dlp` is installed on your computer (`yt-dlp --version` in terminal).

### Configuration Steps:
1. Open XDM Enhanced &rarr; **Settings > Plugins**.
2. Select **yt-dlp Video Downloader** &rarr; click **Configure**.
3. In the configuration dialog, click **Browse** and select your `yt-dlp` executable (e.g. `C:\Tools\yt-dlp.exe` or `/usr/local/bin/yt-dlp`).
4. Click **Test Executable**. A green checkmark confirms that the binary is runnable.
5. Click **Save**.

### Central Download Scheduler Integration:
When yt-dlp downloads are triggered, the plugin routes the task through the public `PluginDownloadBridge`. Tasks appear directly in the main XDM transfer table with:
- Live progress percentages
- Transfer speed graphs and ETA
- `CAT_VIDEOS` automatic categorization
- Persistent download history

---

## 4. Enabling, Disabling & Uninstalling Plugins

In **Settings > Plugins**:
- **Enable / Disable:** Uncheck the checkbox next to any plugin to temporarily disable its event listeners and providers without deleting its files.
- **Uninstall:** Select a plugin and click **Uninstall** to completely delete the package from your local storage.

---

## 5. Security & Isolation Protections

- **API Version Compatibility:** Plugins declare their supported version range (e.g. `[1.0.0, 2.0.0)`). Incompatible plugins are blocked from loading to prevent host application instability.
- **ClassLoader Isolation:** Each plugin loads within an independent child classloader, preventing dependency conflicts between different add-ons.
- **Subprocess Safety:** External binaries are executed via `ProcessBuilder` with explicit array arguments, completely eliminating shell injection vectors.

---

## Related Articles

* [[Plugin SDK Quick Start|Plugin-SDK-Quick-Start]]: Develop your own plugins using `xdm-plugin-api`.
* [[Provider Task Integration|Provider-Task-Integration]]: Submitting tasks to the central scheduler.
* [[Troubleshooting by Symptom|Troubleshooting-by-Symptom]]: Diagnosing plugin load failures.

