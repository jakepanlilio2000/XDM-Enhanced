# Troubleshooting by Symptom

Use this symptom-based diagnostic decision tree to locate quick resolutions for common operational issues in **XDM Enhanced**.

---

## Diagnostic Decision Tree

```
[ Problem Detected ]
       │
       ├── Download issue ────────► Symptom 1 (Stuck Download) or Symptom 2 (Unknown Size)
       ├── Browser issue ─────────► Symptom 3 (Denied Handoff) or Symptom 4 (Host Not Found)
       ├── UI / Theme issue ──────► Symptom 5 (Missing Wallpaper / Corrupt Skin)
       ├── Plugin issue ──────────► Symptom 6 (Plugin Failed) or Symptom 7 (yt-dlp Task Not Visible)
       └── Installer issue ───────► Symptom 8 (Access Denied / SmartScreen Warning)
```

---

### Symptom 1: Download Is Stuck at 0% or Stalls Midway
- **Symptoms:** Transfer stays at 0 KB/s; progress percentage does not advance; network timeout retries appear in logs.
- **Probable Causes:**
  1. The download URL expired due to server-side session timeouts.
  2. The destination drive or temporary chunk directory has run out of disk space.
  3. Max connection streams exceeds server concurrency limits (causing server to ban the IP).
- **Resolution:**
  1. **Refresh Link:** Right-click the transfer &rarr; select **Refresh Link** &rarr; copy the refreshed URL from your browser to resume without restarting.
  2. **Check Disk Space:** Ensure your temporary directory has at least 1.5× the total download size free.
  3. **Lower Connections:** In **Settings > Connection**, lower **Max Connections per Download** from 16 to 4 or 8 streams.

---

### Symptom 2: File Size Shows "Unknown" or Name Is Garbled
- **Symptoms:** The confirmation dialog displays "Unknown size", or the filename shows random URL parameters instead of the real name.
- **Probable Causes:** The remote server omits standard `Content-Length` or `Content-Disposition` headers, or streams dynamically (chunked transfer encoding).
- **Resolution:**
  1. You can still download the file; XDM Enhanced will stream it sequentially until the remote socket closes.
  2. Manually rename the file in the **File Name** text box before clicking **Download Now**.

---

### Symptom 3: Browser Download Is Denied Handoff (Browser Downloads Instead)
- **Symptoms:** Clicking a link in Chrome or Firefox downloads via the browser's internal manager instead of opening XDM Enhanced.
- **Probable Causes:**
  1. The file extension is not registered in XDM's capture list.
  2. The target domain is on the blocked exceptions list.
  3. The extension is disconnected from the desktop client.
- **Resolution:**
  1. Check extension status; if disconnected, follow [[Browser Extension Disconnected|Browser-Extension-Disconnected]].
  2. In desktop XDM Enhanced, open **Settings > Monitoring** &rarr; add the file extension (e.g. `.dmg`, `.pkg`, `.iso`) to **File Types**.
  3. Verify the domain is not listed under **Blocked Sites / Do Not Capture**.

---

### Symptom 4: Native Host Not Found (`host_missing`)
- **Symptoms:** The extension options panel reports `host_missing`, or native messaging handshakes fail.
- **Probable Causes:** XDM Enhanced portable folder was relocated, breaking absolute paths in Windows registry manifests.
- **Resolution:**
  1. Open desktop XDM Enhanced.
  2. Go to **Settings > Browser Integration** &rarr; click **Repair Integration**.
  3. Restart your browser.

---

### Symptom 5: Missing Skin Background or Corrupted Appearance
- **Symptoms:** A custom skin package is selected, but the main window background remains blank or defaults to fallback gray.
- **Probable Causes:**
  1. The `.xdmskin` archive contains missing image paths in `skin.json`.
  2. The image file is corrupt or exceeds supported memory limits.
- **Resolution:**
  1. The skin engine automatically falls back to a safe known-good theme to prevent application crashes.
  2. Re-select the built-in **XDM Enhanced** or **Original XDM** theme in **Settings > Appearance**.
  3. If authoring a skin, check image paths against the [[Skin Schema & UI Slots|Skin-Schema-and-UI-Slots]] specification.

---

### Symptom 6: Plugin Fails to Load on Startup
- **Symptoms:** An installed plugin does not appear in the active list, or Settings > Plugins shows a red error status.
- **Probable Causes:**
  1. The plugin requires an API version incompatible with host version `v1.0.0-beta.6`.
  2. The plugin threw an unhandled exception during `onInitialize()`.
- **Resolution:**
  1. Select the plugin in **Settings > Plugins** &rarr; click **View Details** to inspect the stack trace.
  2. Verify that `plugin.json` specifies a compatible API range (e.g. `[1.0.0, 2.0.0)`).
  3. Click **Disable** or **Uninstall** to restore host stability.

---

### Symptom 7: yt-dlp Video Download Task Does Not Appear in Central List
- **Symptoms:** Running yt-dlp downloads media in the background, but no progress bar or item appears in the main download table.
- **Probable Causes:**
  1. yt-dlp executable path is not configured in Settings > Plugins.
  2. Subprocess failed due to missing Python or outdated yt-dlp binary.
- **Resolution:**
  1. Ensure `yt-dlp.exe` is installed and runnable from command prompt (`yt-dlp --version`).
  2. In XDM Enhanced &rarr; **Settings > Plugins** &rarr; select **yt-dlp Downloader** &rarr; click **Configure** &rarr; set the absolute path to your `yt-dlp` executable.
  3. Ensure the plugin version is updated to `v1.0.0-beta.6` (which introduces the public `PluginDownloadBridge` integration).

---

### Symptom 8: Windows SmartScreen or "Access Denied" Warning
- **Symptoms:** Windows displays "Windows protected your PC" or installer denies file copy permissions.
- **Probable Causes:** Pre-release community builds are unsigned or test-signed; installation to `C:\Program Files` requires administrative privileges.
- **Resolution:**
  1. On the Windows SmartScreen dialog, click **More info** &rarr; click **Run anyway**.
  2. Right-click the installer `.exe` &rarr; choose **Run as administrator**.
  3. Or use the [Windows Portable Bundle](Installation-Guide#option-b-windows-portable-bundle-zero-install) which requires zero administrator rights.

---

## Related Articles

* [[Browser Extension Disconnected|Browser-Extension-Disconnected]]: Detailed extension bridge troubleshooting.
* [[HTTP & HTTPS Transfers|HTTP-and-HTTPS-Transfers]]: Deep dive into segmented connections and link refreshing.
* Still having trouble? [Open an issue on GitHub](https://github.com/jakepanlilio2000/XDM-Enhanced/issues) with reproduction details.

