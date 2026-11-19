# Release Notes & Version History

Detailed changelog and binary checksum records for all published releases of **XDM Enhanced**.

---

## [1.0.2] — 2026-11-20 (Latest Stable Release)

### Highlights & Key Improvements

- **Scheduler Concurrency & Shutdown Safety:**
  - Synchronized all operations on `DownloadScheduler.entries` (`synchronized(entries)`), eliminating race conditions and `ConcurrentModificationException` during concurrent download schedule updates.
  - Added explicit `DownloadScheduler.stop()` invocation during `AppContext.shutdown()` to ensure background timer tasks terminate cleanly without thread leaks or JVM hanging.
- **Atomic Schedule Persistence:**
  - Serialized scheduled download job states strictly within the entries monitor, guaranteeing atomic persistence writes and preventing partial writes or corrupted schedule state during concurrent triggers.
- **BitTorrent Corrupt Piece Recovery (`HashFailedAlert`):**
  - Added explicit handling and dispatch of `HashFailedAlert` in the BitTorrent session lifecycle, ensuring corrupted pieces are immediately detected, dropped, re-requested, and logged without silent data corruption.
- **Browser Integration Diagnostic Details:**
  - Included loopback listener health status in diagnostic copy output when custom protocol handler registration is absent or restricted, eliminating diagnostic false negatives during troubleshooting.
- **Sustained Operation & 5,000-Record Scale Verification:**
  - Validated long-duration memory stability, zero thread leaks, responsive UI rendering under 5,000 active/completed download records, smooth skin/theme switching, and resilient recovery under simulated disk-full and network-severed faults.
- **Release Protection & CI Governance:**
  - Enforced CI release tag protection guards, preventing accidental branch-push overwrites and safeguarding immutable release tags.

### Published Assets & Checksums (`v1.0.2`)

<table class="checksum-table">
  <thead>
    <tr>
      <th style="width: 35%;">Asset Filename</th>
      <th style="width: 15%;">File Size</th>
      <th>SHA-256 Checksum</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>XDM-Enhanced-Setup-v1.0.2-win-x64.exe</strong></td>
      <td>64.0 MB</td>
      <td><code>1c2b03d1425bfc9415294a57ca29b9820b8eb42451e2da8b557a913159d3c6f5</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-v1.0.2-windows-x64.zip</strong></td>
      <td>74.8 MB</td>
      <td><code>46710f6b251b28ae0f94ceff98aedbcd0310c26a4ac1322819148d824184acf0</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.0.2.jar</strong></td>
      <td>95.1 MB</td>
      <td><code>c6b3191fd20b8bf4ef13611487a38738d20340a4995d97913cf48c5e6a17aebc</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Chromium-v1.0.2.zip</strong></td>
      <td>104.0 KB</td>
      <td><code>ad21a2275fcea5214ccc943168d8ce496c81416b2f8adca4edf65f770c90f790</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Firefox-v1.0.2.xpi</strong></td>
      <td>101.2 KB</td>
      <td><code>8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac</code></td>
    </tr>
    <tr>
      <td><strong>xdm-plugin-api-1.0.2.jar</strong></td>
      <td>22.0 KB</td>
      <td><code>f62619835012abe755763c7e4bd09e77c16c0259af145566321e263aba8520b3</code></td>
    </tr>
    <tr>
      <td><strong>checksum-verifier.xdmplugin</strong></td>
      <td>6.2 KB</td>
      <td><code>c8f915c32a984515db8991537089f9324be18ab5c67421d419c5d30a83582806</code></td>
    </tr>
    <tr>
      <td><strong>ytdlp-downloader.xdmplugin</strong></td>
      <td>28.6 KB</td>
      <td><code>8a484eff37e95d961e028c80b353ff84580d275a51e166860f5cfec29a5bf189</code></td>
    </tr>
    <tr>
      <td><strong>cyber-neon.xdmskin</strong></td>
      <td>34.4 KB</td>
      <td><code>5e8293da37215dbafff6a41a88495bdefcc0faa3f942c878ada69664ec05ef0f</code></td>
    </tr>
  </tbody>
</table>

---

## [1.0.1] — 2026-11-19

### Highlights & Key Improvements

- **Active Chunk Visualization & Live Connection Details:**
  - Added real-time chunk pulse animation in `SegmentVisualizationBar` to visually distinguish active receiving stream segments from idle or completed buffers.
  - Integrated detailed connection table (`SegmentDetailTable`) displaying real-time stream bounds, byte offsets, transfer rates, status flags, and IP addresses.
- **High-Frequency Telemetry & UI Responsiveness:**
  - Transitioned transfer rate calculation to a ~10ms moving-average sampling window with 100ms batched UI coalescing, preventing EDT thread congestion under gigabit download speeds while providing fluid transfer telemetry.
- **BitTorrent Modal Workspace Bounding:**
  - Strictly constrained the Add Torrent dialog within visible monitor workspace and restricted magnet link input field dimensions, preventing oversized modals when processing long magnet links.
  - Added a dedicated "Copy Source" action button for easy magnet URI copying.
- **Context Menu Action Parity:**
  - Unified `DownloadContextMenu` across Table View, Modern Cards View, and Classic View with left-click misfire guards.
- **NSIS License RTF Formatting:**
  - Fixed control word escaping (`\widowctrl\hyphauto`) in `gpl-3.0-notice.rtf`, preventing literal RTF tokens from rendering in the installer license screen.
- **Installer Process Management:**
  - Implemented directory-verified process detection, authenticated loopback graceful shutdown (`/system/shutdown`), interactive escalation prompts, and silent `/FORCE_CLOSE` support in NSIS installer.
- **Canonical Documentation & Help Destinations:**
  - Updated all application Help menus, sidebar items, and support links to canonical fork documentation (`https://jakepanlilio2000.github.io/XDM-Enhanced/`), issue trackers, and guides with URI scheme validation and fallback handlers.

### Published Assets & Checksums (`v1.0.1`)

<table class="checksum-table">
  <thead>
    <tr>
      <th style="width: 35%;">Asset Filename</th>
      <th style="width: 15%;">File Size</th>
      <th>SHA-256 Checksum</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>XDM-Enhanced-Setup-v1.0.1-win-x64.exe</strong></td>
      <td>64.0 MB</td>
      <td><code>68be3296248f3f7704dba8cf1945ecc0dda125ed9ba898e468c9bc207b8de76c</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-v1.0.1-windows-x64.zip</strong></td>
      <td>74.3 MB</td>
      <td><code>2fc4b10348f2eb198cdc01159d6d8fc7bd14eeabd3d3d991645902487a6537f1</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.0.1.jar</strong></td>
      <td>95.1 MB</td>
      <td><code>31870f5068bfb6f49ad260fff4a010c2bb2efa3a6df1573bb2bb84399d830011</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Chromium-v1.0.1.zip</strong></td>
      <td>106.5 KB</td>
      <td><code>6db9d41834af3decb57d4c36a13d16e0eb72c8441556d667e82b6fbd3d72b1cd</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Firefox-v1.0.1.xpi</strong></td>
      <td>103.6 KB</td>
      <td><code>8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac</code></td>
    </tr>
    <tr>
      <td><strong>xdm-plugin-api-1.0.1.jar</strong></td>
      <td>22.0 KB</td>
      <td><code>5636b47925d6957554fb5a4313af0e478fa1ac764d2c5cf30f5c30cba4de4cc7</code></td>
    </tr>
    <tr>
      <td><strong>checksum-verifier.xdmplugin</strong></td>
      <td>6.2 KB</td>
      <td><code>c8f915c32a984515db8991537089f9324be18ab5c67421d419c5d30a83582806</code></td>
    </tr>
    <tr>
      <td><strong>ytdlp-downloader.xdmplugin</strong></td>
      <td>28.6 KB</td>
      <td><code>8a484eff37e95d961e028c80b353ff84580d275a51e166860f5cfec29a5bf189</code></td>
    </tr>
    <tr>
      <td><strong>cyber-neon.xdmskin</strong></td>
      <td>34.4 KB</td>
      <td><code>5e8293da37215dbafff6a41a88495bdefcc0faa3f942c878ada69664ec05ef0f</code></td>
    </tr>
  </tbody>
</table>

---

## [1.0.0] — 2026-11-11 (Stable Release)

Official Stable Release of **XDM Enhanced** — high-speed multi-connection segmented HTTP/HTTPS downloading and streaming media capture with native BitTorrent and magnet support, modern FlatLaf dynamic theming, custom skin packages, a public plugin framework, automated post-download rules, and companion browser extensions.

- Includes all features, performance improvements, bug fixes, and security hardening developed through the v1.0.0 release cycle and verified against the 15-phase audit suites.

---

## [1.0.0-beta.6] — 2026-11-10 (Pre-release)

### Highlights & Key Improvements

- **Skin Background & Modal Customization System:**
  - Extended the declarative skin engine to support custom background wallpapers and colors for main windows, sidebars, transfer tables, and dialogs via `skin.json`.
  - Added `SkinBackgroundPanel` with support for image tiling, scaling, centering, and adjustable opacity.
  - Automatic contrast scrims protect text legibility across all modal dialogs.
  - Live skin hot-reloading with regression test coverage in `FullUiSkinContractTest`.
- **User-Controlled Clipboard Monitoring & Non-Intrusive Link Preflight:**
  - Strict privacy protections: only single HTTP/HTTPS URLs under 2,048 characters are parsed. Passwords, secret tokens, and multi-line blocks are discarded immediately.
  - Non-intrusive metadata preflight: performs lightweight HTTP `HEAD` probes (with `Range: bytes=0-0` fallback) to discover file name, Content-Length, Content-Type, and server resume support.
  - **Zero-Payload Guarantee:** Absolutely no payload data or segmented chunks are downloaded prior to explicit user confirmation.
  - Clean socket cancellation when dismissing the New Download dialog.
- **Public Plugin API v2 & Task Adapter Bridge:**
  - Introduced semantic version-range compatibility matching (`[1.0.0, 2.0.0)`) for third-party plugins.
  - Decoupled Plugin Event Bus (`PluginEventBus`) supporting publish-subscribe event dispatching.
  - Implemented public task adapter bridge (`PluginDownloadBridge`) routing plugin tasks into XDM Enhanced's central download scheduler without tight coupling to internal engine classes.
- **Central Download List Integration for yt-dlp Plugin:**
  - Fixed yt-dlp plugin downloads missing from the main download table/cards by routing tasks through the public task adapter bridge with stable IDs and `CAT_VIDEOS` categorization.
  - Real-time progress, speed, ETA, and status synchronization.
  - Safe subprocess execution via `ProcessBuilder` with explicit array argument passing to eliminate shell injection vulnerabilities.
- **Browser Connection Self-Diagnostics & Troubleshooting:**
  - Added self-diagnostics panel in extension options and error pages displaying live connection state, detected issues (`host_missing`, `permission_denied`, `app_not_running`), and a "Check Connection" retry button.
  - Safe desktop repair utility in Settings > Browser: checks and repairs Windows native messaging manifest paths and protocol handler registrations (`xdm-app://`).
  - Comprehensive technical troubleshooting guide: [Browser Troubleshooting Guide](/troubleshooting).

### Published Assets & Checksums (`v1.0.0-beta.6`)

<table class="checksum-table">
  <thead>
    <tr>
      <th style="width: 35%;">Asset Filename</th>
      <th style="width: 15%;">File Size</th>
      <th>SHA-256 Checksum</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>XDM-Enhanced-Setup-v1.0.0-beta.6-win-x64.exe</strong></td>
      <td>64.8 MB</td>
      <td><code>da95a6b3beda92a29357544dc448211f01d6c3ef48ca68182a0cd130a47d3636</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-v1.0.0-beta.6-windows-x64.zip</strong></td>
      <td>75.6 MB</td>
      <td><code>5bbb8edfd6d4d6a32540b309271c6b1cceefa0569f74f66c7bd2ef542876201d</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.0.0-beta.6.jar</strong></td>
      <td>3.8 MB</td>
      <td><code>5f14df837cab6ada72b3f8fad437ee73183f64d21238e2f1bf03b8ed80c26c9c</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Chromium-v1.0.0-beta.6.zip</strong></td>
      <td>106 KB</td>
      <td><code>0ee9bb3edf49e11d126eca200ad67b2938e2d33b306a493bc31821ad0977e981</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Firefox-v1.0.0-beta.6.xpi</strong></td>
      <td>103 KB</td>
      <td><code>14dfc9637085267dc098c56d49b882e69bdb5cca22eaa9cf75b6d4471951feaa</code></td>
    </tr>
    <tr>
      <td><strong>xdm-plugin-api-1.0.0-beta.6.jar</strong></td>
      <td>22 KB</td>
      <td><code>ddec81692aa22354ff3f5df7c70bdd290c922e89d9077bf0c00ccff40d273db4</code></td>
    </tr>
    <tr>
      <td><strong>ytdlp-downloader.xdmplugin</strong></td>
      <td>28.4 KB</td>
      <td><code>0b6524b86d835c03948d0260598d31e27049e3292f6f67b569b369e95e5b0a19</code></td>
    </tr>
    <tr>
      <td><strong>checksum-verifier.xdmplugin</strong></td>
      <td>6.2 KB</td>
      <td><code>152766aa1ee2fc491cb06a7c4f36ee8e3fea95a6ffcbc856e8d44163da5b5a43</code></td>
    </tr>
    <tr>
      <td><strong>cyber-neon.xdmskin</strong></td>
      <td>34.4 KB</td>
      <td><code>74e80ededf6d7f41f29b5cfafa79a06bef8b53fc20b077523122ffaeaff2f213</code></td>
    </tr>
  </tbody>
</table>

---

## [1.0.0-beta.5] — 2026-11-10

- **Extensible Skin Engine & Custom Skin Packages (`.xdmskin`):**
  - Integrated skin engine supporting built-in skins (**XDM Enhanced** FlatLaf and **Original XDM** classic theme) alongside portable `.xdmskin` ZIP archives.
  - Custom window backgrounds, toolbar icons, category SVGs, buttons, fonts, border radii, and accent overrides.
  - Security-hardened archive validation defending against Zip Slip path traversal and prohibiting executable payloads.
  - Dedicated Skin Management interface in Settings > Appearance with preview cards, install/export, and live hot-reloading.
- **Core Plugin Framework & Public Developer API (`xdm-plugin-api`):**
  - Standalone Maven submodule defining the decoupled public SPI contract.
  - Dynamic discovery and loading of `.xdmplugin` archives.
  - Isolated classloader hierarchies preventing plugin conflicts.

---

## [1.0.0-beta.4] — 2026-11-09

- **BitTorrent 2.0 Engine Upgrades:**
  - Upgraded `libtorrent4j` native bindings for enhanced multi-peer swarm stability.
  - Improved magnet metadata resolution over DHT/PEX networks.
  - Added piece availability visualizer in the torrent details dialog.
- **Memory & Socket Optimizations:**
  - Reduced memory footprint during multi-segment transfers on gigabit connections.
  - Improved HTTP connection pooling and keep-alive socket reuse.

---

## [1.0.0-beta.3] — 2026-11-09

- **Browser Extension Improvements:**
  - Floating video sniffer button with Shadow DOM CSS isolation.
  - Multi-stream quality selector (4K, 1080p, 720p, 480p).
  - Improved loopback handshake reliability on `127.0.0.1:8597`.

---

## [1.0.0-beta.2] — 2026-11-09

- **Modern FlatLaf UI Overhaul:**
  - Introduced modern FlatLaf light and dark theme palettes.
  - Category sidebar navigation with badge counts for active transfers.
  - Compact Table View and Classic Cards view modes.

---

## [1.0.0-beta.1] — 2026-11-08

- **Initial Community Fork Release:**
  - Forked independently from Xtreme Download Manager by subhra74.
  - Modularized project structure into Maven submodules.
  - Initial Liberica JDK 25 bundling and modern NSIS packaging pipeline.

