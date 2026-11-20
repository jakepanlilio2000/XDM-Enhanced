# XDM Enhanced v1.1.0-beta.1 (Plugin SDK v2 Beta Preview)

Beta Preview Release of **XDM Enhanced** — introducing the **Modular Plugin SDK v2**, a modern, extensible service provider framework that empowers developers to build native download engines, proxy profile providers, VPN client integration listeners, and custom UI views—while preserving complete backward compatibility with all legacy v1 plugins and maintaining an honest, transparent security trust model.

---

## Release Highlights

- **Decoupled Public SPI (`xdm-plugin-api`):** Plugins compile exclusively against `xdm-plugin-api:1.0.2` (Specification 2.0.0) without access to internal host classes (`xdm.app.*`, `xdm.core.*`, `xdm.manager.*`).
- **Extended Service Provider Interfaces:**
  - `PluginDownloadEngine`: Register external or custom download engines (e.g., yt-dlp, aria2, FFmpeg streams) with first-class progress tracking, cancellation, and pause/resume lifecycle.
  - `PluginNetworkManager` & `ProxyProfile`: Programmatically supply authenticated SOCKS5/HTTP/HTTPS proxy configurations.
  - `PluginVpnIntegration`: Provide non-privileged status and connectivity monitoring for WireGuard and OpenVPN clients.
  - `PluginUiContext` & `PluginView`: Contribute custom tab views, side-panel widgets, and desktop toast notifications adhering to active Light, Dark, System, and custom FlatLaf skins.
  - `PluginSettings`: Type-safe configuration persistence with JSON backing and masked credential storage.
- **Transparent Legacy v1 Compatibility:** Seamless coexistence via `LegacyPluginAdapter`. Legacy plugins targeting `xdm.plugin.Plugin` load and operate without recompilation or manifest modifications.
- **Honest Security Posture & Quarantine:** In-process Java plugins execute with user OS account privileges. Newly discovered plugins default to `UNTRUSTED` quarantine until explicitly approved. Extractors enforce strict zip-slip, symlink, archive bomb, and binary blacklists.
- **Modernized Plugin Manager UX:** Zero dead buttons. Comprehensive status badges (`ACTIVE`, `DISABLED`, `QUARANTINED`, `CRASHED`, `RECOVERY_MODE`), toggle controls, and one-click diagnostic bundle export with credential scrubbing.
- **Fault Tolerance & Safe Mode:** 10-second watchdog timers isolate plugin startup from the UI Event Dispatch Thread. Starting with `--safe-mode` disables third-party plugins for disaster recovery.
- **5 Standalone Compilable Samples:** Reference projects located in `sample-plugins/`: `proxy-provider-sample`, `vpn-status-sample`, `ui-notification-sample`, `v2-sample-plugin`, and `ytdlp-downloader-plugin`.

---

## Verified Release Assets & Checksums

| Asset | Size | SHA-256 Checksum |
|---|---|---|
| `XDM-Enhanced-Extension-Chromium-v1.1.0-beta.1.zip` | 106,508 bytes | `fd0436ccb82d3ccb2a428959dcf7570121d567e964bcf68b03e5f552910f783e` |
| `XDM-Enhanced-Extension-Firefox-v1.1.0-beta.1.xpi` | 103,600 bytes | `8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac` |
| `XDM-Enhanced-Extension-Firefox-v1.1.0-beta.1.zip` | 103,600 bytes | `8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac` |
| `XDM-Enhanced-Setup-v1.1.0-beta.1-win-x64.exe` | 64,127,376 bytes | `bb51d4ce3b614a78190421516bb8af4a4c7072f4ad3bd6bfde817a1fb050064b` |
| `XDM-Enhanced-v1.1.0-beta.1-windows-x64.zip` | 74,953,385 bytes | `f6a4f9d91c684e5db94f648ad8b9603c7aa0fdf69c1236982c8e2846fee15fae` |
| `checksum-verifier.xdmplugin` | 6,211 bytes | `c8f915c32a984515db8991537089f9324be18ab5c67421d419c5d30a83582806` |
| `cyber-neon.xdmskin` | 34,446 bytes | `5e8293da37215dbafff6a41a88495bdefcc0faa3f942c878ada69664ec05ef0f` |
| `proxy-provider.xdmplugin` | 6,742 bytes | `fa1eb3e2bc1ef54fe03ba1b3d9c8c8c03fe8585bbcc620ff5c15b6ae8b4d2755` |
| `ui-notification.xdmplugin` | 10,516 bytes | `d7dace8427aaea5883e4cc3e3e4763bb387754a6676e0c2f7db36b14f5b67a69` |
| `v2-sample-plugin.xdmplugin` | 14,698 bytes | `7d94e132f25e76361e6356c8321edddd5d0a3f8132d8492fb578d03c175d95d5` |
| `vpn-status.xdmplugin` | 7,323 bytes | `4da24225a5137efda49a35dfdfbdef04c8453a38324bd44e90f40ef0b1255f90` |
| `xdm-app-1.1.0-beta.1.jar` | 95,501,822 bytes | `f2487088a007d38d32823d099d5fa69d8d20e8c7b7caaefae5fbdcb962201d86` |
| `xdm-plugin-api-1.1.0-beta.1.jar` | 57,665 bytes | `dff897c962f12d1bec6dbc00bc2a7a39d824e26ad97039312f73d0e2c99774e4` |
| `ytdlp-downloader.xdmplugin` | 30,403 bytes | `1a000e7b63d3c64d43571d24b6dcc17cb18540e9924dd370dadf832b5b652009` |
| `SHA256SUMS.txt` | 1,407 bytes | Included in release |

---

## Acknowledgments & Upstream Attribution

- Original XDM project created by Subhra Das Gupta ([subhra74/xdm](https://github.com/subhra74/xdm)) and upstream contributors. Upstream documentation is preserved in [README.UPSTREAM.md](README.UPSTREAM.md).
- `libtorrent4j` wrapper created by Alden Torres and the FrostWire team.
- `libtorrent` (Rasterbar) BitTorrent library developed by Arvid Norberg.
- `FlatLaf` look-and-feel developed by FormDev Software GmbH.
- Nullsoft Scriptable Install System (NSIS) project team.
- BellSoft for the Liberica JDK 25 runtime.

---

## License

XDM Enhanced is free, open-source software licensed under the **GNU General Public License v3.0 (GPL-3.0)**.
See [LICENSE](LICENSE) for the full license text.
