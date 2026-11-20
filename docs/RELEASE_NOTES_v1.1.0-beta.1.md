# XDM Enhanced v1.1.0-beta.1 Release Notes

**Release Tag**: `v1.1.0-beta.1`  
**Milestone**: Plugin SDK v2 Beta Preview  
**Release Date**: 2026-11-20  
**Status**: Ready for Authorized Maintainer Release (Dry-Run Staged Locally)

---

## 1. Executive Summary

XDM Enhanced `v1.1.0-beta.1` introduces the **Modular Plugin SDK v2**, a modern, extensible service provider framework that empowers developers to build native download engines, proxy profile providers, VPN client integration listeners, and custom UI views—while preserving complete backward compatibility with all legacy v1 plugins and maintaining an honest, transparent security trust model.

This release represents a backward-compatible minor feature milestone (`v1.1.0-beta.1`) following Semantic Versioning 2.0.0. The public Plugin API specification is tracked separately as specification version `2.0.0` (distributed via `xdm:xdm-plugin-api:1.0.2`).

---

## 2. Key Highlights & New Capabilities

### 2.1 Plugin SDK v2 Architecture
- **Public SPI & Pure Decoupling**: Plugins compile solely against `xdm-plugin-api`, completely isolated from internal application internals (`xdm.app.*`, `xdm.core.*`, `xdm.manager.*`).
- **Extended Service Provider Interfaces**:
  - `PluginDownloadEngine`: Register external or custom download engines (e.g. yt-dlp, aria2, FFmpeg streams) with first-class progress tracking, cancellation, and pause/resume lifecycle.
  - `PluginNetworkManager` & `ProxyProfile`: Programmatically supply authenticated SOCKS5/HTTP/HTTPS proxy configurations.
  - `PluginVpnIntegration`: Provide non-privileged status and connectivity monitoring for WireGuard and OpenVPN clients.
  - `PluginUiContext` & `PluginView`: Contribute custom tab views, side-panel widgets, and desktop toast notifications adhering to active Light, Dark, System, and custom FlatLaf skins.
  - `PluginSettings`: Type-safe configuration persistence with JSON backing and masked credential storage.

### 2.2 Transparent Legacy v1 Compatibility
- **Coexistence via `LegacyPluginAdapter`**: Existing plugins targeting the v1 `xdm.plugin.Plugin` interface load and operate seamlessly without recompilation or manifest modifications.
- **Unified Plugin Manager Registry**: Both v1 and v2 plugins coexist in the unified management UI.

### 2.3 Transparent Security Trust Model & Quarantine
- **Honest Privilege Disclosure**: Transparently informs users that in-process Java plugins execute with the user's operating system privileges. Manifest capability lists inform and request consent, but are not sandboxed at the OS level.
- **Install-Time Quarantine**: Newly installed third-party plugins are staged in a quarantined state (`.disabled`) until explicitly reviewed and enabled by the user in the Plugin Manager.
- **Zip-Slip & Path Traversal Guard**: Plugin archive extractors reject directory traversal, symlink attacks, nested archive bombs, and archives exceeding 50 MB.
- **Subprocess Security**: Subprocess execution adapters mandate executable path whitelisting, argument array escaping (zero shell interpolation), strict timeouts, and automated credential scrubbing (`***`).

### 2.4 Modernized Plugin Manager UI
- **Zero Dead Buttons**: Every button in the Plugin Manager (`Install`, `Enable`, `Disable`, `Configure`, `View Logs`, `Uninstall`, `Diagnostic Export`) is fully wired and functional.
- **Status Badges**: Distinct visual badges for `ACTIVE`, `DISABLED`, `QUARANTINED`, `CRASHED`, and `RECOVERY_MODE`.
- **Diagnostic Export**: One-click diagnostic export bundle generates full troubleshooting logs with all authentication tokens, API keys, and passwords scrubbed.

### 2.5 Automated Circuit Breaker & Safe Mode
- **Fault Isolation**: Dedicated bounded worker pool (`PluginThreadPoolExecutor`) isolates plugin execution from the Swing UI Event Dispatch Thread (EDT) and core download queues.
- **Safe Mode Startup Flag**: Starting the application with `--safe-mode` disables all third-party plugins, allowing users to recover from misbehaving or crashing plugins without data loss.

### 2.6 Verified Standalone Sample Projects
Located in `sample-plugins/`, complete with independent standalone Maven builds:
1. `proxy-provider-sample`: Dynamic SOCKS5/HTTP proxy profile provider with simulated health check.
2. `vpn-status-sample`: Non-privileged VPN client status observer (WireGuard / OpenVPN interface detector).
3. `ui-notification-sample`: FlatLaf-styled UI contribution and desktop notification integration.
4. `ytdlp-downloader-plugin`: Video extraction download engine leveraging yt-dlp with sanitized subprocess execution.

---

## 3. Compatibility Matrix

| Component | Version | Compatibility & Support Notes |
|:---|:---|:---|
| **Host Application** | `1.1.0-beta.1` | Preserves all core download manager capabilities from `1.0.x`. |
| **Plugin API Specification** | `2.0.0` | Public SPI defined in `xdm-plugin-api`. |
| **Plugin API Maven Artifact** | `xdm:xdm-plugin-api:1.0.2` | Clean public jar without internal dependencies. |
| **Java Runtime Environment** | JDK 25+ | Built on Liberica OpenJDK 25.0.4.1_1 with G1 AppCDS pre-mapped. |
| **Chromium Extension** | `3.5.4` (`version_name: 1.1.0-beta.1`) | Manifest V3 native messaging integration. |
| **Firefox Extension** | `1.1.4` | Manifest V2 native messaging integration. |
| **Legacy v1 Plugins** | Fully Supported | Handled automatically via `LegacyPluginAdapter`. |
| **Operating Systems** | Windows 10/11 (x64, ARM64) | Full native installer and portable ZIP packages. |
| | Linux (x64, ARM64) | AppImage / tar.gz via standard runtime bundle. |
| | macOS (x64, Apple Silicon) | Application bundle (.app / dmg). |

---

## 4. Notable Breaking Decisions & API Boundaries

1. **Restricted Direct Classpath Access**:
   - Plugins attempting to directly load or reference `xdm.app.*`, `xdm.core.*`, or `xdm.manager.*` classes are not supported. Plugins must compile exclusively against `xdm:xdm-plugin-api`.
2. **Synchronous Blocking Forbidden**:
   - Blocking calls inside `Plugin.initialize()`, `Plugin.start()`, or `PluginDownloadEngine.prepareDownload()` must complete within the allocated 10-second timeout or they will be terminated by the watchdog.
3. **External Tools Not Bundled**:
   - Plugins requiring third-party CLI binaries (e.g., `yt-dlp`, `ffmpeg`, `wireguard`) must not attempt to install system binaries silently. Users must configure the executable path or rely on system PATH discovery.

---

## 5. Security Disclosures & Known Limitations

- **Java SE Security Limitations**: In-process Java plugins run with the same OS permissions as the user executing XDM Enhanced. Java no longer supports `SecurityManager`. Manifest permissions serve as an informative contract and capability declaration rather than an OS-level sandbox boundary.
- **VPN Integration Scope**: The VPN SPI is an observer/status listener interface only. It does not install kernel TUN/TAP drivers, modify system network routing tables, or intercept socket-level OS traffic.
- **Credential Scrubbing**: While the application scrubs passwords and tokens matching known patterns (`bearer`, `token`, `password`, `key`) from logs and diagnostic exports, developers are advised to use the secure `PluginSettings` API for sensitive storage.

---

## 6. Verification & Test Audit

- **Automated Test Matrix**: 387 automated tests executed and passing (`exit code: 0`).
  - Unit tests: Core engine, download queue, chunking, proxy fallback, native messaging.
  - Integration tests: Plugin manager lifecycle, quarantine transitions, settings persistence.
  - Negative tests: Malformed manifests, zip-slip attacks, missing entry points, timeouts.
  - Visual screenshot tests: Light, Dark, System themes, and Custom FlatLaf skins.
- **Headless Application Verification**:
  ```bash
  java -jar xdm-app-1.1.0-beta.1.jar --headless --help
  # Result: Exit code 0
  ```
- **Sample Plugins Compilation**: All 4 standalone sample plugins compile independently outside the main project tree with `mvn clean package`.

---

## 7. SHA-256 Checksums

The integrity of all release artifacts can be verified using the following SHA-256 checksums:

```text
c8f915c32a984515db8991537089f9324be18ab5c67421d419c5d30a83582806  checksum-verifier.xdmplugin
5e8293da37215dbafff6a41a88495bdefcc0faa3f942c878ada69664ec05ef0f  cyber-neon.xdmskin
fa1eb3e2bc1ef54fe03ba1b3d9c8c8c03fe8585bbcc620ff5c15b6ae8b4d2755  proxy-provider.xdmplugin
d7dace8427aaea5883e4cc3e3e4763bb387754a6676e0c2f7db36b14f5b67a69  ui-notification.xdmplugin
7d94e132f25e76361e6356c8321edddd5d0a3f8132d8492fb578d03c175d95d5  v2-sample-plugin.xdmplugin
4da24225a5137efda49a35dfdfbdef04c8453a38324bd44e90f40ef0b1255f90  vpn-status.xdmplugin
f2487088a007d38d32823d099d5fa69d8d20e8c7b7caaefae5fbdcb962201d86  xdm-app-1.1.0-beta.1.jar
fd0436ccb82d3ccb2a428959dcf7570121d567e964bcf68b03e5f552910f783e  XDM-Enhanced-Extension-Chromium-v1.1.0-beta.1.zip
8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac  XDM-Enhanced-Extension-Firefox-v1.1.0-beta.1.xpi
8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac  XDM-Enhanced-Extension-Firefox-v1.1.0-beta.1.zip
bb51d4ce3b614a78190421516bb8af4a4c7072f4ad3bd6bfde817a1fb050064b  XDM-Enhanced-Setup-v1.1.0-beta.1-win-x64.exe
f6a4f9d91c684e5db94f648ad8b9603c7aa0fdf69c1236982c8e2846fee15fae  XDM-Enhanced-v1.1.0-beta.1-windows-x64.zip
dff897c962f12d1bec6dbc00bc2a7a39d824e26ad97039312f73d0e2c99774e4  xdm-plugin-api-1.1.0-beta.1.jar
1a000e7b63d3c64d43571d24b6dcc17cb18540e9924dd370dadf832b5b652009  ytdlp-downloader.xdmplugin
```

---

## 8. Migration Guide

For developers migrating plugins from v1 to v2:
- See the comprehensive [Plugin v1 to v2 Migration Guide](PLUGIN_V1_TO_V2_MIGRATION_GUIDE.md).
- Consult the [Plugin Development Guide](PLUGIN_DEVELOPMENT.md) and [Plugin API Reference](PLUGIN_API_REFERENCE.md).
- End users may refer to the [Plugin Manager User Guide](PLUGIN_MANAGER_USER_GUIDE.md).

---

## 9. Authorized GitHub Release Workflow Handoff

> [!IMPORTANT]
> In accordance with release protocol, this phase performed **zero git commits, pushes, tags, or uploads**. The release materials and packages are staged locally.
>
> When the project maintainer authorizes the publication of `v1.1.0-beta.1`, execute the following step-by-step workflow:

### Step 1: Create Release Commit and Tag
```bash
# Verify working tree status
git status

# Stage metadata, changelog, documentation, and release notes
git add CHANGELOG.md README.md docs/ xdm-app/src/main/resources/app-version.json browser-extension/

# Commit release preparation
git commit -m "chore(release): prepare v1.1.0-beta.1 [skip ci]"

# Create annotated release tag
git tag -a v1.1.0-beta.1 -m "Release v1.1.0-beta.1 (Plugin SDK v2 Beta Preview)"

# Push commit and tag to remote
git push origin master
git push origin v1.1.0-beta.1
```

### Step 2: Publish GitHub Release with Staged Assets
Using the GitHub CLI (`gh`):
```bash
gh release create v1.1.0-beta.1 \
  --title "XDM Enhanced v1.1.0-beta.1 (Plugin SDK v2 Beta Preview)" \
  --notes-file docs/RELEASE_NOTES_v1.1.0-beta.1.md \
  --prerelease \
  build/dist/release-v1.1.0-beta.1/XDM-Enhanced-Setup-v1.1.0-beta.1-win-x64.exe \
  build/dist/release-v1.1.0-beta.1/XDM-Enhanced-v1.1.0-beta.1-windows-x64.zip \
  build/dist/release-v1.1.0-beta.1/xdm-app-1.1.0-beta.1.jar \
  build/dist/release-v1.1.0-beta.1/xdm-plugin-api-1.1.0-beta.1.jar \
  build/dist/release-v1.1.0-beta.1/XDM-Enhanced-Extension-Chromium-v1.1.0-beta.1.zip \
  build/dist/release-v1.1.0-beta.1/XDM-Enhanced-Extension-Firefox-v1.1.0-beta.1.zip \
  build/dist/release-v1.1.0-beta.1/XDM-Enhanced-Extension-Firefox-v1.1.0-beta.1.xpi \
  build/dist/release-v1.1.0-beta.1/proxy-provider.xdmplugin \
  build/dist/release-v1.1.0-beta.1/vpn-status.xdmplugin \
  build/dist/release-v1.1.0-beta.1/ui-notification.xdmplugin \
  build/dist/release-v1.1.0-beta.1/ytdlp-downloader.xdmplugin \
  build/dist/release-v1.1.0-beta.1/checksum-verifier.xdmplugin \
  build/dist/release-v1.1.0-beta.1/v2-sample-plugin.xdmplugin \
  build/dist/release-v1.1.0-beta.1/cyber-neon.xdmskin \
  build/dist/release-v1.1.0-beta.1/SHA256SUMS.txt
```

---
*Prepared by XDM Enhanced Release Engineering Team.*
