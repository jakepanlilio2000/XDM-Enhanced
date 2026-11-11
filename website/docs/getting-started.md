# Getting Started with XDM Enhanced

This guide walks you through installing XDM Enhanced on your computer, configuring essential settings, and starting your first high-speed segmented download.

---

## 1. Installation Options

### Option A: Windows Installer (Recommended)
1. Download [`XDM-Enhanced-Setup-v1.0.0-beta.6-win-x64.exe`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/XDM-Enhanced-Setup-v1.0.0-beta.6-win-x64.exe).
2. Run the setup wizard. The installer automatically includes an isolated Liberica JDK 25 runtime image.
3. Complete the installation. XDM Enhanced will automatically create start menu shortcuts and register the `xdm-app://` protocol handler.

### Option B: Windows Portable ZIP
1. Download [`XDM-Enhanced-v1.0.0-beta.6-windows-x64.zip`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/XDM-Enhanced-v1.0.0-beta.6-windows-x64.zip).
2. Extract the archive to any folder (e.g. `C:\Tools\XDM-Enhanced` or a USB drive).
3. Double-click `xdm-app.exe` to launch. All configurations and temporary download chunks remain contained inside the portable folder.

### Option C: Universal Executable JAR (Linux / macOS)
1. Ensure 64-bit Java 25 is installed (`java -version`).
2. Download [`xdm-app-1.0.0-beta.6.jar`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/xdm-app-1.0.0-beta.6.jar).
3. Launch via terminal:
   ```bash
   java -jar xdm-app-1.0.0-beta.6.jar
   ```

> [!NOTE]
> **Linux Wayland Notice:** Native Wayland rendering is supported when running on the JetBrains Runtime (`sun.awt.wl.WLToolkit`). Under standard OpenJDK runtimes, XDM Enhanced runs reliably via XWayland / X11 fallback.

---

## 2. First Launch & Interface Overview

When XDM Enhanced opens, you will see the main application window and a system tray icon:

- **Category Sidebar (Left):** Filters downloads into *All*, *Documents*, *Compressed*, *Programs*, *Music*, *Videos*, and *Torrents*.
- **Toolbar (Top):** Quick actions to add a download (`+`), pause all transfers, resume queue, open settings, or launch video tools.
- **Transfer View (Center):** Displays your active and historical transfers. You can toggle between **Table View** (compact) and **Classic Cards** in Settings > Appearance.

---

## 3. Configuring Default Download Folders

1. Open XDM Enhanced and click the **Settings** icon (⚙️) on the top toolbar.
2. Under the **General** tab:
   - **Download Folder:** Set your preferred destination folder (default: `Downloads\XDM`).
   - **Temporary Folder:** Ensure the temporary directory has sufficient free space for multi-segment chunk allocation.
3. Under the **Connection** tab:
   - **Max Connections per Download:** Adjust default segment count (recommended: 8 to 16 streams).
   - **Speed Limiter:** Enable speed limits if you share bandwidth with other network devices.

---

## 4. Starting Your First Download

### Method 1: Manual URL Entry
1. Click the **Add Download (+)** button on the top toolbar.
2. Paste the target URL into the dialog.
3. Notice that XDM Enhanced immediately performs a lightweight preflight probe, detecting the remote file name and exact size without pre-downloading any payload.
4. Click **Download Now** to begin the transfer, or **Download Later** to queue it.

### Method 2: Browser Integration (Automatic)
1. Install the companion extension for your browser ([Extension Guide](/extensions)).
2. Click any downloadable link on the web. XDM Enhanced will automatically intercept the link and display the download confirmation modal.

### Method 3: Clipboard Monitoring
1. Copy any valid HTTP/HTTPS link to your clipboard.
2. If Clipboard Monitoring is enabled in Settings, XDM Enhanced will open the confirmation dialog automatically. Passwords and sensitive clipboard text are automatically discarded.

---

## 5. Next Steps

- [Configure Browser Integration](/docs/browser-integration)
- [Learn BitTorrent & Magnet Downloading](/docs/torrent-guide)
- [Customize Skins & Wallpapers](/developer/skins)

