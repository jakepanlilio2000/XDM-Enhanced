# Downloads & Release Packages

Download verified releases, standalone archives, browser companion extensions, and developer packages for **XDM Enhanced**.

<div class="tip custom-block" style="margin-top: 1.5rem; margin-bottom: 2rem;">
  <div style="display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 1rem;">
    <div>
      <span class="badge-pill badge-verified">Active Channel: Current Stable</span>
      <h3 style="margin: 0.25rem 0;">Version: v1.0.2</h3>
      <p style="margin: 0; color: var(--vp-c-text-2); font-size: 0.9rem;">
        Published on <strong>November 20, 2026</strong>. Built with Liberica JDK 25 on Windows x64.
      </p>
    </div>
    <div>
      <a href="/releases" class="btn-secondary" style="font-size: 0.85rem;">View Release Notes</a>
    </div>
  </div>
</div>

> [!NOTE]
> **Channel Notice:** XDM Enhanced **v1.0.2** is the current active stable release. It incorporates all features, performance improvements, and security hardening from the v1.0.0 release cycle and verified post-stable fixes. Previous release packages remain accessible in the [Releases Archive](/releases).

---

## 1. Desktop Application Packages

<div class="feature-grid">
  <div class="download-card primary-card">
    <div>
      <span class="badge-pill badge-verified">Recommended for Windows</span>
      <h3 style="margin-top: 0.75rem;">Windows Setup (x64)</h3>
      <p>Modern NSIS installer with bundled Liberica JDK 25 runtime, desktop shortcut, system tray autostart, and protocol registration.</p>
      <ul style="font-size: 0.85rem; color: var(--vp-c-text-2); padding-left: 1.25rem; margin-top: 0.5rem;">
        <li>Format: Modern NSIS <code>.exe</code></li>
        <li>File Size: <strong>64.0 MB</strong></li>
        <li>System: Windows 10 & 11 (64-bit)</li>
      </ul>
    </div>
    <div style="margin-top: 1.25rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.2/XDM-Enhanced-Setup-v1.0.2-win-x64.exe" class="btn-primary" style="width: 100%; box-sizing: border-box;">Download Setup .EXE</a>
    </div>
  </div>

  <div class="download-card">
    <div>
      <span class="badge-pill badge-stable">Zero Installation</span>
      <h3 style="margin-top: 0.75rem;">Windows Portable Bundle</h3>
      <p>Standalone zip archive containing the application and native runtime. Ideal for portable USB storage and non-admin enterprise environments.</p>
      <ul style="font-size: 0.85rem; color: var(--vp-c-text-2); padding-left: 1.25rem; margin-top: 0.5rem;">
        <li>Format: Compressed <code>.zip</code></li>
        <li>File Size: <strong>74.8 MB</strong></li>
        <li>System: Windows 10 & 11 (64-bit)</li>
      </ul>
    </div>
    <div style="margin-top: 1.25rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.2/XDM-Enhanced-v1.0.2-windows-x64.zip" class="btn-secondary" style="width: 100%; box-sizing: border-box;">Download Portable .ZIP</a>
    </div>
  </div>

  <div class="download-card">
    <div>
      <span class="badge-pill badge-stable">Cross-Platform</span>
      <h3 style="margin-top: 0.75rem;">Universal Executable JAR</h3>
      <p>Standard runnable JAR suitable for Linux, macOS, and BSD systems with an existing 64-bit Java 25 runtime environment.</p>
      <ul style="font-size: 0.85rem; color: var(--vp-c-text-2); padding-left: 1.25rem; margin-top: 0.5rem;">
        <li>Format: Java Executable <code>.jar</code></li>
        <li>File Size: <strong>95.1 MB</strong></li>
        <li>Requires: 64-bit JRE / JDK 25</li>
      </ul>
    </div>
    <div style="margin-top: 1.25rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.2/xdm-app-1.0.2.jar" class="btn-secondary" style="width: 100%; box-sizing: border-box;">Download Standalone .JAR</a>
    </div>
  </div>
</div>

---

## 2. Browser Companion Extensions

Install the companion browser extension to enable seamless one-click link grabbing and the floating HTML5 video sniffer.

<div class="feature-grid">
  <div class="download-card">
    <div>
      <span class="badge-pill badge-verified">Chromium Browsers</span>
      <h3 style="margin-top: 0.75rem;">Chromium MV3 Extension</h3>
      <p>Compatible with Google Chrome, Microsoft Edge, Brave, Opera, Vivaldi, and other Chromium-based browsers.</p>
    </div>
    <div style="margin-top: 1rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.2/XDM-Enhanced-Extension-Chromium-v1.0.2.zip" class="btn-secondary" style="width: 100%; box-sizing: border-box;">Download Extension (.ZIP)</a>
      <div style="margin-top: 0.5rem; text-align: center;">
        <a href="/extensions" style="font-size: 0.85rem; color: var(--vp-c-brand-1);">Installation Guide &rarr;</a>
      </div>
    </div>
  </div>

  <div class="download-card">
    <div>
      <span class="badge-pill badge-verified">Mozilla Firefox</span>
      <h3 style="margin-top: 0.75rem;">Firefox Signed Add-on</h3>
      <p>Native Firefox add-on package (Manifest V2) supporting Firefox 109+, LibreWolf, Waterfox, and Firefox ESR.</p>
    </div>
    <div style="margin-top: 1rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.2/XDM-Enhanced-Extension-Firefox-v1.0.2.xpi" class="btn-secondary" style="width: 100%; box-sizing: border-box;">Download Add-on (.XPI)</a>
      <div style="margin-top: 0.5rem; text-align: center;">
        <a href="/extensions" style="font-size: 0.85rem; color: var(--vp-c-brand-1);">Installation Guide &rarr;</a>
      </div>
    </div>
  </div>
</div>

---

## 3. Developer & Add-on Packages

| Package Name | File Type | Size | Description | Download |
|:---|:---:|:---:|:---|:---:|
| **Plugin API v2** | `.jar` | 22.0 KB | Public Java interface for building decoupled third-party plugins. | [Download](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.2/xdm-plugin-api-1.0.2.jar) |
| **Checksum Verifier** | `.xdmplugin` | 6.2 KB | Sample plugin providing automated MD5/SHA-256 integrity verification. | [Download](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.2/checksum-verifier.xdmplugin) |
| **yt-dlp Downloader** | `.xdmplugin` | 28.6 KB | Sample plugin integrating yt-dlp media extraction with the central scheduler. | [Download](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.2/ytdlp-downloader.xdmplugin) |
| **Cyber Neon Skin** | `.xdmskin` | 34.4 KB | High-contrast neon cyan theme package demonstrating the skin engine. | [Download](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.2/cyber-neon.xdmskin) |

---

## 4. SHA-256 Checksums (`v1.0.2`)

Verify downloaded binaries against the cryptographic hashes published in [SHA256SUMS.txt](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.2/SHA256SUMS.txt):

<table class="checksum-table">
  <thead>
    <tr>
      <th style="width: 40%;">File Name</th>
      <th>SHA-256 Checksum</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>XDM-Enhanced-Setup-v1.0.2-win-x64.exe</strong></td>
      <td><code>1c2b03d1425bfc9415294a57ca29b9820b8eb42451e2da8b557a913159d3c6f5</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-v1.0.2-windows-x64.zip</strong></td>
      <td><code>46710f6b251b28ae0f94ceff98aedbcd0310c26a4ac1322819148d824184acf0</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.0.2.jar</strong></td>
      <td><code>c6b3191fd20b8bf4ef13611487a38738d20340a4995d97913cf48c5e6a17aebc</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Chromium-v1.0.2.zip</strong></td>
      <td><code>ad21a2275fcea5214ccc943168d8ce496c81416b2f8adca4edf65f770c90f790</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Firefox-v1.0.2.xpi</strong></td>
      <td><code>8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac</code></td>
    </tr>
    <tr>
      <td><strong>xdm-plugin-api-1.0.2.jar</strong></td>
      <td><code>f62619835012abe755763c7e4bd09e77c16c0259af145566321e263aba8520b3</code></td>
    </tr>
    <tr>
      <td><strong>ytdlp-downloader.xdmplugin</strong></td>
      <td><code>8a484eff37e95d961e028c80b353ff84580d275a51e166860f5cfec29a5bf189</code></td>
    </tr>
    <tr>
      <td><strong>checksum-verifier.xdmplugin</strong></td>
      <td><code>c8f915c32a984515db8991537089f9324be18ab5c67421d419c5d30a83582806</code></td>
    </tr>
    <tr>
      <td><strong>cyber-neon.xdmskin</strong></td>
      <td><code>5e8293da37215dbafff6a41a88495bdefcc0faa3f942c878ada69664ec05ef0f</code></td>
    </tr>
  </tbody>
</table>

### Verifying Checksums Locally

```powershell
# On Windows (PowerShell)
Get-FileHash -Algorithm SHA256 .\XDM-Enhanced-Setup-v1.0.2-win-x64.exe

# On Linux / macOS
sha256sum XDM-Enhanced-Setup-v1.0.2-win-x64.exe
```

---

## 5. Previous Releases & Source Archives

All historical builds from `v1.0.0-beta.1` through `v1.0.1` are maintained on the [GitHub Releases Repository](https://github.com/jakepanlilio2000/XDM-Enhanced/releases). If GitHub API rate limits occur, downloads remain directly accessible via GitHub releases mirror links.
