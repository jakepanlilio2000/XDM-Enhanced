# XDM Enhanced v1.0.0-beta.5

Fifth beta release of **XDM Enhanced** — an independent, open-source download manager forked from Xtreme Download Manager (XDM). It pairs high-speed multi-connection segmented HTTP/HTTPS downloading and streaming video capture with native BitTorrent and magnet support, modern FlatLaf dynamic theming, custom skin packages, a public plugin framework, floating browser media grabbing, automated post-download rules, and companion browser extensions.

---

## Release Highlights

- **Extensible Skin Engine & Custom Skin Packages (`.xdmskin`):** Full skinning support allowing users to switch between the modern **XDM Enhanced** theme and authentic **Original XDM** classic theme, or install portable community `.xdmskin` packages. Supports custom window backgrounds, wallpapers, toolbar icons, category icons, buttons, fonts, border radii, and accent overrides with live hot-reloading and sandboxed package validation.
- **Core Plugin Framework & Developer API (`xdm-plugin-api`):** Introduced a decoupled public Java SPI module allowing third-party developers to extend XDM Enhanced without modifying the core codebase. Features sandboxed lifecycle management (discovery, validation, quarantine by default, trust verification), dedicated Settings > Plugins UI, and working reference plugins (Checksum Verifier and optional yt-dlp media downloader).
- **Floating Browser Video Downloader & Quality Selection:** Lightweight, non-intrusive floating download button anchored to detected HTML5 `<video>` players in Chromium and Firefox extensions. Features Shadow DOM style isolation, responsive sizing, fullscreen adaptation, keyboard accessibility, format and quality selection (4K down to 480p), audio track badges (Muxed vs. Video Only), estimated size notation (`≈ ... (est.)`), EME DRM inspection with advisory warning, and offline desktop detection.

---

## Changes & Fixes

### Added
- **Skin Engine (`xdm-app`):**
  - Portable `.xdmskin` ZIP archive format with `skin.json` declarative manifest.
  - Skin Management UI in Settings > Appearance with preview cards, install, export, and delete actions.
  - Security validation defending against Zip Slip path traversal and malicious binaries.
  - Complete developer documentation ([Skin Development Guide](docs/SKIN_DEVELOPMENT.md)) and sample skin [`sample-skins/cyber-neon.xdmskin`](sample-skins/cyber-neon.xdmskin).
- **Plugin Architecture (`xdm-plugin-api`, `xdm-app`):**
  - Dedicated Maven module `xdm-plugin-api` exposing clean SPI interfaces (`XdmPlugin`, `PluginContext`, `DownloadProvider`, `MediaProvider`, `PluginDownloadTask`, `PluginProgressEvent`).
  - Quarantine-by-default security model requiring explicit user activation.
  - Dedicated Plugins panel in Settings.
  - Reference plugins and documentation: [Plugin Development Guide](docs/PLUGIN_DEVELOPMENT.md), `sample-plugins/checksum-verifier.xdmplugin`, and `sample-plugins/ytdlp-downloader.xdmplugin`.
- **Floating Browser Video Downloader (`browser-extension`):**
  - Floating download button appearing on supported video players.
  - Dropdown quality picker with detected resolutions, container types, and audio availability.
  - Format integrity ensuring HLS/DASH streams are never falsely represented as MP4 files.
  - Encrypted Media Extensions (EME) DRM detection (`mediaKeys`) displaying a `DRM Protected Stream` banner.
  - Asynchronous download dispatch with offline status detection.

### Changed
- **Version Alignment:** Updated all application modules, installer configurations, and companion extensions to `1.0.0-beta.5`.
- **Documentation Overhaul:** Modernized `README.md` to provide a concise, readable overview focused on verified functionality, clean installation steps, and developer guides.

---

## Known Issues

- **Unsigned Firefox Add-ons:** Standard Firefox release channels require unlisted Mozilla AMO signing (`web-ext sign`) for permanent `.xpi` add-on installation. Unsigned extensions can be loaded as temporary add-ons via `about:debugging` or permanently on Firefox Developer Edition/Nightly with `xpinstall.signatures.required = false`.
- **Consumer Chrome Extension Loading:** Google Chrome consumer builds require Developer Mode to load unpacked `.zip` extension folders from outside the Chrome Web Store.

---

## Release Assets & Checksums

| Asset | Size | SHA-256 Checksum |
|---|---|---|
| `XDM-Enhanced-Setup-v1.0.0-beta.5-win-x64.exe` | 64,614,210 bytes | `6e4e94a2ab91a5519ae248ac3c29dded6a3dc1b165bfba08f8b63bbe3ee6bd99` |
| `XDM-Enhanced-v1.0.0-beta.5-windows-x64.zip` | 75,403,564 bytes | `ccc87b841cce61ca208e28374e4531f774b550f3dc20c3881475db9861649fe7` |
| `xdm-app-1.0.0-beta.5.jar` | 97,594,661 bytes | `62439b492e6245ecaaeca37f5f7f8611052cd94cc62066b9eb974e444f42e10f` |
| `xdm-plugin-api-1.0.0-beta.5.jar` | 20,377 bytes | `2dd312bd35465412da7783b7d1e5399172924d31be5c997603ee0a316508fe9d` |
| `XDM-Enhanced-Extension-Chromium-v1.0.0-beta.5.zip` | 102,180 bytes | `925fdb36d4234316cda079f1fcc7cb849432ae6a70ada5972baaf21673ec7988` |
| `XDM-Enhanced-Extension-Firefox-v1.0.0-beta.5.zip` | 98,607 bytes | `ff50587ef2d8f0b56e65c78761a9da6b4ef34b383b2cd1669e3fc5114aef99ad` |
| `XDM-Enhanced-Extension-Firefox-v1.0.0-beta.5.xpi` | 98,607 bytes | `ff50587ef2d8f0b56e65c78761a9da6b4ef34b383b2cd1669e3fc5114aef99ad` |
| `cyber-neon.xdmskin` | 34,100 bytes | `a562d6f545f92c6b321cd80df8783adfd8d25120df4bc48caa5ecd59ade2807c` |
| `checksum-verifier.xdmplugin` | 6,213 bytes | `bdfb11fa84e4cd3bdd703911aeb7d7de8f40542d630f26f1725febfca638b2ac` |
| `ytdlp-downloader.xdmplugin` | 27,306 bytes | `92fc02f6d8e1b5540923596ddbe797afd6705aa894ba71afec37646349ada721` |
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
