# Release Notes & Version History

Detailed changelog and binary checksum records for all published releases of **XDM Enhanced**.

---

## [1.0.0-beta.6] — 2026-11-10 (Latest Pre-release)

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

