# XDM Enhanced v1.0.1 (Maintenance Release)

Maintenance Release of **XDM Enhanced** — an independent, open-source download manager forked from Xtreme Download Manager (XDM). It pairs high-speed multi-connection segmented HTTP/HTTPS downloading and streaming media capture with native BitTorrent and magnet support, modern FlatLaf dynamic theming, custom skin packages, a public plugin framework, automated post-download rules, and companion browser extensions.

---

## Release Highlights

- **Active Chunk Visualization & Live Connection Details:** High-contrast visual pulse animation in `SegmentVisualizationBar` and a detailed connection table (`SegmentDetailTable`) displaying real-time stream bounds, byte offsets, transfer rates, status flags, and IP addresses.
- **High-Frequency Telemetry & UI Responsiveness:** Transitioned speed calculation to a ~10ms moving-average sampling window with 100ms batched UI coalescing, preventing EDT thread congestion under gigabit download speeds while providing fluid transfer telemetry.
- **BitTorrent Modal Workspace Bounding:** Strictly bounded dialog dimensions to usable monitor workspace geometry and constrained magnet URI input field width, preventing modal dialog expansion off-screen with 2,000+ character magnet links. Added a dedicated "Copy Source" clipboard button.
- **Modern Cards Context Menu Parity:** Restored full right-click context menu parity across Modern Cards View, matching Table View and Classic View capabilities while preventing left-click selection misfires.
- **Robust Running-Application Handling in Installer:** Isolated target detection verifying executable path and directory containment (`process-manager.ps1`), authenticated loopback HTTP graceful shutdown (`/system/shutdown`) with persistent state preservation, interactive user escalation, and silent `/FORCE_CLOSE` support.
- **Canonical Help, Documentation & Wiki Links:** Restored all Help actions and sidebar navigation to point to authoritative fork documentation (`https://jakepanlilio2000.github.io/XDM-Enhanced/`), repository guides, and community support destinations with scheme validation and fallback handlers.
- **NSIS License RTF Formatting:** Fixed control word escaping (`\widowctrl\hyphauto`) in `gpl-3.0-notice.rtf`, eliminating raw markup tokens from rendering as literal text in the installer license screen.

---

## Changes & Fixes

### Added
- **Segment Visualization Bar (`xdm-app`):** High-contrast active chunk animation providing visual clarity on active stream boundaries.
- **Segment Detail Table (`xdm-app`):** Tabular connection inspection displaying chunk index, byte start/end ranges, current offset, active transfer rate, state, and remote IP address.
- **Copy Source Action (`xdm-app`):** Convenient button in the Add BitTorrent Download dialog to copy the entire raw magnet URI or source string to the clipboard.

### Changed
- **Transfer Rate Telemetry (`xdm-core`, `xdm-app`):** Real-time sub-second byte counting (~10ms) with moving-average rate smoothing and 100ms EDT coalescing.
- **Help & Support Navigation (`xdm-app`):** Synchronized all Help menus, About dialog links, and sidebar navigation to canonical fork documentation and support channels. Hardened `UrlHelper` with URI scheme validation (`isHttpOrHttpsUrl`), ASCII encoding fallback, and native Windows shell execution.

### Fixed
- **BitTorrent Modal Layout (`xdm-app`):** Constrained dialog size and input components to fit within the visible display bounds across single- and multi-monitor setups.
- **Context Menu Action Parity (`xdm-app`):** Unified `DownloadContextMenu` across Table View, Modern Cards View, and Classic View with left-click misfire guards.
- **NSIS License Formatting (`packaging/windows`):** Corrected single-backslash RTF syntax in `gpl-3.0-notice.rtf` preventing `\widowctrl` from appearing as raw text in the installer.
- **Installer Process Management (`packaging/windows`):** Verified directory containment before process termination, authenticated loopback graceful shutdown with state flush, interactive prompts, and silent automated flag handling.

---

## Verified Release Assets & Checksums

| Asset | Size | SHA-256 Checksum |
|---|---|---|
| `XDM-Enhanced-Extension-Chromium-v1.0.1.zip` | 106,503 bytes | `6db9d41834af3decb57d4c36a13d16e0eb72c8441556d667e82b6fbd3d72b1cd` |
| `XDM-Enhanced-Extension-Firefox-v1.0.1.xpi` | 103,600 bytes | `8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac` |
| `XDM-Enhanced-Extension-Firefox-v1.0.1.zip` | 103,600 bytes | `8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac` |
| `XDM-Enhanced-Setup-v1.0.1-win-x64.exe` | 63,960,863 bytes | `68be3296248f3f7704dba8cf1945ecc0dda125ed9ba898e468c9bc207b8de76c` |
| `XDM-Enhanced-v1.0.1-windows-x64.zip` | 74,296,378 bytes | `2fc4b10348f2eb198cdc01159d6d8fc7bd14eeabd3d3d991645902487a6537f1` |
| `checksum-verifier.xdmplugin` | 6,211 bytes | `c8f915c32a984515db8991537089f9324be18ab5c67421d419c5d30a83582806` |
| `cyber-neon.xdmskin` | 34,446 bytes | `5e8293da37215dbafff6a41a88495bdefcc0faa3f942c878ada69664ec05ef0f` |
| `xdm-app-1.0.1.jar` | 95,109,075 bytes | `31870f5068bfb6f49ad260fff4a010c2bb2efa3a6df1573bb2bb84399d830011` |
| `xdm-plugin-api-1.0.1.jar` | 21,965 bytes | `5636b47925d6957554fb5a4313af0e478fa1ac764d2c5cf30f5c30cba4de4cc7` |
| `ytdlp-downloader.xdmplugin` | 28,561 bytes | `8a484eff37e95d961e028c80b353ff84580d275a51e166860f5cfec29a5bf189` |
| `SHA256SUMS.txt` | 988 bytes | Included in release |

---

## Acknowledgments & Upstream Attribution

- Original XDM project created by Subhra Das Gupta ([subhra74/xdm](https://github.com/subhra74/xdm)) and upstream contributors. Upstream documentation is preserved in [README.UPSTREAM.md](README.UPSTREAM.md).
- `libtorrent4j` wrapper created by Alden Torres and the FrostWire team.
- `libtorrent` (Rasterbar) BitTorrent library developed by Arvid Norberg.
- `FlatLaf` look-and-feel developed by FormDev Software GmbH.
- Nullsoft Scriptable Install System (NSIS) project team.
- BellSoft for the Liberica JDK 25 runtime.

---

## License

XDM Enhanced is free, open-source software licensed under the **GNU General Public License v3.0 (GPL-3.0)**.
See [LICENSE](LICENSE) for the full license text.
