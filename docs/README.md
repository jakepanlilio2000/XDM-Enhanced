# XDM Enhanced — Technical Documentation Portal

Welcome to the internal technical documentation directory of **XDM Enhanced**. This directory contains the authoritative, source-controlled specifications and developer references for the project.

---

## 📚 Authoritative Specifications

| Document | Scope | Description |
| :--- | :---: | :--- |
| [**Skin Development Specification**](SKIN_DEVELOPMENT.md) | Theming | Complete JSON schema, UI component slots, FlatLaf color keys, wallpaper scaling rules, and `.xdmskin` packaging instructions. |
| [**Plugin Development Guide (SDK v2)**](PLUGIN_DEVELOPMENT.md) | Extensibility | Decoupled Java SPI architecture, lifecycle states, manifest schema, event bus contracts, UI, proxy, VPN, and isolated ClassLoader architecture. |
| [**Plugin API Reference Specification**](PLUGIN_API_REFERENCE.md) | Extensibility | Complete versioned public API signatures, models, and interfaces for SDK v2. |
| [**Plugin Manager User Guide**](PLUGIN_MANAGER_USER_GUIDE.md) | User Guide | Instructions for installing, enabling, configuring, viewing logs, and exporting secret-free diagnostics. |
| [**Plugin v1 to v2 Migration Guide**](PLUGIN_V1_TO_V2_MIGRATION_GUIDE.md) | Migration | Step-by-step roadmap and compatibility policies for upgrading legacy v1 plugins to SDK v2. |
| [**Plugin Security & Trust Model**](PLUGIN_SECURITY_AND_TRUST_MODEL.md) | Security | Transparent boundary disclosures, user approval workflows, and archive hardening defenses. |
| [**Storage & Migration Specification**](STORAGE_AND_MIGRATION.md) | Storage | Profile layout, binary tag schemas, atomic transactions, forward/backward compatibility, safe migrations, and credential protection. |
| [**Browser Connection Troubleshooting**](BROWSER_CONNECTION_TROUBLESHOOTING.md) | Diagnostics | Step-by-step resolution for native messaging disconnects, loopback port 8597 bindings, firewall permissions, and manifest verification. |
| [**Architectural Rationale**](why-a-download-manager.md) | Architecture | Detailed breakdown of multi-connection dynamic segmentation, disk assembly, and stream capture vs. standard browser downloads. |

---

## 🛠️ Reference Implementations in Repository

Working examples are included directly in the source tree to accelerate your development:

- **Reference Skin Package:** [`sample-skins/cyber-neon.xdmskin`](../sample-skins/cyber-neon.xdmskin) — Demonstrates dark theme swatches, custom title bar, rounded card borders, and custom category icons.
- **Reference Checksum Plugin:** [`sample-plugins/checksum-verifier.xdmplugin`](../sample-plugins/checksum-verifier.xdmplugin) — Demonstrates post-download file integrity verification (MD5, SHA-1, SHA-256) via event bus hooks.
- **Reference yt-dlp Download Provider:** [`sample-plugins/ytdlp-downloader.xdmplugin`](../sample-plugins/ytdlp-downloader.xdmplugin) — Demonstrates external CLI integration, process argument hardening, and custom download provider registration with visible tasks.
- **Reference Proxy Profile Provider:** [`sample-plugins/proxy-provider.xdmplugin`](../sample-plugins/proxy-provider.xdmplugin) — Demonstrates scoped SOCKS5/HTTP proxy resolution with fail-closed policies and credential redaction.
- **Reference VPN Telemetry Client:** [`sample-plugins/vpn-status.xdmplugin`](../sample-plugins/vpn-status.xdmplugin) — Demonstrates non-privileged external VPN client discovery, profiles, and state listeners.
- **Reference UI & Alerts Enhancement:** [`sample-plugins/ui-notification.xdmplugin`](../sample-plugins/ui-notification.xdmplugin) — Demonstrates context menus, toolbar buttons, status bar pills, details tabs, theme observation, and desktop notifications.

---

## 🌐 Project Navigation & Resources

- **Official Project Website:** [https://jakepanlilio2000.github.io/XDM-Enhanced/](https://jakepanlilio2000.github.io/XDM-Enhanced/) *(Pending deployment; source in [`website/`](../website/))*
- **Community Wiki:** [https://github.com/jakepanlilio2000/XDM-Enhanced/wiki](https://github.com/jakepanlilio2000/XDM-Enhanced/wiki) *(Staged in [`wiki/`](../wiki/))*
- **Latest Stable Release:** [GitHub Releases v1.0.2](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.0.2)
- **Contribution Guidelines:** [CONTRIBUTING.md](../CONTRIBUTING.md)
- **Issue & Bug Tracker:** [GitHub Issues](https://github.com/jakepanlilio2000/XDM-Enhanced/issues)
- **Upstream Attribution:** [README.UPSTREAM.md](../README.UPSTREAM.md)
