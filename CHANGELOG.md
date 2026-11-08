# Changelog

All notable changes to the XDM Enhanced project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
- **IDM-Inspired Desktop Interface:**
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

### Changed

- Established project identity as **XDM Enhanced** (`v1.0.0-beta.1`).
- Preserved original upstream documentation in `README.UPSTREAM.md`.
- Authored professional, unadorned technical `README.md` for the fork.
- Updated About dialog to identify `XDM Enhanced 1.0.0-beta.1` while preserving upstream copyright attribution.
- Updated packaging scripts (`packaging/build-bundle.ps1` and `packaging/build-bundle.sh`) to preserve native `libtorrent4j` binaries (`.dll`, `.so`, `.dylib`) during foreign library stripping.

### Fixed

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

