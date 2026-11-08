# XDM Enhanced

An independent, open-source fork of Xtreme Download Manager.

---

## 1. Project Overview

XDM Enhanced is an independent fork of Xtreme Download Manager (XDM). It extends XDM's multi-connection segmented HTTP/HTTPS download engine and streaming video grabber with native BitTorrent and magnet-link downloading capabilities, presented in an Internet Download Manager (IDM)-inspired desktop user interface.

This fork is not an official upstream release. The original upstream project documentation is preserved in [README.UPSTREAM.md](README.UPSTREAM.md).

## 2. Key Features

- **Segmented HTTP/HTTPS Downloads:** Multi-connection segmented downloading directly into the destination file without a separate temporary copy or slow disk assembly step.
- **Stream Capture:** Detection and extraction of HLS (`.m3u8`) and MPEG-DASH (`.mpd`) video streams, muxing directly to MP4 without external FFmpeg dependencies. Subtitle extraction to timed `.srt` format.
- **Native BitTorrent Engine:** BitTorrent 1.2 and 2.0 protocol support powered by `libtorrent4j` (v2.1.0-35) wrapping native `libtorrent` (Rasterbar).
- **Magnet Links and Torrent Files:** Native ingestion of `magnet:?` URIs and `.torrent` files with background metadata resolution.
- **Selective Downloading and File Priorities:** Pre-download file inspection dialog with file selection trees, individual file exclusion, and priority settings (Do Not Download, Normal, High, Maximum).
- **IDM-Inspired Desktop Interface:** 7-category sidebar filter (All Downloads, Compressed, Documents, Music, Programs, Video, Torrents), high-density toolbar, customizable data table with progress indicators, status bar storage meter, and separate nonblocking progress dialogs.
- **Transfer Management:** Global and per-task download and upload bandwidth limits, active torrent concurrency limits, and queue position management (Top, Up, Down, Bottom).
- **Seeding Rules:** Automated seeding termination based on target share ratio (e.g. 1.0x) or elapsed seeding duration.
- **Tracker and Peer Controls:** Distributed Hash Table (DHT), Peer Exchange (PEX), and Local Service Discovery (LSD) support. Manual tracker additions, force reannounce, and live peer transfer inspection. Full compliance with BEP 27 private torrent restrictions.
- **Windows System Integration:** Windows Registry associations for `.torrent` files and `magnet:` URI protocol handling, with single-instance socket argument forwarding.
- **Security Protections:** 50 MB upper cap on torrent metadata parsing, path sanitization against directory traversal (`..`) and Windows reserved device names, and canonical path checks preventing unintended file deletion.

## 3. Current Development Status

XDM Enhanced is in active development and preparing for its initial beta release (`v1.0.0-beta.1`). All core download pipelines, BitTorrent operations, IDM UI components, and settings persistence layers have been independently verified through automated regression suites.

## 4. Supported Operating Systems

| Operating System | Architecture | BitTorrent Engine | Status |
|---|---|---|---|
| **Windows 10 / 11** | `x64` (`amd64`) | `libtorrent4j.dll` | Fully Verified (Primary Target) |
| **Linux** | `x64` (`amd64`) | `liblibtorrent4j.so` | Supported (Libraries Bundled) |
| **macOS** | `x64` & `arm64` | `liblibtorrent4j.dylib` | Supported (Libraries Bundled) |

*Note: 32-bit Windows (x86) is deprecated upstream by libtorrent4j and is not supported.*

## 5. Requirements

### Building from Source
- **Java Development Kit (JDK):** JDK 25 (e.g., BellSoft Liberica JDK 25 or OpenJDK 25).
- **Build Tool:** Apache Maven 3.9 or higher.

### Running Pre-Built Packages
- **Standalone Windows Bundle:** No external Java installation required (bundled runtime image included).
- **Universal JAR (`xdm-app.jar`):** Java Runtime Environment (JRE) 25 (64-bit).

## 6. Building from Source

To compile all modules and produce the universal application JAR:

```bash
# Clone the repository
git clone https://github.com/jakepanlilio2000/XDM-Enhanced.git
cd XDM-Enhanced

# Build all modules and package the universal JAR
mvn clean package -DskipTests
```

The compiled fat JAR is output to:
`xdm-app/target/xdm-app.jar`

To run the complete automated test suite across all modules:

```bash
mvn test -DskipTests=false
```

### Windows Runtime Packaging (Optional)

To produce the standalone Windows x64 runtime bundle (`app-image`) containing a trimmed JRE and native launcher (`xdm-app.exe`):

```powershell
powershell -ExecutionPolicy Bypass -File packaging\build-bundle.ps1 -Type app-image -Arch x64 -SkipBuild
```

The standalone directory is output to:
`build\dist\windows-x64\xdm-app\`

## 7. Running the Application

### Universal JAR
```bash
java -jar xdm-app/target/xdm-app.jar
```

### Standalone Windows Bundle
Execute `xdm-app.exe` from the unpacked distribution folder:
```cmd
build\dist\windows-x64\xdm-app\xdm-app.exe
```

### Command-Line Headless Mode
```bash
java -jar xdm-app/target/xdm-app.jar --headless -o /path/to/downloads https://example.com/file.zip
```

## 8. Browser Integration

XDM Enhanced runs an internal HTTP communication endpoint bound strictly to `127.0.0.1:8597`. It integrates with major web browsers using companion extensions:

- **Chrome, Edge, Brave, Opera, Vivaldi:** Manifest V3 extension located in `browser-extension/chrome-extension/`.
- **Firefox:** Manifest V2 extension located in `browser-extension/firefox-extension/`.

The extension intercepts downloadable media and transfers, passing download URLs and magnet links directly to XDM Enhanced. The integration endpoint enforces DNS-rebinding protection and strictly verifies extension origins (`chrome-extension://` and `moz-extension://`).

## 9. Release Downloads

Pre-built binaries and release notes are available on the GitHub repository releases page:

https://github.com/jakepanlilio2000/XDM-Enhanced/releases

Supported release artifacts include:
1. **Standalone Windows x64 Bundle:** A portable directory including `xdm-app.exe` and a self-contained JRE 25 runtime.
2. **Universal JAR (`xdm-app.jar`):** A single runnable JAR compatible with any 64-bit operating system with JRE 25 installed.

## 10. Known Limitations

- **32-Bit Systems:** 32-bit x86 architectures are not supported due to native BitTorrent library requirements.
- **Windows MSI Generation:** Building WiX-based `.msi` installers requires the WiX toolset installed on the build host. The standalone `app-image` bundle provides equivalent portable installation without WiX.
- **IPv6 Trackers:** Environments using pure IPv6-only trackers without DHT require dual-stack connectivity.

## 11. Original Project Acknowledgment

XDM Enhanced is an independent fork of Xtreme Download Manager, originally developed by the XDM contributors.

- **Upstream Repository:** https://github.com/subhra74/xdm
- **Original Upstream Documentation:** [README.UPSTREAM.md](README.UPSTREAM.md)
- **Original Project Homepage:** https://xtremedownloadmanager.com/

This fork is an independent community development and is not endorsed by or affiliated with the upstream XDM project maintainers.

## 12. Licensing

XDM Enhanced is free open-source software licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file for complete license terms.

- Original XDM Codebase: Copyright © 2013–2026 Subhra Das Gupta and upstream contributors.
- XDM Enhanced Additions: Copyright © 2026 XDM Enhanced Contributors.

### Third-Party Software Notices
- **libtorrent4j:** MIT License (Copyright © Alden Torres)
- **libtorrent (Rasterbar):** BSD 3-Clause License (Copyright © Arvid Norberg)
- **FlatLaf:** Apache License 2.0 (Copyright © FormDev Software GmbH)
- **KotlinX Serialization:** Apache License 2.0 (Copyright © JetBrains s.r.o.)

See `xdm-torrent/NOTICE` and `xdm-core/NOTICE` for additional component notices.
