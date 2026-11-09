# Changelog

All notable changes to the XDM Enhanced project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

