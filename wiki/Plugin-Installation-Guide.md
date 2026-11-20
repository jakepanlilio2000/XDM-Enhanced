# Plugin Installation & Management Guide

XDM Enhanced features an extensible Java Plugin Platform (SPI v2) that allows developers and users to add third-party download extractors, checksum tools, and custom protocol handlers.

---

## 1. Official Sample Plugins

The following official sample plugins are provided in the source repository and release assets:

| Plugin Name | Package Filename | Capabilities | Description |
|:---|:---|:---:|:---|
| **yt-dlp Downloader** | `ytdlp-downloader.xdmplugin` | `DOWNLOAD_PROVIDER`, `URL_RECOGNITION`, `SETTINGS_PANEL` | Integrates authorized `yt-dlp` media extraction with visible progress and pause/resume. |
| **Proxy Profile Provider** | `proxy-provider.xdmplugin` | `NETWORK_PROXY`, `SETTINGS_PANEL` | Demonstrates scoped SOCKS5/HTTP proxy routing with fail-closed security. |
| **VPN Telemetry Client** | `vpn-status.xdmplugin` | `VPN_STATUS`, `SETTINGS_PANEL` | Demonstrates non-privileged VPN status discovery, profiles, and download pause policies. |
| **UI & Alerts Enhancement** | `ui-notification.xdmplugin` | `CONTEXT_MENU`, `TOOLBAR`, `STATUS_INDICATOR`, `DETAILS_SECTION`, `SETTINGS_PANEL` | Modular UI actions, status pills, details tabs, theme observation, and desktop alerts. |
| **Checksum Verifier** | `checksum-verifier.xdmplugin` | `POST_DOWNLOAD_PROCESSOR`, `SETTINGS_PANEL` | Automatically calculates MD5, SHA-1, and SHA-256 hashes upon download completion. |

---

## 2. Installing a Plugin (`.xdmplugin`)

1. Open XDM Enhanced &rarr; **Settings** (⚙️) &rarr; select **Plugins**.
2. Click **Install from File...** (`Alt+I`) &rarr; select your downloaded `.xdmplugin` or `.jar` archive.
3. Review the **Security & Trust Disclosure** dialog:
   - Name, version, author, declared license, source path, and calculated SHA-256 hash.
   - Declared capabilities and external tool touchpoints.
   - In-process execution privilege notice.
4. Click **Trust & Enable** to activate the plugin.

---

## 3. Configuring and Managing Plugins

- **Configure:** Click **Configure** on any active plugin card to modify its settings.
- **View Logs:** Click **View Logs** to inspect registered extension points, classloader status, and sanitized diagnostic records.
- **Export Diagnostics:** Click **Export Diagnostics...** (`Alt+E`) in the header to preview and save a secret-free troubleshooting report.
- **Capabilities Guide:** Click **Capabilities Guide** (`Alt+H`) in the header to view tool, OS, consent, and restart requirements.
- **Enable / Disable / Retry:** Toggle plugin states asynchronously without freezing the user interface.
- **Remove:** Uninstalls third-party plugins with optional data preservation.

---

## 4. Security & Troubleshooting Protections

- **In-Process Boundary:** Manifest permissions alone do not provide JVM sandboxing. Plugins execute with user account privileges.
- **Archive Hardening:** Defends against Zip Slip traversal, symbolic links, native binaries, and zip bombs.
- **Safe Mode Recovery:** Launch `xdman --safe-mode` or `xdman --no-plugins` to quarantine all plugins if a rogue add-on prevents startup.

---

## Related Articles

* [[Plugin SDK Quick Start|Plugin-SDK-Quick-Start]]: Develop your own plugins using `xdm-plugin-api`.
* [[Provider Task Integration|Provider-Task-Integration]]: Submitting tasks to the central scheduler.
* [[Troubleshooting by Symptom|Troubleshooting-by-Symptom]]: Diagnosing plugin load failures.

