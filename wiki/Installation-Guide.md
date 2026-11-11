# Installation Guide

This guide covers installing and running **XDM Enhanced** across supported desktop platforms.

---

## 1. Prerequisites & System Requirements

| Package Target | Minimum Operating System | Java Runtime | Required Free Disk Space |
|:---|:---|:---|:---:|
| **Windows Setup (.exe)** | Windows 10 or 11 (64-bit) | None (Bundled Liberica JDK 25) | ~250 MB |
| **Windows Portable (.zip)** | Windows 10 or 11 (64-bit) | None (Bundled Liberica JDK 25) | ~280 MB |
| **Universal Executable (.jar)** | Linux, macOS 12+, BSD | 64-bit Java 25 | ~50 MB |

---

## 2. Windows Installation Options

### Option A: Modern NSIS Installer (Recommended)

1. Download [`XDM-Enhanced-Setup-v1.0.0-beta.6-win-x64.exe`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/XDM-Enhanced-Setup-v1.0.0-beta.6-win-x64.exe) from GitHub Releases.
2. Launch the installer executable.
   *Note: If Windows SmartScreen displays an "Unknown Publisher" prompt, click **More info** and select **Run anyway** (pre-release binaries are community builds).*
3. Follow the setup wizard to choose your installation directory (default: `C:\Program Files\XDM-Enhanced`).
4. Select optional components:
   - Create Desktop Shortcut
   - Register System Startup (runs minimized to system tray)
   - Register Browser Native Messaging Protocol (`xdm-app://`)
5. Click **Finish** to complete setup and launch XDM Enhanced.

**Expected Result:**
The XDM Enhanced main window opens, and the project squircle icon appears in the Windows system notification area (system tray).

---

### Option B: Windows Portable Bundle (Zero-Install)

1. Download [`XDM-Enhanced-v1.0.0-beta.6-windows-x64.zip`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/XDM-Enhanced-v1.0.0-beta.6-windows-x64.zip).
2. Right-click the `.zip` file and select **Extract All...** to a permanent location (e.g. `C:\Tools\XDM-Enhanced` or an external USB flash drive).
3. Open the extracted directory and double-click `xdm-app.exe`.

**Expected Result:**
XDM Enhanced runs in standalone portable mode. Application configuration files and temporary segment caches are stored locally inside the portable directory without writing to the Windows Registry.

---

## 3. Linux & macOS Installation (Universal JAR)

1. Verify that 64-bit Java 25 is installed on your workstation:
   ```bash
   java -version
   ```
2. Download [`xdm-app-1.0.0-beta.6.jar`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/xdm-app-1.0.0-beta.6.jar).
3. Open a terminal and run:
   ```bash
   java -jar xdm-app-1.0.0-beta.6.jar
   ```

**Display Server & Toolkit Limitations on Linux:**
- **Native Wayland:** Native Wayland toolkit rendering (`WLToolkit`) requires running on the JetBrains Runtime (`sun.awt.wl.WLToolkit`). Under standard OpenJDK runtimes, XDM Enhanced automatically falls back to XWayland / X11 rendering.
- **File Chooser Override:** The "Built-in file chooser" toggle in Advanced Settings is functional only on Linux (Windows and macOS always use native operating system dialogs).

**Expected Result:**
The FlatLaf desktop GUI launches with native OS window decorations.

---

## 4. Common Failure States & Recovery

| Failure Symptom | Probable Cause | Recovery Action |
|:---|:---|:---|
| **Installer fails with "Access Denied"** | Missing admin privileges | Right-click the installer and choose **Run as administrator**. |
| **Universal JAR fails to launch** | Java version is older than Java 25 | Install 64-bit Liberica JDK 25 or OpenJDK 25. |
| **App launches but immediately exits** | Another XDM instance is already running | Check task manager for existing `xdm-app.exe` processes. |

---

## Related Articles & Next Steps

* [[First Download Guide|First-Download-Guide]]: Add and accelerate your first download.
* [[Browser Extension Setup|Browser-Extension-Setup]]: Enable browser integration.
* [[Troubleshooting by Symptom|Troubleshooting-by-Symptom]]: Detailed diagnostic resolutions.
* Found a bug? [Report an installation issue](https://github.com/jakepanlilio2000/XDM-Enhanced/issues).

