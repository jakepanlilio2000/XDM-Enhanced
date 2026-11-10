# Features & Architecture Breakdown

XDM Enhanced is engineered from the ground up for maximum download throughput, deep desktop customization, and decoupled extensibility. Every feature documented below has been verified against runtime source and automated regression suites.

---

## 1. High-Speed Segmented Download Engine

```
[ Remote Web Server ]
         │
         ├── Connection 1 ──► [ Segment 01 ] ──┐
         ├── Connection 2 ──► [ Segment 02 ] ──┼──► [ Target File on Disk ]
         ├── Connection 3 ──► [ Segment 03 ] ──┤    (Direct Block Assembly)
         └── Connection 4 ──► [ Segment 04 ] ──┘
```

- **Dynamic Connection Splitting:** Dynamically splits incoming HTTP, HTTPS, and FTP streams into up to 32 parallel connections per download, saturating available bandwidth without saturating thread pools.
- **Direct Block Assembly:** Allocates target file segments directly on storage without a secondary post-download copy pass, eliminating double-disk write latency.
- **Resilient Resume Capability:** Automatically recovers interrupted transfers caused by broken sockets, router resets, or system restarts.
- **Link Preflight & Refresh:** Discovers file attributes (size, mime type, server resume support) via non-intrusive `HEAD` / `Range: bytes=0-0` probes before initiating large transfers. If a download URL expires, the Link Refresh feature re-authenticates without restarting from scratch.
- **Bandwidth Speed Throttling:** Global and per-task transfer rate limits to maintain network responsiveness for other applications.

---

## 2. BitTorrent 2.0 & Magnet Link Engine

- **High-Performance Backend:** Powered by `libtorrent4j` wrapping native `libtorrent` (Rasterbar), supporting both BitTorrent v1 and v2 specifications.
- **Background Magnet Resolution:** Instantly resolves `magnet:?` metadata in the background, populating the torrent file manifest before downloading begins.
- **Hierarchical File Selection:** Interactive file selector tree allowing individual files to be excluded or assigned download priorities (*Do Not Download*, *Normal*, *High*, *Maximum*).
- **Decentralized Discovery:** Built-in Distributed Hash Table (DHT), Peer Exchange (PEX), and Local Service Discovery (LSD), with strict adherence to BEP 27 private tracker privacy flags.
- **Piece Map & Diagnostics:** Live visual inspection of peer IPs, encryption status, connected seeds, and piece hash rechecking.

---

## 3. Companion Browser Integration & Media Sniffer

- **Dual Extension Support:**
  - **Chromium-based browsers** (Chrome, Edge, Brave, Opera, Vivaldi): Modern Manifest V3 extension.
  - **Mozilla Firefox:** Native Manifest V2 extension signed and installable via `.xpi`.
- **Local-Only Loopback Bridge:** Secure HTTP communication on `http://127.0.0.1:8597` with strict origin checking, localhost binding, and DNS rebinding defense (HTTP 403 Forbidden on unrecognized hosts).
- **Floating Video Sniffer:** Non-intrusive floating grab button injected into HTML5 video players using Shadow DOM style isolation to prevent webpage CSS interference.
- **Quality & Format Inspector:** Detects video streams (MP4, WebM, HLS/DASH) across resolutions from 4K down to 480p, indicating muxed audio tracks and estimated file sizes.
- **Self-Diagnostics & Desktop Repair:** Browser popup includes a live connectivity health checker and links to the desktop self-repair utility (`xdm-app://`).

---

## 4. Full-App Custom Skin Engine

- **Dual Built-in Presets:**
  - **XDM Enhanced:** Modern FlatLaf theme with responsive light/dark palettes, rounded buttons, and emerald/purple accent swatches.
  - **Original XDM:** Authentic classic look replicating original XDM visual aesthetics for legacy fans.
- **Declarative Skin Packages (`.xdmskin`):**
  - Portable ZIP archives containing `skin.json`, custom toolbar icons, category SVGs, and window backgrounds.
  - Hardened with Zip Slip path-traversal protection and strict security limits preventing executable payloads.
- **Background Wallpaper Customization:**
  - Custom background images and colors for main windows, sidebars, transfer tables, and dialogs.
  - Supports image scaling, tiling, centering, and adjustable opacity overlays.
- **Modal Legibility Preservation:** Dialog backdrops automatically maintain contrast and legibility regardless of chosen wallpaper.
- **Live Hot-Reloading:** Switch skins on the fly without restarting the application.

---

## 5. Decoupled Java Plugin SPI v2

- **Independent Compilation:** Plugins compile against `xdm-plugin-api.jar` with zero dependencies on internal desktop classes (`xdm.app.*`).
- **Semantic Version Range Matching:** Plugin manifests declare compatibility ranges (e.g. `[1.0.0, 2.0.0)`), guaranteeing that minor host updates never break installed plugins.
- **Decoupled Event Bus (`PluginEventBus`):** Clean publish-subscribe lifecycle and transfer progress events.
- **Central Download List Bridge (`PluginDownloadBridge`):** Routes third-party downloads (e.g. yt-dlp) into the central task scheduler, displaying real-time progress, speed, ETA, and history alongside standard downloads.
- **Hardened Subprocess Invocation:** Executes external helper binaries using `ProcessBuilder` with strict array argument passing to eliminate shell injection vulnerabilities.

---

## 6. Privacy & Non-Intrusive Link Preflight

- **User-Controlled Clipboard Filter:** Only parses single HTTP/HTTPS URLs under 2,048 characters. Passwords, auth tokens, multi-line blocks, and unrelated clipboard text are immediately discarded without logging.
- **Zero Pre-Download Payload Guarantee:** Preflight requests solely retrieve HTTP headers; absolutely no file data is downloaded before explicit user confirmation.
- **Instant Cancellation:** Dismissing the New Download dialog cleanly aborts in-flight network probes.

---

## Current Beta Limitations & Known Boundaries

As XDM Enhanced is currently in active development on the **Pre-release Channel (v1.0.0-beta.6)**, users should be aware of the following known operating parameters:

1. **yt-dlp Binary Path:** The `ytdlp-downloader.xdmplugin` requires a pre-installed `yt-dlp` executable on the host system. The executable path must be configured in Settings > Plugins.
2. **Relocated Executable Repair:** If you move the portable XDM folder to a new drive letter, run **Settings > Browser > Repair Integration** to update Windows protocol handler and native messaging paths.
3. **Firewall Rule on Port 8597:** The browser extension communicates strictly over local loopback (`127.0.0.1:8597`). Overly aggressive third-party firewalls blocking localhost loopback traffic may trigger a false "Disconnected" badge.

