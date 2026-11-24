# Installation Guide

This guide covers installing and running **XDM Enhanced** across supported desktop platforms.

---

## 1. Prerequisites & System Requirements

| Package Target | Supported Platform / OS | Runtime Requirement | Package Format |
|:---|:---|:---|:---:|
| **Windows Setup (.exe)** | Windows 10 & 11 (64-bit) | None (Bundled Liberica JDK 25) | NSIS Installer |
| **Windows Portable (.zip)** | Windows 10 & 11 (64-bit) | None (Bundled Liberica JDK 25) | Standalone Zip |
| **Windows MSI Installer** | Windows 10 & 11 (x64 / ARM64) | None (Bundled Liberica JDK 25) | Windows Installer MSI |
| **macOS Native App (.dmg)** | macOS 12+ Monterey / Ventura / Sonoma / Sequoia | None (Bundled Liberica JDK 25) | Apple Silicon & Intel DMG |
| **Linux Native Packages** | Debian, Ubuntu, Fedora, RHEL, Arch, Manjaro | None (Bundled native JRE) | `.deb`, `.rpm`, `.pkg.tar.zst` |
| **Linux Portable (.tar.gz)**| Any modern Linux (x64 / ARM64) | None (Bundled native JRE) | Compressed Tarball |
| **Universal Executable (.jar)**| Any desktop OS | 64-bit Java 25+ | Runnable Fat JAR |

---

## 2. Windows Installation Options

### Option A: Modern NSIS Installer (Recommended)

1. Download the latest Windows installer:
   - **Latest Stable Release:** [`XDM-Enhanced-Setup-v1.2.0-win-x64.exe`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.2.0/XDM-Enhanced-Setup-v1.2.0-win-x64.exe)
   - **Previous Stable (v1.1.0):** [`XDM-Enhanced-Setup-v1.1.0-win-x64.exe`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/XDM-Enhanced-Setup-v1.1.0-win-x64.exe)
2. Launch the installer executable.
   *Note: If Windows SmartScreen displays an "Unknown Publisher" prompt, click **More info** and select **Run anyway** (community-signed release builds).*
3. Follow the setup wizard to choose your installation directory (default: `C:\Program Files\XDM-Enhanced`).
4. Select optional components:
   - Create Desktop Shortcut
   - Register System Startup (runs minimized to system tray)
   - Register Browser Native Messaging Protocol (`xdm-app://`)
5. Click **Finish** to complete setup and launch XDM Enhanced.

### Option B: Windows MSI Installer (Enterprise & ARM64)
- **x64 Systems:** [`xdm-1.2.0-x64.msi`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.2.0/xdm-1.2.0-x64.msi)
- **ARM64 Copilot+ PCs:** [`xdm-1.2.0-arm64.msi`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.2.0/xdm-1.2.0-arm64.msi)

### Option C: Windows Portable Bundle (Zero-Install)

1. Download [`XDM-Enhanced-v1.2.0-windows-x64.zip`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.2.0/XDM-Enhanced-v1.2.0-windows-x64.zip) or [`v1.1.0 Portable`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/XDM-Enhanced-v1.1.0-windows-x64.zip).
2. Right-click the `.zip` file and select **Extract All...** to a permanent location (e.g. `C:\Tools\XDM-Enhanced` or an external USB flash drive).
3. Open the extracted directory and double-click `xdm-app.exe`.

---

## 3. macOS Installation (Native DMG)

1. Download the disk image matching your Mac processor from GitHub Releases:
   - **Apple Silicon (M1 / M2 / M3 / M4):** [`xdm-app-1.2.0-arm64.dmg`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.2.0/xdm-app-1.2.0-arm64.dmg)
   - **Intel x64:** [`xdm-app-1.2.0-x64.dmg`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.2.0/xdm-app-1.2.0-x64.dmg)
2. Double-click the `.dmg` file to mount it.
3. Drag **XDM Enhanced** into your **Applications** folder.
4. Launch XDM Enhanced from Spotlight or Launchpad.

---

## 4. Linux Installation (Native Packages & Tarball)

Native Linux packages include a tailored JRE runtime and desktop menu integration:

- **Debian / Ubuntu / Linux Mint:**
  ```bash
  # x64:
  sudo dpkg -i xdm-app_1.2.0_amd64.deb
  # ARM64:
  sudo dpkg -i xdm-app_1.2.0_arm64.deb
  ```
- **Fedora / RHEL / openSUSE:**
  ```bash
  # x64:
  sudo rpm -i xdm-app-1.2.0-1.x86_64.rpm
  # ARM64:
  sudo rpm -i xdm-app-1.2.0-1.aarch64.rpm
  ```
- **Arch Linux / Manjaro:**
  ```bash
  sudo pacman -U xdm-app-1.2.0-1-x86_64.pkg.tar.zst
  ```
- **Portable Tarball (Zero-Install):**
  ```bash
  tar -xzf xdm-app-1.2.0-linux-x64.tar.gz
  ./xdm-app/xdm-app
  ```

---

## 5. Linux & macOS (Universal Fat JAR)

1. Verify that 64-bit Java 25 is installed on your workstation:
   ```bash
   java -version
   ```
2. Download [`xdm-app-1.2.0.jar`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.2.0/xdm-app-1.2.0.jar).
3. Open a terminal and run:
   ```bash
   java -jar xdm-app-1.2.0.jar
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

