# XDM Enhanced — Technical Documentation Portal

Welcome to the internal technical documentation directory of **XDM Enhanced**. This directory contains the authoritative, source-controlled specifications and developer references for the project.

---

## 📚 Authoritative Specifications

| Document | Scope | Description |
| :--- | :---: | :--- |
| [**Skin Development Specification**](SKIN_DEVELOPMENT.md) | Theming | Complete JSON schema, UI component slots, FlatLaf color keys, wallpaper scaling rules, and `.xdmskin` packaging instructions. |
| [**Plugin Development Specification**](PLUGIN_DEVELOPMENT.md) | Extensibility | Decoupled Java SPI architecture, lifecycle states, manifest schema, event bus contracts, and sandboxed ClassLoader isolation. |
| [**Browser Connection Troubleshooting**](BROWSER_CONNECTION_TROUBLESHOOTING.md) | Diagnostics | Step-by-step resolution for native messaging disconnects, loopback port 8597 bindings, firewall permissions, and manifest verification. |
| [**Architectural Rationale**](why-a-download-manager.md) | Architecture | Detailed breakdown of multi-connection dynamic segmentation, disk assembly, and stream capture vs. standard browser downloads. |

---

## 🛠️ Reference Implementations in Repository

Working examples are included directly in the source tree to accelerate your development:

- **Reference Skin Package:** [`sample-skins/cyber-neon.xdmskin`](../sample-skins/cyber-neon.xdmskin) — Demonstrates dark theme swatches, custom title bar, rounded card borders, and custom category icons.
- **Reference Checksum Plugin:** [`sample-plugins/checksum-verifier.xdmplugin`](../sample-plugins/checksum-verifier.xdmplugin) — Demonstrates post-download file integrity verification (MD5, SHA-1, SHA-256) via event bus hooks.
- **Reference yt-dlp Plugin:** [`sample-plugins/ytdlp-downloader.xdmplugin`](../sample-plugins/ytdlp-downloader.xdmplugin) — Demonstrates external CLI integration, process argument hardening, and custom download provider registration.

---

## 🌐 Project Navigation & Resources

- **Official Project Website:** [https://jakepanlilio2000.github.io/XDM-Enhanced/](https://jakepanlilio2000.github.io/XDM-Enhanced/) *(Pending deployment; source in [`website/`](../website/))*
- **Community Wiki:** [https://github.com/jakepanlilio2000/XDM-Enhanced/wiki](https://github.com/jakepanlilio2000/XDM-Enhanced/wiki) *(Staged in [`wiki/`](../wiki/))*
- **Latest Pre-release:** [GitHub Releases v1.0.0-beta.6](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.0.0-beta.6)
- **Contribution Guidelines:** [CONTRIBUTING.md](../CONTRIBUTING.md)
- **Issue & Bug Tracker:** [GitHub Issues](https://github.com/jakepanlilio2000/XDM-Enhanced/issues)
- **Upstream Attribution:** [README.UPSTREAM.md](../README.UPSTREAM.md)
