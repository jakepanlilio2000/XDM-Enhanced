# XDM Enhanced v1.0.0-beta.3

Third beta release of **XDM Enhanced** — an independent, open-source download manager forked from Xtreme Download Manager (XDM). It pairs high-speed multi-connection segmented HTTP/HTTPS downloading and streaming video capture with native BitTorrent and magnet support, modern FlatLaf dynamic theming, automated post-download rules, download completion power controls, network optimization presets, and companion browser extensions.

---

## Release Highlights

- **Dedicated Language Settings Tab:** Added a dedicated tab in Settings supporting live switching across all 32 bundled languages with immediate UI refresh and persistent locale storage.
- **Segmented Download Progress Bar:** Real-time multi-color segmented progress bar visualizer showing active HTTP connections, downloaded chunk byte boundaries, active transfer rates, and connection counts.
- **Table View vs. Classic Cards View:** Distinct dual layout presentation modes accessible via toolbar toggle and Settings, offering a high-density tabular view with sorting and a nostalgic card-based view.
- **Network Optimizer:** Quick connection presets (High Speed LAN / Fiber, Broadband / 4G, Slow / Mobile, or Custom) adjusting segment limits, connection timeouts, and buffer sizes.
- **Refresh Download Link / URL:** Update expired download links with pre-flight byte-range and content-length validation before updating active or paused download tasks.
- **Magnet Metadata Naming:** Dynamic resolution of torrent names upon magnet metadata retrieval, replacing placeholder names in real time across the UI, persistent storage, and disk paths.
- **Existing Torrent Data Detection & Piece Recheck:** Automatic detection of existing files when adding torrents, prompting the user for verification and leveraging `libtorrent` fastresume piece hashing.
- **Wayland & Linux Platform Settings:** Advanced settings tab providing native Wayland toolkit flags and window decoration management on Linux desktops.
- **Redesigned About Dialog:** Professional FlatLaf application info window featuring version metadata, upstream attribution, license details, and a one-click GitHub star button.

---

## Added Features & Improvements

### User Interface & Layout
- **Dedicated Language Tab:**
  - Placed alongside General, Network, and Advanced tabs in Settings.
  - Live language switcher with instant application of translated strings across open windows.
  - Preserves user language selection in `~/.xdm-enhanced/xdm-settings.json`.
- **View Mode Switching:**
  - High-density **Table View** with sortable columns (Name, Size, Progress, Speed, Status, ETA).
  - Nostalgic **Classic Cards View** with item cards, progress rings, and action buttons.
  - Toolbar toggle button and Settings preference for immediate view toggling.
- **Segmented Download Progress Visualizer:**
  - Multi-segmented visual bar displaying active chunk download boundaries in real time.
  - Connection metric pill counters for active worker threads and segment speed distribution.
  - Seamless fallback for single-connection downloads or streaming captures.
- **Redesigned About Dialog:**
  - Modern FlatLaf card styling with application icon and build metadata.
  - Upstream project acknowledgment and licensing disclosures (GPL-3.0).
  - Interactive "Star on GitHub" button opening `https://github.com/jakepanlilio2000/XDM-Enhanced`.

### BitTorrent Engine
- **Magnet Metadata Resolution:**
  - Replaces generic "Magnet Download" placeholder name once `.torrent` metadata is resolved from the DHT/swarm.
  - Sanitizes filesystem characters and updates UI tables, card views, and persistent task state.
- **Existing Torrent Data Detection & Recheck:**
  - Proactively checks destination directories for existing payload files.
  - Prompts user to perform full piece hash verification using `libtorrent`'s native piece-checking engine.
  - Reuses valid pieces and repairs corrupt sectors without redownloading verified data.

### Download Engine & Networking
- **Network Optimizer Presets:**
  - **High Speed (LAN / Fiber):** 32 max segments, 30s timeout, 128KB buffer.
  - **Broadband / 4G:** 16 max segments, 60s timeout, 64KB buffer.
  - **Slow / Mobile:** 8 max segments, 120s timeout, 32KB buffer.
  - **Custom:** Full manual control over segment count, timeouts, and buffer sizes.
- **Download Link Refresh:**
  - Dedicated "Refresh Link" action for interrupted or expired downloads.
  - Validates byte ranges and file length against the original task before resuming.
- **Automatic File Categorization:**
  - Restored category sidebar with Document, Music, Video, Program, and Archive groupings.
  - Automatically categorizes downloads by file extension into user-configurable destination folders.

### Linux & Platform Integration
- **Wayland Settings:**
  - UI toggles for Wayland environment parameters (`-Dawt.useSystemAAFontSettings=on`, `-Dsun.java2d.opengl=true`).
  - Safe desktop environment detection ensuring options are presented cleanly across platforms.

---

## Release Assets & Checksums

| Asset | Size | SHA-256 Checksum |
|---|---|---|
| `XDM-Enhanced-Setup-v1.0.0-beta.3-win-x64.exe` | 64,246,627 bytes | `359a0c1ed0b44464d4a199487e76c1c00cb91726e4ff02c78a5d3ecb65b51473` |
| `XDM-Enhanced-v1.0.0-beta.3-windows-x64.zip` | 75,026,211 bytes | `0c1d437bc01d69e05f4081530cb9a5ac1b630085551e73e672e21f6fae890b7b` |
| `xdm-app-1.0.0-beta.3.jar` | 96,637,276 bytes | `cba2726d8df15d312244de66acb5ecf91f247514abaf99f38c9ba696c7401cc8` |
| `XDM-Enhanced-Extension-Chromium-v1.0.0-beta.3.zip` | 84,171 bytes | `df7fb949f395a7219c270ab45c7c15040b670f1fc8b3c6bfe50004c1273f234e` |
| `XDM-Enhanced-Extension-Firefox-v1.0.0-beta.3.zip` | 80,353 bytes | `c331e30bbbd5bd61b29a212ee66059c9bcebc74135b01c129753159a41333c9a` |
| `SHA256SUMS.txt` | | Included in release |

---

## Acknowledgments & Upstream Attribution

- Original XDM project created by Subhra Das Gupta ([subhra74/xdm](https://github.com/subhra74/xdm)) and upstream contributors.
- `libtorrent4j` wrapper created by Alden Torres and the FrostWire team.
- `libtorrent` (Rasterbar) BitTorrent library developed by Arvid Norberg.
- `FlatLaf` look-and-feel developed by FormDev Software GmbH.
- Nullsoft Scriptable Install System (NSIS) project team.
- BellSoft for the Liberica JDK 25 runtime.

---

## License

XDM Enhanced is free, open-source software licensed under the **GNU General Public License v3.0 (GPL-3.0)**.
See [LICENSE](LICENSE) for the full license text.
