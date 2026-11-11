# XDM Enhanced v1.0.0-rc.1

First Release Candidate of **XDM Enhanced** — an independent, open-source download manager forked from Xtreme Download Manager (XDM). It pairs high-speed multi-connection segmented HTTP/HTTPS downloading and streaming video capture with native BitTorrent and magnet support, modern FlatLaf dynamic theming, custom skin packages, a public plugin framework, floating browser media grabbing, automated post-download rules, and companion browser extensions.

---

## Release Highlights

- **Skin Background & Modal Customization System:** Extends the skinning engine to support declarative custom background images and colors for windows, sidebars, toolbars, and modal dialogs via `skin.json`. Adds `SkinBackgroundPanel` with tiling, scaling, centering, and opacity control, along with modal dimming and contrast-preserving legibility.
- **User-Controlled Clipboard Monitoring & Non-Intrusive Preflight:** Enhanced clipboard security and network efficiency. Only single HTTP/HTTPS URLs (<= 2,048 chars) are evaluated when clipboard monitoring is enabled; credentials, multi-line blocks, and unrelated text are rejected immediately. Performs lightweight `HEAD`/range metadata discovery with **zero payload bytes** downloaded before explicit user confirmation.
- **Public Plugin API v2 & Task Adapter Bridge:** Introduces semantic version-range compatibility matching (`[1.0.0, 2.0.0)`) preventing minor host updates from breaking plugins. Decouples plugin messaging via a publish-subscribe `PluginEventBus` and provides a public task adapter bridge (`PluginDownloadBridge`) routing tasks into the core download manager.
- **Central Download List Integration for yt-dlp Plugin:** Solves missing yt-dlp tasks in the main download list. Tasks now register immediately with persistent IDs, `CAT_VIDEOS` categorization, real-time speed/ETA progress reporting, lifecycle state transitions (initialization through completion/failure), and safe `ProcessBuilder` argument isolation.
- **Browser Connection Self-Diagnostics & Safe Repair:** Eliminates false "XDM Enhanced Disconnected" warnings via resilient loopback handshake verification and exponential backoff retry. Features an in-extension diagnostic panel reporting precise failure reasons, plus a non-blocking "Repair Browser Integration" tool in Desktop Settings that repairs Windows native messaging manifests and protocol registrations safely without silent registry hijacking.

---

## Changes & Fixes

### Added

- **Skin Engine (`xdm-app`):**
  - Declarative background image and color customization for windows, panels, sidebars, toolbars, and dialogs.
  - `SkinBackgroundPanel` supporting tile, scale, center, and adjustable opacity modes.
  - Dialog backdrop tinting with contrast preservation for accessibility.
  - Component styling resolver (`SkinStyleResolver`) for unified button radii, padding, and borders across dialogs.
  - Live skin hot-reloading regression test coverage in `SkinEngineTest` and `FullUiSkinContractTest`.
- **Clipboard & Preflight Architecture (`xdm-app`):**
  - Strict privacy filter: discards passwords, tokens, multiple URLs, and arbitrary text immediately.
  - Non-intrusive metadata preflight via HTTP `HEAD` or fallback `Range: bytes=0-0`.
  - Zero-payload preflight guarantee: no chunk files or payload bytes are downloaded before user confirmation.
  - In-flight socket cancellation when dialog is dismissed.
- **Plugin Architecture (`xdm-plugin-api`, `xdm-app`):**
  - Semantic version-range compatibility matching (`[minVersion, maxVersion)`).
  - Decoupled `PluginEventBus` with typed `PluginProgressEvent` and listener contracts.
  - Public `PluginDownloadBridge` and `PluginTaskAdapter` routing third-party downloads to the central download queue.
- **yt-dlp Media Downloader Plugin (`sample-plugins`):**
  - Full task registration and tracking in central download list across all views (table, cards, rows).
  - Progress tracking with speed, ETA, and state persistence.
  - Process argument hardening with list-based execution.
- **Browser Integration & Diagnostics (`browser-extension`, `xdm-app`):**
  - In-extension diagnostics reporting exact status (`host_missing`, `permission_denied`, `app_not_running`).
  - Desktop Settings "Repair Browser Integration" utility with asynchronous execution on background threads.
  - Comprehensive guide: [`docs/BROWSER_CONNECTION_TROUBLESHOOTING.md`](docs/BROWSER_CONNECTION_TROUBLESHOOTING.md).

### Changed

- **Version Alignment:** Synchronized project and artifact version declarations to `1.0.0-beta.6` across all Maven modules, NSIS installer, build configurations, and companion extensions.
- **Documentation:** Updated `README.md` and distribution guides with troubleshooting references.

### Fixed

- **Browser Extension Handshake:** Fixed false "Disconnected" warnings caused by startup race conditions using exponential backoff retry.
- **EDT Responsiveness:** Prevented Swing UI freezes during browser connection checks and integration repairs by dispatching network and registry I/O to background daemon threads.
- **Stale Registry Handlers:** Auto-detected invalid or missing executable paths in the Windows `xdm-app://` protocol registry handler.

### Security

- **Clipboard Privacy Guard:** Enforces strict length limits (<= 2048 chars) and single-URL validation; never inspects or logs sensitive clipboard history.
- **Preflight Isolation:** Guarantees headers-only inspection; no payload bytes downloaded before user confirmation.
- **Subprocess Argument Hardening:** Enforces array/list arguments for yt-dlp executions, rejecting command-line shell string concatenation.
- **Loopback Origin & Header Verification:** Strict host and origin verification on `127.0.0.1:8597` rejecting DNS rebinding with HTTP 403 Forbidden.

---

## Known Issues

- **Unsigned Firefox Add-ons:** Standard Firefox release channels require unlisted Mozilla AMO signing (`web-ext sign`) for permanent `.xpi` add-on installation. Unsigned extensions can be loaded as temporary add-ons via `about:debugging` or permanently on Firefox Developer Edition/Nightly with `xpinstall.signatures.required = false`.
- **Consumer Chrome Extension Loading:** Google Chrome consumer builds require Developer Mode to load unpacked `.zip` extension folders from outside the Chrome Web Store.
- **Platform File Dialog Styling:** Native OS file pickers retain operating system look-and-feel rather than custom skin backgrounds.

---

## Verified Release Assets & Checksums

| Asset | Size | SHA-256 Checksum |
|---|---|---|
| `XDM-Enhanced-Setup-v1.0.0-rc.1-win-x64.exe` | 89,720,692 bytes | `4d46a73574df3908c6766e484d14acf3c3b10d55d5235937172fa6a2c20f2b0c` |
| `XDM-Enhanced-v1.0.0-rc.1-windows-x64.zip` | 111,821,592 bytes | `b93954154dbd06eb92640dfd21047ac74382c917f6ffb86f6d3b942dd7fb5d75` |
| `XDM-Enhanced-Extension-Chromium-v1.0.0-rc.1.zip` | 106,507 bytes | `e4a9fbad42a9354318c9d410a8306f96af12f667197209b2295444033f975d00` |
| `XDM-Enhanced-Extension-Firefox-v1.0.0-rc.1.zip` | 103,600 bytes | `8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac` |
| `XDM-Enhanced-Extension-Firefox-v1.0.0-rc.1.xpi` | 103,600 bytes | `8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac` |
| `xdm-app-1.0.0-rc.1.jar` | 95,042,279 bytes | `e1c68779c52921c156e20a4a0a46a1a03c1280656c70b742475706b9aa2b9055` |
| `xdm-plugin-api-1.0.0-rc.1.jar` | 21,971 bytes | `925b39b2b1a15ea576aa656d75f7d87acfece8aeb27a865519b97d6e0b23129b` |
| `cyber-neon.xdmskin` | 34,446 bytes | `5e8293da37215dbafff6a41a88495bdefcc0faa3f942c878ada69664ec05ef0f` |
| `checksum-verifier.xdmplugin` | 6,213 bytes | `d5f9439fe969c74b0b10ec400168607685946a96a19ea8cfb25acc7cf19d1882` |
| `ytdlp-downloader.xdmplugin` | 28,567 bytes | `70270b4281ad36812cba8efc1ac056af95d2f425e76394d904ff5cf42004aaab` |
| `SHA256SUMS.txt` | 1,023 bytes | Included in release |

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
