# Downloads & Release Packages

Download verified releases, native installers, standalone archives, browser companion extensions, and developer packages for **XDM Enhanced**.

<div class="tip custom-block" style="margin-top: 1.5rem; margin-bottom: 2rem;">
  <div style="display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 1rem;">
    <div>
      <span class="badge-pill badge-verified">Active Release: Stable</span>
      <h3 style="margin: 0.25rem 0;">Version: v1.1.0 (Plugin SDK v2 &amp; Upstream Sync)</h3>
      <p style="margin: 0; color: var(--vp-c-text-2); font-size: 0.9rem;">
        Published on <strong>November 24, 2026</strong>. Graduates Modular Plugin SDK v2, multi-platform installers, HLS playlist capture &amp; muxer fixes, and networking/accessibility hardening.
      </p>
    </div>
    <div style="display: flex; gap: 0.5rem; align-items: center;">
      <a href="/releases" class="btn-secondary" style="font-size: 0.85rem;">View Release Notes</a>
    </div>
  </div>
</div>

> [!NOTE]
> **Release Channels:**
> - **Stable Channel (`v1.1.0`):** Recommended for all users. Graduates Modular Plugin SDK v2, macOS DMGs, Linux native packages, Windows MSI/NSIS/Portable builds, and upstream media/network bugfixes.
> - **Previous Stable (`v1.0.2`):** Remains available on [GitHub Releases](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.0.2) for historical fallback.

---

## 1. Desktop Application Packages (`v1.1.0`)

<div class="feature-grid">
  <div class="download-card primary-card">
    <div>
      <span class="badge-pill badge-verified">Recommended for Windows</span>
      <h3 style="margin-top: 0.75rem;">Windows Setup (x64)</h3>
      <p>Modern NSIS installer with bundled Liberica JDK 25 runtime, desktop shortcut, system tray autostart, and native browser protocol registration.</p>
      <ul style="font-size: 0.85rem; color: var(--vp-c-text-2); padding-left: 1.25rem; margin-top: 0.5rem;">
        <li>Format: Modern NSIS <code>.exe</code></li>
        <li>System: Windows 10 &amp; 11 (64-bit)</li>
      </ul>
    </div>
    <div style="margin-top: 1.25rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/XDM-Enhanced-Setup-v1.1.0-win-x64.exe" class="btn-primary" style="width: 100%; box-sizing: border-box;">Download Setup .EXE</a>
    </div>
  </div>

  <div class="download-card">
    <div>
      <span class="badge-pill badge-stable">Zero Installation</span>
      <h3 style="margin-top: 0.75rem;">Windows Portable Bundle</h3>
      <p>Standalone zip archive containing the application and native runtime. Ideal for portable USB storage and non-admin enterprise environments.</p>
      <ul style="font-size: 0.85rem; color: var(--vp-c-text-2); padding-left: 1.25rem; margin-top: 0.5rem;">
        <li>Format: Compressed <code>.zip</code></li>
        <li>System: Windows 10 &amp; 11 (64-bit)</li>
      </ul>
    </div>
    <div style="margin-top: 1.25rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/XDM-Enhanced-v1.1.0-windows-x64.zip" class="btn-secondary" style="width: 100%; box-sizing: border-box;">Download Portable .ZIP</a>
    </div>
  </div>

  <div class="download-card">
    <div>
      <span class="badge-pill badge-stable">Windows MSI &amp; ARM64</span>
      <h3 style="margin-top: 0.75rem;">Windows MSI Installers</h3>
      <p>Standard Windows Installer packages for managed IT enterprise deployments, Copilot+ ARM64 PCs, and silent distribution.</p>
      <ul style="font-size: 0.85rem; color: var(--vp-c-text-2); padding-left: 1.25rem; margin-top: 0.5rem;">
        <li><a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-1.1.0-x64.msi">Download x64 MSI</a></li>
        <li><a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-1.1.0-arm64.msi">Download ARM64 MSI</a></li>
      </ul>
    </div>
    <div style="margin-top: 1.25rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-1.1.0-x64.msi" class="btn-secondary" style="width: 100%; box-sizing: border-box;">Download x64 MSI</a>
    </div>
  </div>

  <div class="download-card">
    <div>
      <span class="badge-pill badge-verified">Apple macOS</span>
      <h3 style="margin-top: 0.75rem;">macOS Disk Image (DMG)</h3>
      <p>Native macOS application bundle with embedded runtime and system integration for Apple Silicon (M-series) and Intel Macs.</p>
      <ul style="font-size: 0.85rem; color: var(--vp-c-text-2); padding-left: 1.25rem; margin-top: 0.5rem;">
        <li><a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-app-1.1.0-arm64.dmg">Apple Silicon DMG (M1-M4)</a></li>
        <li><a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-app-1.1.0-x64.dmg">Intel x64 DMG</a></li>
      </ul>
    </div>
    <div style="margin-top: 1.25rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-app-1.1.0-arm64.dmg" class="btn-secondary" style="width: 100%; box-sizing: border-box;">Download Apple Silicon DMG</a>
    </div>
  </div>

  <div class="download-card">
    <div>
      <span class="badge-pill badge-verified">Linux Distributions</span>
      <h3 style="margin-top: 0.75rem;">Linux Native Packages</h3>
      <p>Tailored packages with desktop integration, start menu icons, and bundled JRE for all major Linux distributions.</p>
      <ul style="font-size: 0.85rem; color: var(--vp-c-text-2); padding-left: 1.25rem; margin-top: 0.5rem;">
        <li>Debian / Ubuntu: <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-app_1.1.0_amd64.deb">amd64</a> | <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-app_1.1.0_arm64.deb">arm64</a></li>
        <li>Fedora / RHEL: <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-app-1.1.0-1.x86_64.rpm">x86_64</a> | <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-app-1.1.0-1.aarch64.rpm">aarch64</a></li>
        <li>Arch / Manjaro: <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-app-1.1.0-1-x86_64.pkg.tar.zst">x86_64</a> | <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-app-1.1.0-1-aarch64.pkg.tar.zst">aarch64</a></li>
        <li>Portable Tarball: <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-app-1.1.0-linux-x64.tar.gz">x64</a> | <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-app-1.1.0-linux-arm64.tar.gz">arm64</a></li>
      </ul>
    </div>
    <div style="margin-top: 1.25rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-app_1.1.0_amd64.deb" class="btn-secondary" style="width: 100%; box-sizing: border-box;">Download Debian/Ubuntu .DEB</a>
    </div>
  </div>

  <div class="download-card">
    <div>
      <span class="badge-pill badge-stable">Cross-Platform</span>
      <h3 style="margin-top: 0.75rem;">Universal Executable JAR</h3>
      <p>Standard runnable fat JAR suitable for any desktop system with an existing 64-bit Java 25 runtime environment.</p>
      <ul style="font-size: 0.85rem; color: var(--vp-c-text-2); padding-left: 1.25rem; margin-top: 0.5rem;">
        <li>Format: Java Executable <code>.jar</code></li>
        <li>Requires: 64-bit JRE / JDK 25+</li>
      </ul>
    </div>
    <div style="margin-top: 1.25rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-app-1.1.0.jar" class="btn-secondary" style="width: 100%; box-sizing: border-box;">Download Standalone .JAR</a>
    </div>
  </div>
</div>

---

## 2. Browser Companion Extensions

Install the companion browser extension to enable automatic link capturing and the floating HTML5 media sniffer.

<div class="feature-grid">
  <div class="download-card">
    <div>
      <span class="badge-pill badge-verified">Chromium Browsers</span>
      <h3 style="margin-top: 0.75rem;">Chromium MV3 Extension</h3>
      <p>Compatible with Google Chrome, Microsoft Edge, Brave, Opera, Vivaldi, and other Chromium-based browsers.</p>
    </div>
    <div style="margin-top: 1rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/XDM-Enhanced-Extension-Chromium-v1.1.0.zip" class="btn-secondary" style="width: 100%; box-sizing: border-box;">Download Extension (.ZIP)</a>
      <div style="margin-top: 0.5rem; text-align: center;">
        <a href="/extensions" style="font-size: 0.85rem; color: var(--vp-c-brand-1);">Installation Guide &rarr;</a>
      </div>
    </div>
  </div>

  <div class="download-card">
    <div>
      <span class="badge-pill badge-verified">Mozilla Firefox</span>
      <h3 style="margin-top: 0.75rem;">Firefox Signed Add-on</h3>
      <p>Native Firefox add-on package (Manifest V2) supporting Firefox 109+, LibreWolf, Floorp, Waterfox, and Firefox ESR.</p>
    </div>
    <div style="margin-top: 1rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/XDM-Enhanced-Extension-Firefox-v1.1.0.xpi" class="btn-secondary" style="width: 100%; box-sizing: border-box;">Download Add-on (.XPI)</a>
      <div style="margin-top: 0.5rem; text-align: center;">
        <a href="/extensions" style="font-size: 0.85rem; color: var(--vp-c-brand-1);">Installation Guide &rarr;</a>
      </div>
    </div>
  </div>
</div>

---

## 3. Modular Plugin SDK v2 Packages &amp; Skins

| Package Name | File Type | Description | Download |
|:---|:---:|:---|:---:|
| **Plugin API v2 (`xdm-plugin-api`)** | `.jar` | Public Java SPI specification 2.0.0 (`xdm-plugin-api:1.1.0`) for building decoupled plugins. | [Download](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/xdm-plugin-api-1.1.0.jar) |
| **yt-dlp Downloader Plugin** | `.xdmplugin` | Official download provider integrating authorized yt-dlp media extraction. | [Download](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/ytdlp-downloader.xdmplugin) |
| **Proxy Profile Provider** | `.xdmplugin` | Network provider demonstrating scoped SOCKS5/HTTP proxy profile management. | [Download](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/proxy-provider.xdmplugin) |
| **VPN Telemetry Client** | `.xdmplugin` | Non-privileged VPN client status monitoring and pause policy integration. | [Download](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/vpn-status.xdmplugin) |
| **UI &amp; Alerts Notification Plugin** | `.xdmplugin` | Demonstrates UI tabs, toast notifications, status pills, and theme observation. | [Download](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/ui-notification.xdmplugin) |
| **Reference SDK v2 Plugin** | `.xdmplugin` | Complete sample plugin demonstrating typed EventTopics and lifecycle methods. | [Download](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/v2-sample-plugin.xdmplugin) |
| **Checksum Verifier Plugin** | `.xdmplugin` | Automated cryptographic post-download verification (MD5, SHA-1, SHA-256). | [Download](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/checksum-verifier.xdmplugin) |
| **Cyber Neon Skin** | `.xdmskin` | Verified custom FlatLaf skin package with neon cyan accents. | [Download](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/cyber-neon.xdmskin) |

---

## 4. SHA-256 Checksums (`v1.1.0`)

Verify downloaded binaries against the cryptographic hashes published in [`SHA256SUMS.txt`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/SHA256SUMS.txt) on the `v1.1.0` GitHub Release.

### Verifying Checksums Locally

```powershell
# On Windows (PowerShell)
Get-FileHash -Algorithm SHA256 .\XDM-Enhanced-Setup-v1.1.0-win-x64.exe

# On Linux / macOS (Terminal)
sha256sum XDM-Enhanced-Setup-v1.1.0-win-x64.exe
```

---

## 5. Previous Stable Channel (`v1.0.2`)

For users referencing the prior `v1.0.2` baseline:

| Package | Format | File Size | Download Link |
|:---|:---:|:---:|:---:|
| **Windows Setup** | `.exe` | 64.0 MB | [Download Setup .EXE](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.2/XDM-Enhanced-Setup-v1.0.2-win-x64.exe) |
| **Windows Portable** | `.zip` | 74.8 MB | [Download Portable .ZIP](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.2/XDM-Enhanced-v1.0.2-windows-x64.zip) |
| **Universal Fat JAR** | `.jar` | 95.1 MB | [Download App .JAR](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.2/xdm-app-1.0.2.jar) |
| **Chromium Extension** | `.zip` | 104 KB | [Download Extension](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.2/XDM-Enhanced-Extension-Chromium-v1.0.2.zip) |
| **Firefox Add-on** | `.xpi` | 101 KB | [Download Add-on](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.2/XDM-Enhanced-Extension-Firefox-v1.0.2.xpi) |

---

## 6. Previous Releases &amp; Source Archives

All historical builds from `v1.0.0-beta.1` through `v1.1.0-beta.1` are maintained on the [GitHub Releases Repository](https://github.com/jakepanlilio2000/XDM-Enhanced/releases).
