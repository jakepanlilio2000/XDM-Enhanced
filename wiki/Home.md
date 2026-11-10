# Welcome to the XDM Enhanced Wiki

**XDM Enhanced** is an independently maintained, open-source desktop download manager forked from [Xtreme Download Manager (XDM)](https://github.com/subhra74/xdm). It provides dynamic multi-segment HTTP/HTTPS download acceleration, native BitTorrent 2.0 swarm handling, modern FlatLaf dynamic theming, custom `.xdmskin` theme archives, a decoupled Java plugin platform (SPI v2), and companion browser extensions.

> **Release Channel Notice:** XDM Enhanced is currently in its active beta cycle on the **Pre-release Channel (`v1.0.0-beta.6`)**. There are currently no older stable v1.0.0 releases. Please consult the [[Release Channels and Updates|Release-Channels-and-Updates]] guide for binary verification and release notes.

---

## Quick Start Matrix

| I want to... | Recommended Guide | Key Steps |
|:---|:---|:---|
| **Install XDM Enhanced** | [Installation Guide](Installation-Guide) | Download Windows Setup `.exe`, Portable `.zip`, or Universal `.jar`. |
| **Download my first file** | [First Download Guide](First-Download-Guide) | Paste a URL or use automatic clipboard link capture. |
| **Integrate with my browser** | [Browser Extension Setup](Browser-Extension-Setup) | Install Chromium MV3 or Firefox MV2 add-ons. |
| **Fix a disconnected extension** | [Browser Extension Disconnected](Browser-Extension-Disconnected) | Run the desktop self-repair utility and verify loopback port 8597. |
| **Download torrents / magnets** | [BitTorrent & Magnet Guide](BitTorrent-and-Magnet-Guide) | Add magnet links, filter files in tree, and recheck piece integrity. |
| **Customize theme & wallpapers**| [Appearance & Skins Guide](Appearance-and-Skins-Guide) | Install `.xdmskin` themes, adjust window wallpapers, and set dark mode. |
| **Install plugins (yt-dlp)** | [Plugin Installation Guide](Plugin-Installation-Guide) | Install `.xdmplugin` add-ons and configure external media extractors. |
| **Build from source / develop** | [Building from Source](Building-from-Source) | Set up Liberica JDK 25 and Maven 3.9+ to compile the modular codebase. |

---

## User Documentation Index

- **Setup & Getting Started:**
  - [[Installation Guide|Installation-Guide]]: Platform prerequisites, NSIS installer, portable bundle, universal JAR.
  - [[First Download Guide|First-Download-Guide]]: Starting segmented downloads, queue controls, pause/resume.
  - [[Browser Extension Setup|Browser-Extension-Setup]]: Installing extensions for Chrome, Edge, Brave, and Firefox.
  - [[Clipboard Monitoring & Privacy|Clipboard-Monitoring-and-Privacy]]: Opt-in clipboard capture, zero-payload guarantee, credential protection.
- **Core Download Features:**
  - [[HTTP & HTTPS Transfers|HTTP-and-HTTPS-Transfers]]: Dynamic segmentation (up to 32 streams), direct block allocation, link refresh.
  - [[Queues & Categories|Queues-and-Categories]]: Automated file sorting, queue schedules, bandwidth throttling.
  - [[BitTorrent & Magnet Guide|BitTorrent-and-Magnet-Guide]]: BitTorrent 2.0, DHT/PEX, piece diagnostics, BEP 27 private flag handling.
  - [[Torrent Data Recheck|Torrent-Data-Recheck]]: Re-hashing existing local data to resume external or corrupted torrents.
- **Customization & Add-ons:**
  - [[Appearance & Skins Guide|Appearance-and-Skins-Guide]]: Custom wallpapers, FlatLaf theme swatches, Original XDM preset.
  - [[Plugin Installation Guide|Plugin-Installation-Guide]]: Installing third-party add-ons, configuring yt-dlp.
- **Support & Upgrades:**
  - [[Release Channels & Checksums|Release-Channels-and-Updates]]: Verifying SHA-256 hashes, release channel structure.
  - [[Frequently Asked Questions|Frequently-Asked-Questions]]: DRM limitations, multi-instance lock, common queries.

---

## Troubleshooting & Diagnostics

- [[Browser Extension Disconnected|Browser-Extension-Disconnected]]: Dedicated 4-step checklist for recovering connection between extension and desktop.
- [[Troubleshooting by Symptom|Troubleshooting-by-Symptom]]: Interactive decision tree covering stuck downloads, missing sizes, native host errors, and plugin visibility.

---

## Developer Platform Documentation

- [[Architecture Overview|Architecture-Overview]]: Multi-module system layout (`xdm-core`, `xdm-manager`, `xdm-app`, `xdm-plugin-api`).
- [[Building from Source|Building-from-Source]]: Complete developer build and test instructions.
- [[Skin Developer Quick Start|Skin-Developer-Quick-Start]] & [[Skin Schema & UI Slots|Skin-Schema-and-UI-Slots]]: Authoring declarative theme packages.
- [[Plugin SDK Quick Start|Plugin-SDK-Quick-Start]]: Writing independent plugins against `xdm-plugin-api`.
- [[Plugin API Version Compatibility|Plugin-API-Version-Compatibility]]: Semantic version range matching (`[1.0.0, 2.0.0)`).
- [[Provider Task Integration|Provider-Task-Integration]]: Submitting third-party downloads to the central scheduler.
- [[Browser Integration & Native Messaging|Browser-Integration-and-Native-Messaging]]: Technical specifications for the local loopback bridge.
- [[Developer Troubleshooting|Developer-Troubleshooting]]: Debugging thread deadlocks, memory leaks, and classloaders.
- [[Contributing & Security|Contributing-and-Security]]: Code contribution guidelines and responsible vulnerability disclosure.

---

## Upstream Lineage & Attribution

XDM Enhanced is an independent fork of [Xtreme Download Manager (XDM)](https://github.com/subhra74/xdm), created and maintained by **Subhra Das Gupta (`subhra74`)**. Original credits, architectural heritage, and copyright notices are preserved in [README.UPSTREAM.md](https://github.com/jakepanlilio2000/XDM-Enhanced/blob/master/README.UPSTREAM.md).

---

## Project Portals & Repository Links

- **Official Website:** [https://jakepanlilio2000.github.io/XDM-Enhanced/](https://jakepanlilio2000.github.io/XDM-Enhanced/)
- **Latest Pre-release:** [GitHub Releases v1.0.0-beta.6](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.0.0-beta.6)
- **Source Code Repository:** [https://github.com/jakepanlilio2000/XDM-Enhanced](https://github.com/jakepanlilio2000/XDM-Enhanced)
- **Authoritative Technical Specs:** [docs/ Specifications Directory](https://github.com/jakepanlilio2000/XDM-Enhanced/tree/master/docs)
- **Reference Samples in Repo:** [sample-plugins/](https://github.com/jakepanlilio2000/XDM-Enhanced/tree/master/sample-plugins) and [sample-skins/](https://github.com/jakepanlilio2000/XDM-Enhanced/tree/master/sample-skins)
- **Issue Tracker & Discussions:** [GitHub Issues](https://github.com/jakepanlilio2000/XDM-Enhanced/issues)

