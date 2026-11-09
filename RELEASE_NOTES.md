# XDM Enhanced v1.0.0-beta.4

Fourth beta release of **XDM Enhanced** — an independent, open-source download manager forked from Xtreme Download Manager (XDM). It pairs high-speed multi-connection segmented HTTP/HTTPS downloading and streaming video capture with native BitTorrent and magnet support, modern FlatLaf dynamic theming, automated post-download rules, download completion power controls, network optimization presets, and companion browser extensions.

---

## Release Highlights

- **Fork-Specific Application Updater:** Dedicated in-app auto-update discovery querying releases exclusively from the GitHub fork (`jakepanlilio2000/XDM-Enhanced`). Supports both **Stable** and **Beta (Prerelease)** channels, Semantic Versioning comparison, in-app download progress tracking, cancellation, and cryptographic SHA-256 integrity verification before launching installers.
- **Persistent Progress Dialog on Pause:** Pausing a download now keeps the progress dialog visible and open. Accurately indicates transfer transitions (`Downloading` -> `Pausing...` -> `Paused` -> `Resuming...` -> `Completed`), retains the confirmed progress percentage and byte count, stops showing stale speeds, and provides an immediate Resume button. Closing the progress window safely dismisses the dialog without canceling the download.
- **Native Windows Default Apps Navigation:** Fixed the "Open Windows Settings" action in BitTorrent settings launching a web browser. Now natively executes Windows shell protocol navigation (`start "" "ms-settings:defaultapps"`) to directly open Windows Default Apps, with debounce rate-limiting and legacy Control Panel fallback.
- **Categorized File & Dialog Icons:** Comprehensive category vector icon set across download dialogs. Add Download and Progress windows render distinct, theme-adaptive icons for Documents, Compressed, Music, Videos, Programs, Images, Torrents, and Other files, with dynamic icon updates when changing URLs, filenames, or categories.
- **Browser Extension Update Integration:** Overhauled Chromium (Manifest V3) and Firefox (Manifest V2) extension update manifests and discovery endpoints. Added in-extension notifications on popup and settings pages with direct links to verified GitHub releases, while preserving localhost loopback IPC security without remote code execution.

---

## Changes & Fixes

### Added
- **Native Application Updater (`UpdateDialog`, `UpdateChecker`):**
  - Configurable update checks in Settings > General.
  - Channel selector: Stable vs. Beta.
  - Markdown-rendered release notes display.
  - Platform/architecture asset matching.
  - Automated SHA-256 checksum verification against published release manifests.
- **Browser Extension Update Feeds:**
  - `chrome-updates.xml` for enterprise-managed Chromium deployments.
  - `updates.json` for self-hosted Firefox distributions.
  - Synchronized desktop-extension version compatibility reporting.

### Fixed
- **Download Progress Window Closure on Pause:** Fixed issue where pausing an active transfer prematurely disposed the progress window.
- **BitTorrent Windows Settings Button:** Fixed button launching web browser instead of opening `ms-settings:defaultapps`.
- **Localization Warning:** Added missing `CAT_IMAGES=Images` entry to `en.txt` to eliminate missing translation warnings.
- **Loopback Socket Test Draining:** Added socket drain delay in refresh link tests to eliminate race conditions during heavy parallel test execution.

### Improved
- **Download Category Styling:** Added image extensions (`.png`, `.jpg`, `.webp`, `.svg`, etc.) and `.torrent` to `CategoryStyle`.
- **DPI-Scaled Category Badges:** 36px icon badges and display labels in `ProgressWindow` for HTTP and BitTorrent downloads.
- **Dropdown Category Icons:** Category comboboxes in `NewDownloadWindow` and `NewTorrentDownloadWindow` display scalable category icons next to each option.

---

## Known Issues

- **Unsigned Firefox Add-ons:** Standard Firefox release channels require unlisted Mozilla AMO signing (`web-ext sign`) for permanent `.xpi` add-on installation. Unsigned extensions can be loaded as temporary add-ons via `about:debugging` or permanently on Firefox Developer Edition/Nightly with `xpinstall.signatures.required = false`.
- **Consumer Chrome Extension Loading:** Google Chrome consumer builds require Developer Mode to load unpacked `.zip` extension folders from outside the Chrome Web Store.

---

## Release Assets & Checksums

| Asset | Size | SHA-256 Checksum |
|---|---|---|
| `XDM-Enhanced-Setup-v1.0.0-beta.4-win-x64.exe` | 63,548,724 bytes | `c31dc1c69597e7656b774162bee25ae220b38b6dd793c4d9854b9c1844ebb7eb` |
| `XDM-Enhanced-v1.0.0-beta.4-windows-x64.zip` | 74,341,547 bytes | `eccb8cd7e63ab1b41eb701dffe42a2aefd28e8b8dc646a3807175c8cd1dfacdc` |
| `xdm-app-1.0.0-beta.4.jar` | 94,179,254 bytes | `34adba46b03ac7d3cb4bad78e87b11ab6711db2b62674a733292c376b29d2c55` |
| `XDM-Enhanced-Extension-Chromium-v1.0.0-beta.4.zip` | 87,827 bytes | `b39903ea750e608b791693dde1ed07d846d27ed5dc7778038fbeba7bfb14bf19` |
| `XDM-Enhanced-Extension-Firefox-v1.0.0-beta.4.zip` | 84,027 bytes | `2a7b5ad65015412e32783a607f694776ccf408cdce55a385d31840728e95e93f` |
| `XDM-Enhanced-Extension-Firefox-v1.0.0-beta.4.xpi` | 84,027 bytes | `2a7b5ad65015412e32783a607f694776ccf408cdce55a385d31840728e95e93f` |
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
