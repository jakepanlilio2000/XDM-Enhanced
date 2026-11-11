# Changelog

All notable changes to the XDM Enhanced project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0-rc.1] - 2026-11-11

### Added

- **Release Candidate Verification Suite:** Added comprehensive 15-phase audit and verification suites covering media capture, browser handshake, theme engine, plugin API v2, database migration rollback, desktop view-mode switching, NSIS uninstaller safety, credential scrubbing, and stress profiling.
- **Browser Diagnostic Error Dispatch:** Implemented explicit handling for `host_missing` and `launch_failure` in companion browser extensions.
- **Dynamic Theme Resilience:** Added defensive `FlatLaf.updateUI()` shielding in `ThemeManager` preventing transient window states from aborting theme application.

### Fixed

- **Path & Device Name Sanitization:** Hardened `FileNameHelper` against Windows reserved device names (`CON`, `PRN`, `AUX`, `NUL`, `COM1-9`, `LPT1-9`) and control characters.
- **Transactional Migration Rollback:** Added rollback protection preserving legacy state when encountering corrupted download metadata during migration.
- **Uninstaller Directory Containment:** Secured NSIS uninstaller verifying installation target paths before recursive directory deletion.
- **Modal Dialog Keyboard Navigation:** Added `Escape` key dismissals and hardened native Windows URI association resolution.
- **AWT Test Harness Teardown:** Resolved lingering window leaks across UI test suites.

## [1.0.0-beta.6] - 2026-11-11

### Added

- **Skin Background & Modal Customization System:**
  - Extended the skinning engine to support declarative custom background images and colors for main windows, panels, sidebars, toolbars, and modal dialogs via `skin.json`.
  - Added `SkinBackgroundPanel` with support for image tiling, scaling, centering, and adjustable opacity.
  - Modal dialog backdrop customization with automatic contrast and legibility preservation.
  - Component-level styling resolver (`SkinStyleResolver`) for consistent button arc, border radiuses, and padding across all dialogs.
  - Live skin hot-reloading with regression coverage in `SkinEngineTest` and `FullUiSkinContractTest`.
- **User-Controlled Clipboard Monitoring & Non-Intrusive Link Preflight:**
  - Strict privacy protections for clipboard monitoring: only single HTTP/HTTPS URLs under 2,048 characters are parsed. Credentials, multi-line blocks, unrelated text, and secret tokens are discarded immediately without logging.
  - Non-intrusive metadata preflight: performs lightweight HTTP `HEAD` (with `Range: bytes=0-0` fallback) to discover file name, Content-Length, Content-Type, and server resume capability.
  - Zero-payload guarantee: absolutely no payload data or segmented chunks are downloaded prior to explicit user initiation ("Download Now" or approved auto-start).
  - Preflight cancellation: cleanly cancels in-flight socket requests when users dismiss the New Download dialog.
- **Public Plugin API v2 & Task Adapter Bridge:**
  - Introduced semantic version-range compatibility matching (`[1.0.0, 2.0.0)`) for plugins, ensuring minor application updates do not break installed plugins.
  - Decoupled Plugin Event Bus (`PluginEventBus`) supporting publish-subscribe event dispatching (`PluginEventListener`, `PluginProgressEvent`).
  - Implemented public task adapter bridge (`PluginDownloadBridge`) routing plugin tasks into XDM Enhanced's central download scheduler without tight coupling to internal engine classes.
- **Central Download List Integration for yt-dlp Plugin:**
  - Fixed yt-dlp plugin downloads missing from the main download table/cards by routing tasks through the public task adapter bridge with stable IDs, proper metadata, and `CAT_VIDEOS` categorization.
  - Real-time download progress, speed, ETA, and state synchronization across initializing, downloading, paused, cancelled, completed, and error states.
  - Subprocess execution isolation: invoked via `ProcessBuilder` with strict list argument passing to eliminate shell injection vulnerabilities.
- **Browser Connection Self-Diagnostics & Troubleshooting:**
  - Added self-diagnostics panel in extension options and error pages displaying live connection state, detected issues (`host_missing`, `permission_denied`, `app_not_running`), and a "Check Connection" retry button.
  - Safe desktop repair utility in Settings > General: checks and repairs Windows native messaging manifest paths and protocol handler registrations (`xdm-app://`), validating that executables exist on disk.
  - Comprehensive technical troubleshooting guide: [`docs/BROWSER_CONNECTION_TROUBLESHOOTING.md`](docs/BROWSER_CONNECTION_TROUBLESHOOTING.md).

### Fixed

- **False "XDM Enhanced Disconnected" Warnings:**
  - Resolved spurious disconnect warnings in Chromium and Firefox extensions with exponential backoff retry mechanisms and resilient loopback handshake verification.
  - Prevented event dispatch thread (EDT) freezes by shifting integration diagnostics and repair routines to background daemon threads with responsive UI progress indicators.
  - Fixed stale registry protocol handler detection when application binary paths are relocated or deleted.

### Security

- **Clipboard Privacy Guard:** Enforced strict length limits (<= 2048 chars) and single-URL validation; never inspects, captures, or transmits passwords, clipboard history, or non-URL data.
- **Preflight Isolation:** Enforced that preflight requests only fetch HTTP headers; never pre-downloads payload content before user confirmation.
- **Subprocess Argument Hardening:** Enforced explicit array/list arguments for yt-dlp executions, rejecting command-line shell string concatenation.
- **Loopback Origin & Header Verification:** Strict host and origin verification on `127.0.0.1:8597` rejecting DNS rebinding with HTTP 403 Forbidden.

## [1.0.0-beta.5] - 2026-11-10

### Added

- **Extensible Skin Engine & Custom Skin Packages (`.xdmskin`):**
  - Integrated skin engine supporting built-in skins (**XDM Enhanced** modern FlatLaf theme and authentic **Original XDM** classic theme) as well as portable, declarative `.xdmskin` archive packages.
  - Comprehensive customization points: custom window backgrounds, wallpapers, toolbar icons, category sidebar icons, buttons, fonts, border radii, and accent overrides.
  - Security-hardened archive validation defending against Zip Slip path traversal and prohibiting native executable payloads.
  - Dedicated Skin Management interface in Settings > Appearance with preview cards, one-click installation, export, live hot-reloading, and deletion.
  - Complete developer documentation and reference package: [Skin Development Guide](docs/SKIN_DEVELOPMENT.md) and [`sample-skins/cyber-neon.xdmskin`](sample-skins/cyber-neon.xdmskin).
- **Core Plugin Framework & Public Developer API (`xdm-plugin-api`):**
  - Dedicated public Java Service Provider Interface (SPI) module (`xdm-plugin-api`) defining contracts: `XdmPlugin`, `PluginContext`, `DownloadProvider`, `MediaProvider`, `PluginDownloadTask`, `PluginProgressEvent`, and settings registration.
  - Sandboxed lifecycle management: discovery, metadata validation, quarantine by default, user-confirmed trust activation, initialization, and safe uninstallation.
  - Dedicated Plugins panel in Settings with capability listings, status badges, configuration dialogs, and safe-mode fallback.
  - Reference implementations: Checksum Verifier plugin (`sample-plugins/checksum-verifier.xdmplugin`) and optional yt-dlp downloader integration (`sample-plugins/ytdlp-downloader.xdmplugin`).
  - Comprehensive public developer guide: [Plugin Development Guide](docs/PLUGIN_DEVELOPMENT.md).
- **Floating Browser Video Downloader & Quality Selection:**
  - Lightweight, non-intrusive floating download button anchored to detected HTML5 `<video>` players in Chromium and Firefox extensions.
  - Shadow DOM style isolation to prevent host page style bleed, responsive sizing, fullscreen adaptation, and keyboard accessibility (Tab, Enter, Escape, Arrow keys).
  - Stream quality and format resolution: displays detected container formats (MP4, WebM), resolutions (4K, 1440p, 1080p, 720p, 480p), audio track availability badges (Muxed vs. Video Only), and estimated stream sizes (`≈ ... (est.)`).
  - Strict format integrity: HLS and MPEG-DASH playlists are labeled accurately and never falsely claimed as MP4 files.
  - Encrypted Media Extensions (EME) DRM inspection: detects `mediaKeys` early and displays a clear DRM advisory alert without attempting invalid capture.
  - Asynchronous download dispatch with connection status verification and offline feedback.

### Changed

- **Version Alignment:** Synchronized project and artifact version declarations to `1.0.0-beta.5` across all Maven modules, NSIS installer, build configurations, and companion extensions.
- **Documentation Overhaul:** Reorganized `README.md` to present a concise, professional, and accessible overview focused on verified capabilities, clean installation steps, and developer extension points, moving development issue references to changelog and developer guides.

### Security

- **Package Verification:** Enforced strict canonical path checks (Zip Slip defense) in both `.xdmskin` and `.xdmplugin` archive unpackers.
- **Sandboxed Execution:** Disallowed native executable binaries and shell scripts inside third-party plugin packages.
- **Localhost IPC Hardening:** Enforced strict origin validation (`chrome-extension://`, `moz-extension://`) and loopback binding (`127.0.0.1:8597`) across all browser-to-desktop endpoints.

## [1.0.0-beta.4] - 2026-11-10

### Added

- **Fork-Specific GitHub Application Updater:**
  - Integrated native auto-updater configured exclusively to query GitHub Releases from the repository fork (`https://github.com/jakepanlilio2000/XDM-Enhanced`). Upstream feeds are never queried.
  - Full support for **Stable** and **Beta (Prerelease)** release channels with accurate Semantic Versioning (`SemVer`) comparison.
  - Configurable background update checks and manual "Check for Updates" action available in Settings > General and the Application menu.
  - Dedicated modern **UpdateDialog** featuring real markdown release notes, platform-compatible asset detection, progress-tracked in-app downloads, cancellation, and SHA-256 package checksum verification prior to launching installers.
- **Browser Extension Update Discovery & Distribution System:**
  - Implemented update checking and discovery mechanisms adhering to browser security constraints for both Chromium (Manifest V3) and Firefox (Manifest V2).
  - Hosted update manifests: `browser-extension/chrome-updates.xml` for enterprise-managed Chromium deployments and `browser-extension/updates.json` for self-hosted Firefox distributions.
  - In-extension notifications in Popup and Options pages when new versions are available, linking directly to verified GitHub Release packages.
  - Synchronized native messaging version compatibility handshakes between extensions and desktop application without loading remote executable scripts.

### Fixed

- **Persistent Download Progress Dialog on Pause:**
  - Resolved progress window closing or disappearing when clicking Pause during an active download.
  - The progress window remains open and accurately reflects transfer states (`Downloading` -> `Pausing...` -> `Paused` -> `Resuming...` -> `Completed`).
  - Preserves filename, URL, destination folder, progress bar percentage, and confirmed downloaded bytes.
  - Stops showing stale transfer speeds on pause and switches to paused indicators (`---`).
  - Swaps Pause control for an enabled Resume button upon confirmed pause.
  - Close button safely dismisses the progress window without canceling or terminating the background download.
- **Native Windows Settings Navigation:**
  - Fixed "Open Windows Settings" button in BitTorrent settings launching the default web browser instead of opening the intended Windows Settings page.
  - Replaced browser URL delegation with direct Windows shell protocol execution (`start "" "ms-settings:defaultapps"`) to reliably launch Windows Default Apps settings.
  - Added debounce rate-limiting to prevent multiple setting window spawns on rapid clicks.
  - Included legacy fallback to `control.exe /name Microsoft.DefaultPrograms` and safe no-op handling for non-Windows platforms.

### Improved

- **Category Icons Across Download Dialogs:**
  - Expanded `CategoryStyle` to include vector icons, theme-compatible colors, and display names for all supported categories: Documents, Compressed, Music, Videos, Programs, Images, Torrents, and Other/Uncategorized.
  - Added DPI-scaled category icons to Add Download (`NewDownloadWindow`) and Torrent Add Download (`NewTorrentDownloadWindow`) category combo boxes.
  - Wired dynamic icon updating when entering download URLs, changing file names, or picking custom categories.
  - Added prominent 36px category icon badges and category labels to `ProgressWindow` for both HTTP and BitTorrent transfers.
- **Update Channel Selection & Packaging Safeguards:**
  - Clearer user feedback and diagnostics in Settings > General for update preferences.
  - Enhanced installer isolation preventing interference with upstream XDM installations.

### Known Issues

- **Unsigned Firefox Add-on Installation:** Firefox standard release channel requires unlisted AMO signing via `web-ext sign` to install `.xpi` extensions permanently; unsigned packages require Firefox Developer Edition/Nightly with `xpinstall.signatures.required = false` or temporary loading via `about:debugging`.
- **Consumer Chrome Extension Installation:** Google Chrome consumer builds require Developer Mode to load unpacked `.zip` extension archives outside the Chrome Web Store.

## [1.0.0-beta.3] - 2026-11-10

### Added

- **Dedicated Language Settings Tab:**
  - Extracted language selection into an independent `Language` tab in the Settings window.
  - Live UI translation switching across 32 supported language bundles without requiring application restart.
- **Table View vs. Classic Cards Dual Layout:**
  - Two distinct, selectable presentation modes for the download list:
    - **Table View:** Dense data grid with sortable columns for Name, Size, Status, Progress, Transfer Rate, and ETA.
    - **Classic Cards:** Informational card-based layout featuring rich file icons, inline progress bars, and direct action buttons.
  - View mode preference persists across application restarts.
- **Segment Visualization & Connection Table:**
  - Dedicated multi-segment colored visualization bar depicting downloaded, active, and pending chunks.
  - Collapsible segment details table displaying active connection counts, individual chunk byte offsets, status, and download rates.
- **Network Optimizer:**
  - Guided network tuning utility accessible from the Tools menu.
  - Low, Normal, and High / Aggressive connection presets optimizing connection limits, buffer sizes, and socket timeouts based on network latency and bandwidth.
- **Refresh Download Link:**
  - Integrated link replacement dialog to update expired or invalid download URLs (e.g., pre-signed S3/CDN URLs).
  - Pre-flight compatibility probing: validates that replacement URL matches expected file size and supports HTTP 206 byte-ranges before resuming existing chunks.
- **Existing Torrent Data Detection & Recheck:**
  - Automatic detection of pre-existing files when adding a torrent or selecting a custom destination.
  - Non-destructive piece hash checking using the BitTorrent session engine (`CHECKING_FILES` state), resuming only missing pieces and avoiding redundant downloads.
- **Redesigned About Dialog & GitHub Integration:**
  - Modernized FlatLaf card layout reflecting dynamic build metadata from `app-version.json`.
  - Visible "Star on GitHub" action button with system browser dispatch and clipboard fallback.
  - Tabbed interface providing detailed project description, license notices, upstream acknowledgment, and system runtime diagnostics.

### Improved

- **Automatic Download Categorization:**
  - Extension-based categorization engine automatically sorting downloads into Documents, Music, Video, Programs, and Compressed.
  - Dedicated destination directory routing per category with support for user-defined custom rules and manual overrides.
- **Magnet Metadata Naming:**
  - Immediate temporary naming state ("Fetching metadata...") displaying hash or display name parameter until torrent metadata arrives.
  - Automatic resolution to real torrent display name upon metadata arrival, updating Table View, Classic Cards, and progress dialogs on the Event Dispatch Thread and persisting to database.
- **Platform-Aware Wayland Settings:**
  - Display server detection on Linux (`XDG_SESSION_TYPE`, `WAYLAND_DISPLAY`).
  - Automatic fallback to lightweight popup menus and combo boxes (`WaylandComboBoxUI`) to eliminate flickering and heavy-weight window clipping under Wayland compositors.
- **Responsive Transfer-Speed & ETA Reporting:**
  - Smoothed download rate calculations and long ETA formatting across active HTTP and BitTorrent transfers.

### Fixed

- **Category Sidebar Visibility Restoration:**
  - Restored collapsible category navigation sidebar in the main window with category badge counts.
  - Fixed persistence bug where sidebar visibility state was not retained across application restarts.
- **Torrent Progress Window Segment Bar Visibility:**
  - Fixed regression where the HTTP segment visualization bar remained visible during BitTorrent transfers; segment panels are now strictly hidden in favor of torrent swarm metrics.
- **NSIS Dynamic Version Injection:**
  - Guarded `PRODUCT_VERSION` definition in `xdm-installer.nsi` with `!ifndef`, allowing build scripts (`build-bundle.ps1`) to pass release versions cleanly.
- **Path Traversal Sanitization in Torrents:**
  - Hardened torrent file paths and metadata against directory traversal sequences (`../../`), drive letters, and Windows reserved device names (`CON`, `PRN`, `AUX`, `NUL`).

### Acknowledgments

- Subhra Das Gupta (@subhra74) for the foundational XDM architecture.
- FormDev Software GmbH for FlatLaf modern Look and Feel.
- The libtorrent and libtorrent4j projects for native BitTorrent capabilities.
- Remix Design for the Remix Icon library.

## [1.0.0-beta.2] - 2026-11-09

### Added

- **Post-Download Action Rules Engine:**
  - Configurable action rules executed automatically upon download completion based on file extension matching.
  - Contextual variable placeholders: `%file%`, `%file_name%`, `%file_extension%`, `%file_directory%`, `%absolute_file_path%`, `%absolute_directory_path%`.
  - Settings UI for rule ordering, toggling, editing, deletion, and manual dry-run testing with real-time output and failure reporting.
  - *Addresses upstream report: [subhra74/xdm#1409](https://github.com/subhra74/xdm/issues/1409).*
- **Download Completion Power Actions:**
  - Selectable action when all active downloads finish: *Do nothing*, *Sleep / Suspend*, *Shutdown*, *Hibernate*.
  - Platform support checks verifying system capability before offering sleep or hibernation.
  - Cancellable 30-second countdown warning dialog allowing users to abort disruptive power actions.
  - *Addresses upstream report: [subhra74/xdm#1430](https://github.com/subhra74/xdm/issues/1430).*
- **Drag-and-Drop Completed Downloads:**
  - Direct file drag-and-drop (`DnD`) from completed download rows into Windows Explorer, desktop folders, email clients, and media players via `DownloadListTransferHandler`.
  - Incomplete, paused, or missing files are safeguarded from drag initiation.
  - *Addresses upstream report: [subhra74/xdm#1383](https://github.com/subhra74/xdm/issues/1383).*
- **Browser Extension Exclusions & Multi-Keyword Filters:**
  - "Block this site" website exclusion manager with hostname normalization, wildcard subdomain support (`*.example.com`), and dedicated management UI.
  - Multi-keyword filter bar in bulk link grabber supporting tokenized keyword chips, case-insensitivity, regex, and configurable AND/OR evaluation.
  - *Addresses upstream report: [subhra74/xdm#1406](https://github.com/subhra74/xdm/issues/1406).*
- **Desktop Browser Settings Tiles:**
  - Modernized compact browser settings view featuring clickable browser status cards for Chrome, Firefox, Edge, Brave, Opera, and Vivaldi.
  - Real-time detection of installed browsers and one-click extension installation and repair actions.
  - *Addresses upstream reports: [subhra74/xdm#1391](https://github.com/subhra74/xdm/issues/1391) and [subhra74/xdm#1372](https://github.com/subhra74/xdm/issues/1372) (browser UI refresh concept inspired by contributor Abhishek Mallav, @abhishekmallav).*
- **NSIS Windows Installer:**
  - Modern UI 2 installer (`XDM-Enhanced-Setup-v1.0.0-beta.2-win-x64.exe`) with dual installation scope (`perMachineOrUser`).
  - Bundled trimmed Liberica JDK 25 runtime, AppCDS metadata cache (`xdm.jsa` / `xdm-zgc.jsa`), Conscrypt TLS accelerator, and FlatLaf native integration.
  - Clean uninstaller with optional user configuration retention and complete registry cleanup.
- **Browser Extension Release Packaging:**
  - Dedicated packaging automation pipeline (`browser-extension/package-extensions.ps1`) producing clean, validated release zip archives for Chromium (MV3) and Firefox (MV2).
- **Linux Desktop Integration:**
  - Standard FreeDesktop `.desktop` application launcher generation, MIME associations (`x-scheme-handler/xdm`, `application/x-bittorrent`), and autostart registration.
  - *Addresses upstream report: [subhra74/xdm#1412](https://github.com/subhra74/xdm/issues/1412).*

### Changed

- **Browser Integration Settings:** Replaced legacy plain text link list with compact, interactive card tiles showing browser branding, connection status, and direct store links.
- **Companion Extension Version Handshake:** Updated `MIN_XDM_VERSION = "1.0.0"` in companion extensions to recognize desktop `v1.0.0-beta.2` as modern and current.

### Improved

- **Accent Customization & Theming:**
  - Eight curated FlatLaf accent presets (Indigo, Blue, Cyan, Emerald, Amber, Rose, Purple, Orange) with custom hex color picker and WCAG 2.1 contrast enforcement.
  - Live updating across all open windows and dialogs without requiring application restart.
  - *Addresses upstream report: [subhra74/xdm#1395](https://github.com/subhra74/xdm/issues/1395).*
- **System Theme Auto-Switching:**
  - Dynamic `System` theme follow mode tracking OS light/dark changes in real time.
  - *Addresses upstream report: [subhra74/xdm#1349](https://github.com/subhra74/xdm/issues/1349).*
- **Application Startup Performance:**
  - Sub-500ms startup achieved via AppCDS class metadata archives on Liberica JDK 25.

### Fixed

- **Download Folder Persistence:** Resolved issue where user-selected default download folders failed to persist across application restarts and browser-triggered downloads (*Addresses upstream report: [subhra74/xdm#1426](https://github.com/subhra74/xdm/issues/1426)*).
- **Clear Dialog Preferences:** Implemented preference persistence in Clear Downloads dialog with explicit confirmation guards for file deletion (*Addresses upstream report: [subhra74/xdm#1417](https://github.com/subhra74/xdm/issues/1417)*).
- **Dialog Centering:** Fixed `MessageBox` and custom dialogs centering relative to parent frames across multi-monitor setups.

### Security

- **Loopback Origin & DNS Rebinding Protection:** Hardened loopback IPC server with strict `Origin` header checking (`chrome-extension://`, `moz-extension://`) and DNS rebinding `Host` header enforcement (`127.0.0.1` / `localhost`).
- **Post-Download Rule Execution Safeguards:** Execution of external system commands is disabled by default; parameter substitution strictly quotes and tokenizes all placeholders (`%file%`, `%file_directory%`, etc.) to prevent command injection.
- **Path Traversal Sanitization:** Downloaded filenames and destination directories undergo canonicalization and traversal token stripping (`..`, null bytes, Windows reserved device names).
- **Extension Content Security Policy:** Extensions enforce strict `script-src 'self'` CSP with zero inline scripts, remote code loading, or `eval()` execution.

### Known Issues

- Browser extensions must be loaded in Developer Mode (`Load unpacked`) or via local add-on installation until published on the Chrome Web Store and Mozilla Add-ons (AMO).
- 32-bit Windows (x86) is deprecated upstream by libtorrent4j and is not supported.
- Linux distribution packages (.deb / .rpm) require system JRE 21+ or standalone runtime bundle.

### Acknowledgments

- Upstream issue reporters:
  - Subhra Das Gupta ([@subhra74](https://github.com/subhra74)) and community reporters for issues [#1430](https://github.com/subhra74/xdm/issues/1430), [#1426](https://github.com/subhra74/xdm/issues/1426), [#1417](https://github.com/subhra74/xdm/issues/1417), [#1412](https://github.com/subhra74/xdm/issues/1412), [#1409](https://github.com/subhra74/xdm/issues/1409), [#1406](https://github.com/subhra74/xdm/issues/1406), [#1383](https://github.com/subhra74/xdm/issues/1383), [#1395](https://github.com/subhra74/xdm/issues/1395), [#1349](https://github.com/subhra74/xdm/issues/1349), [#1372](https://github.com/subhra74/xdm/issues/1372), [#1391](https://github.com/subhra74/xdm/issues/1391).
- Contributor Abhishek Mallav ([@abhishekmallav](https://github.com/abhishekmallav)) for the browser extension UI refresh concept inspiration (PR reference for #1372).
- Nullsoft Scriptable Install System (NSIS) project team for the Windows installer framework.
- FormDev Software GmbH for the FlatLaf theme engine.
- BellSoft for the Liberica JDK 25 runtime.

---

## [1.0.0-beta.1] - 2026-11-08

This is the first independent beta release of XDM Enhanced, forked from Xtreme Download Manager (XDM) 9.

### Added

- **Native BitTorrent and Magnet Engine (`xdm-torrent` module):**
  - Integrated `libtorrent4j` (v2.1.0-35) wrapping native `libtorrent` (Rasterbar 2.0.x / 1.2.x).
  - Background metadata retrieval for `magnet:?` URIs.
  - Pre-download multi-file inspection dialog with tree navigation and file selection/deselection.
  - Per-file priority controls (*Do Not Download*, *Normal*, *High*, *Maximum*).
  - Tracker management supporting UDP and HTTP trackers, tier inspection, status reporting, and manual reannounce.
  - Peer discovery via Distributed Hash Table (DHT), Peer Exchange (PEX), and Local Service Discovery (LSD).
  - Real-time peer connection statistics (IP address, client identification, TCP/uTP connection type, flags, live upload/download speed).
  - Transfer queue management with active torrent limits and queue position adjustments (*Top*, *Up*, *Down*, *Bottom*).
  - Configurable seeding modes (*Never*, *Unlimited*, *Ratio/Time*) with automatic termination based on share ratio or duration thresholds.
  - Full compliance with BEP 27 private torrent restrictions (disabling DHT/PEX/LSD on private infohashes).
  - Fastresume persistence (`task-$id.resume`) and crash recovery state storage in `TaskInfoDB`.
- **Traditional Desktop Interface:**
  - 7-category navigation sidebar with live download counters (*All Downloads*, *Compressed*, *Documents*, *Music*, *Programs*, *Video*, *Torrents*).
  - High-density customized toolbar with quick actions (*Add URL*, *Add Magnet*, *Add Torrent*, *Resume*, *Stop*, *Delete*, *Settings*, *Schedule*).
  - Sortable data table with FlatLaf progress bars, status badges, contextual right-click actions, and destination shortcuts.
  - Real-time disk storage status bar indicator showing free and total volume capacity.
  - Dedicated `NewTorrentDownloadWindow` creation dialog.
  - Nonblocking `TorrentDetailsDialog` with tabs for *General*, *Files* (with per-piece completion visualization), *Trackers*, and *Peers*.
- **System and Browser Integration:**
  - Windows Registry file association for `.torrent` files (`HKCU\Software\Classes\.torrent`).
  - Windows Registry protocol handler registration for `magnet:` links (`HKCU\Software\Classes\magnet`).
  - Single-instance IPC socket handoff forwarding command-line arguments to the running application instance.
  - Browser extension handoff endpoints (`/add-torrent`, `/add-magnet`) over local loopback (`127.0.0.1:8597`).
- **Dynamic Theming and Appearance Engine (`ThemeManager`):**
  - Instant theme switching without application restart across all windows, dialogs, menus, and popups.
  - Three appearance modes: `LIGHT`, `DARK`, and `SYSTEM` (with live OS tracking and documented fallback).
  - Background daemon watcher for real-time synchronization with OS light/dark mode toggles on Windows, macOS, and Linux.
  - User-customizable accent colors with 6 curated presets (Blue `#3B82F6`, Cyan `#06B6D4`, Green `#10B981`, Amber `#F59E0B`, Purple `#8B5CF6`, Rose `#F43F5E`), custom hex/color picker, and reset-to-default.
  - Persistent accent color configuration storage in `AppConfig`.
  - Visual accent color swatch selector integrated into Settings > Appearance (`AccentChooserPanel`).

### Changed

- Established project identity as **XDM Enhanced** (`v1.0.0-beta.1`).
- Preserved original upstream documentation in `README.UPSTREAM.md`.
- Authored professional, unadorned technical `README.md` for the fork.
- Updated About dialog to identify `XDM Enhanced 1.0.0-beta.1` while preserving upstream copyright attribution.
- Cleaned up competitor branding and acronym references across source code, UI strings, and documentation in favor of neutral, professional desktop terminology.
- Designed original modern geometric icon merging an accelerated download arrow with a stylized "X" on a deep midnight squircle, generating high-resolution PNGs (16–512px), macOS tray template glyphs, browser extension icons, and multi-resolution Windows `xdm.ico`.
- Unified application name to **XDM Enhanced** across the main window, About dialog, system tray, notification popups, native taskbars, application menus, desktop entries, error dialogs, and packaging metadata.
- Isolated application state directory (`xdm-enhanced`) from official upstream XDM installations, providing automatic zero-data-loss migration of existing user configurations and databases without altering original files.
- Updated AutoStart registry keys and launcher display names to `XDM Enhanced` to prevent collisions with official XDM installations.
- Updated packaging scripts (`packaging/build-bundle.ps1` and `packaging/build-bundle.sh`) to preserve native `libtorrent4j` binaries (`.dll`, `.so`, `.dylib`) during foreign library stripping.

### Fixed

- **Theming & UI Contrast:**
  - Resolved low-contrast disk capacity text in `DownloadStatusBar` (`lblDisk.foreground = UIManager.getColor("Label.disabledForeground")`) and unified status bar label foregrounds to match active theme palettes.
  - Corrected low-contrast text on light surfaces in `DownloadTableRenderers` with WCAG AA compliant colors for download status badges across both light and dark themes.
  - Eliminated static hardcoded component backgrounds and borders across custom views (`DownloadTableView`, `CategorySidebar`, `AppToolBar`, `ModernDownloadTable`) by establishing dynamic `updateTheme()` lifecycle notifications.
  - Removed application restart requirement for appearance mode and accent color changes in Settings > Appearance, applying updates instantaneously across all active windows and dialogs.
- **Windows Installer Conflict & Upstream Isolation:**
  - Assigned dedicated UpgradeCode (`C6A8025B-6D3E-45E9-9BD1-314644B272C1`) in `packaging/windows/xdm.wxs` preventing accidental upgrade collision with or uninstallation of official upstream Xtreme Download Manager.
  - Isolated installation path (`%ProgramFiles%\XDM Enhanced`), registry keys (`Software\XDM Enhanced`), autostart run values, and Start Menu/Desktop shortcuts.
  - Removed destructive upstream custom action that uninstalled the official Microsoft Store package of XDM 8 (`56617subhra74.XtremeDownloadManager-XDM`).
  - Synchronized launcher binary metadata (`ProductName=XDM Enhanced`) in `packaging/windows/resources/xdm-app.properties`.
- **Scheduler Torrent Handoff:** Resolved silent failure in `DownloadScheduler` by adding type-safe routing for torrent tasks via `TorrentDownloadManager`.
- **Shutdown Fastresume Loss:** Added JVM shutdown hook ensuring active torrent tasks synchronously flush fastresume data to disk before process exit.
- **Uninitialized Property Safety:** Fixed `UninitializedPropertyAccessException` in `AppContext.torrentManager` when `taskInfoDB` has not been pre-initialized.
- **Corrupted Fastresume Loops:** Added automated cleanup of rejected `.resume` files on libtorrent `FastresumeRejectedAlert` to allow clean re-hashing from disk.

### Improved

- **Input Boundaries:** Enforced a 50 MB upper cap on `.torrent` file and byte array parsing to prevent heap exhaustion denial-of-service.
- **Path Traversal Sanitization:** Implemented `sanitizeTorrentFilePath` to strip directory traversals (`..`), Windows drive letters (`C:\`), control characters, and reserved DOS device names (`CON`, `PRN`, `AUX`, `NUL`, etc.).
- **Safe Disk Deletion:** Implemented `deleteFilesSafelyFromDisk` with strict canonical path verification preventing deletions outside the target download folder or deletion of the download folder itself.

### Known Issues

- 32-bit Windows (x86) is deprecated upstream by libtorrent4j and is not supported.
- Windows `.msi` installer generation requires the WiX toolset on the build machine; the standalone portable `app-image` runtime bundle is the supported binary distribution when WiX is unavailable.
- Pure IPv6-only tracker environments without DHT require dual-stack connectivity.

### Acknowledgments

- Original Xtreme Download Manager (XDM) codebase and architecture created by Subhra Das Gupta ([subhra74](https://github.com/subhra74)) and upstream contributors.
- `libtorrent4j` wrapper created by Alden Torres and the FrostWire team.
- `libtorrent` (Rasterbar) BitTorrent library developed by Arvid Norberg.
- `FlatLaf` look and feel developed by FormDev Software GmbH.

