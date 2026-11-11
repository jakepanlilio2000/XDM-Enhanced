# Developer Portal & Architecture Overview

Welcome to the **XDM Enhanced** developer documentation. This portal provides architectural blueprints, API specifications, and compilation guides for contributors and third-party extension developers.

---

## 1. Modular Codebase Architecture

The project is structured as a multi-module Maven project adhering to strict architectural boundaries:

```
XDM-Enhanced/
├── xdm-plugin-api/       # Public Java SPI contracts (Zero internal dependencies)
├── xdm-core/             # Segmented transfer engine, socket pooling, hash utilities
├── xdm-manager/          # Queue scheduler, bandwidth throttle, category rules
├── xdm-app/              # Swing GUI, FlatLaf skin engine, task adapter bridge
├── browser-extension/    # Companion browser extensions (Chromium MV3 & Firefox MV2)
├── sample-skins/         # Reference .xdmskin theme packages (Cyber Neon)
└── packaging/            # NSIS installer, WiX MSI, Linux NFPM, AppCDS classlists
```

### Module Responsibilities

| Submodule | Technology | Primary Responsibility |
|:---|:---:|:---|
| **`xdm-plugin-api`** | Java 8+ | Standalone public API published as `xdm-plugin-api.jar`. Declares `PluginService`, `PluginContext`, `PluginEventBus`, and capability annotations. |
| **`xdm-core`** | Kotlin / Java | High-throughput byte streaming, HTTP/HTTPS client with keep-alive pooling, direct block pre-allocation, and SHA-256 verifiers. |
| **`xdm-manager`** | Kotlin | Download queue manager, category classification rules, retry schedulers, and global rate limiters. |
| **`xdm-app`** | Kotlin / Swing | Desktop user interface, FlatLaf theming, `.xdmskin` archive parser, `PluginDownloadBridge` task adapter, and system tray integration. |

---

## 2. Core Extension Points

XDM Enhanced provides two distinct extension mechanisms:

<div class="feature-grid">
  <div class="card-box">
    <h3><a href="/developer/plugins">1. Decoupled Java Plugins (SPI v2)</a></h3>
    <p>Add new download protocols, third-party extractor bridges (e.g. yt-dlp), or checksum verifiers. Compiled against <code>xdm-plugin-api.jar</code> without touching application internals.</p>
  </div>

  <div class="card-box">
    <h3><a href="/developer/skins">2. Declarative Skin Packages (.xdmskin)</a></h3>
    <p>Customize the visual presentation of XDM Enhanced. Package custom window wallpapers, FlatLaf palette overrides, custom category SVGs, and modal backdrops into portable ZIP archives.</p>
  </div>
</div>

---

## 3. Technology Stack & Toolchains

- **Compiler & Runtime:** Liberica JDK 25 (Java 25 target for desktop application; Java 8 target for plugin API).
- **Build System:** Apache Maven 3.9.9 (`mvn clean package`).
- **UI Framework:** Java Swing with FlatLaf modern styling and SVG rendering (`flatlaf-extras`).
- **BitTorrent Engine:** `libtorrent4j` wrapping Rasterbar `libtorrent` 2.x native binaries.
- **Packaging:** Nullsoft Scriptable Install System (NSIS 3.x), WiX Toolset 3.14, and NFPM.

---

## 4. Next Steps & External References

- [Explore the Plugin API v2 Guide](/developer/plugins)
- [Learn the Skin Customization System](/developer/skins)
- [Build XDM Enhanced from Source](/developer/building)
- [Contributing Guidelines & DCO Workflow](/contribute)
- [Authoritative Source Specifications (`docs/`) ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/tree/master/docs)
- [Reference Plugins in Repository (`sample-plugins/`) ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/tree/master/sample-plugins)
- [Reference Skins in Repository (`sample-skins/`) ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/tree/master/sample-skins)
- [Community Wiki Developer Section ↗](https://github.com/jakepanlilio2000/XDM-Enhanced/wiki#developer-platform-documentation)

