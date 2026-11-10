# Troubleshooting & Self-Diagnostics Guide

This guide provides end-user troubleshooting steps, technical self-diagnostics, and repair procedures for resolving connectivity issues between the **XDM Enhanced Browser Extension** and the **Desktop Application**.

---

## 1. Quick 4-Step Recovery Checklist

If your browser extension shows **"XDM Enhanced Disconnected"**, follow these 4 steps in order:

<div class="card-box" style="margin: 1.5rem 0;">
  <ol style="margin: 0; padding-left: 1.5rem; line-height: 1.8;">
    <li><strong>Check if XDM Enhanced is running:</strong> Ensure the XDM Enhanced icon is visible in your Windows system tray or taskbar.</li>
    <li><strong>Click "Recheck Connection":</strong> Open the extension popup in your browser toolbar and click the <strong>Recheck Connection</strong> button.</li>
    <li><strong>Run Desktop Self-Repair:</strong> Open the desktop app &rarr; <strong>Settings</strong> &rarr; <strong>Browser</strong> &rarr; <strong>Diagnostics & Repair</strong> &rarr; click <strong>Repair Integration</strong>.</li>
    <li><strong>Reload the Extension:</strong> Open <code>chrome://extensions</code> or <code>about:addons</code>, locate XDM Enhanced, toggle it off and on, or click the reload button.</li>
  </ol>
</div>

---

## 2. Common Error Codes & Solutions

The extension self-diagnostics panel surfaces specific error codes to explain connection failures:

### `app_not_running`
- **Cause:** The desktop application is not running, or closed unexpectedly.
- **Solution:** Launch XDM Enhanced from the Start Menu or desktop shortcut. Ensure "Start XDM Enhanced on System Startup" is enabled in Settings > General.

### `port_in_use` (Port 8597 Conflict)
- **Cause:** Another process on your computer is occupying TCP port `8597`.
- **Diagnosis:** Run PowerShell to check what is listening on port 8597:
  ```powershell
  Get-NetTCPConnection -LocalPort 8597 -ErrorAction SilentlyContinue | Select-Object OwningProcess, State
  ```
- **Solution:** Terminate conflicting processes or reboot your computer.

### `host_missing` (Windows Native Messaging Bridge)
- **Cause:** The browser cannot locate the native messaging host manifest registered in the Windows registry, often caused by moving the XDM portable folder to another location.
- **Solution:** Run **Repair Integration** inside the desktop application under Settings > Browser. This re-registers the absolute manifest paths in Windows registry:
  - Chrome: `HKCU\Software\Google\Chrome\NativeMessagingHosts\xdm_bridge`
  - Edge: `HKCU\Software\Microsoft\Edge\NativeMessagingHosts\xdm_bridge`
  - Firefox: `HKCU\Software\Mozilla\NativeMessagingHosts\xdm_bridge`

### `permission_denied` / Firewall Block
- **Cause:** Third-party firewall or antivirus software is blocking loopback socket communication on `127.0.0.1`.
- **Solution:** Add an inbound/outbound exception for `xdm-app.exe` on local TCP port `8597`. Remember: XDM Enhanced loopback traffic never leaves your local computer.

---

## 3. Desktop Self-Repair Tool

XDM Enhanced includes an integrated self-repair utility that inspects and repairs integration components:

1. Open XDM Enhanced.
2. Navigate to **Settings** (⚙️) &rarr; **Browser Integration**.
3. Under **Diagnostics & Repair**, click **Check Integration**.
4. The tool verifies:
   - Registry manifest entries for all installed browsers.
   - Existence and executable permissions of `xdm-app.exe`.
   - Windows URI protocol association (`xdm-app://`).
   - Loopback HTTP port `8597` listener status.
5. If any check fails, click **Repair Integration** to restore all settings automatically.

---

## 4. Frequently Asked Questions (FAQ)

### Why doesn't the video download button appear on YouTube / Netflix?
Due to Digital Rights Management (DRM) encryption (Widevine / FairPlay), encrypted streams cannot be recorded or downloaded by standard download accelerators. For authorized YouTube media, use the official `ytdlp-downloader.xdmplugin` plugin with yt-dlp.

### Can I run multiple instances of XDM Enhanced?
No. XDM Enhanced enforces single-instance execution via a local file lock and socket handshake. Launching a second instance passes any command-line download arguments to the existing running process.

### How do I refresh an expired download link without losing progress?
Right-click the expired download in the list &rarr; choose **Refresh Link**. A dialog appears allowing you to visit the source page and capture a new authenticated URL. Once captured, downloading resumes immediately from the exact byte where it paused.

