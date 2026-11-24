# XDM Enhanced v1.1.0

Stable Release of **XDM Enhanced** — graduating the **Modular Plugin SDK v2** (`xdm-plugin-api:1.1.0`, Specification 2.0.0) alongside synced upstream core media muxing, HLS playlist capture, link refresh, early IPv4 networking, and desktop accessibility improvements.

---

## Release Highlights

- **Graduated Modular Plugin SDK v2 (`xdm-plugin-api:1.1.0`):**
  - Clean public Java SPI (Specification 2.0.0) isolated from internal host classes (`xdm.app.*`, `xdm.core.*`, `xdm.manager.*`) via `PluginFilteringClassLoader`.
  - First-class SPIs for download engines (`PluginDownloadEngine`), scoped network proxies (`ProxyProvider` / `PluginNetworkManager`), non-privileged VPN telemetry (`VpnStatusProvider`), chained post-download processors (`PostDownloadProcessor`), UI contributors, typed `EventTopics`, and `PluginSettings`.
  - Full legacy v1 compatibility via `LegacyV1PluginAdapter`, default `UNTRUSTED` quarantine approval, zip-slip/archive-bomb/executable hardening, 10s watchdog timers, and `--safe-mode` / `--no-plugins` recovery flags.
  - 6 standalone compilable sample plugins in `sample-plugins/`: `proxy-provider-sample`, `vpn-status-sample`, `ui-notification-sample`, `v2-sample-plugin`, `checksum-verifier-plugin`, and `ytdlp-downloader-plugin`.
  - Fixed signed ID negativity and terminal-state delete race conditions in `PluginDownloadBridge` and `TaskInfoDB`.
- **Media Muxing & HLS Playlist Body Capture:**
  - Synced upstream `xdm-core` muxer and codec improvements across MP4/ISO, MKV, Ogg, TS, AAC/Opus/FLAC/AC3/Vorbis, chunk takeover/retry logic, and atomic output commits.
  - Added browser extension captured playlist body forwarding (`hls-hook.js` / `hls-relay.js` in Chromium MV3 and `readPlaylistBody` in Firefox MV2) backed by `HlsPlaylistStore` and `PlaylistTextCache`.
  - Added configurable **Save streams as MP4** setting across settings UI, config persistence, and the New Video Download dialog.
- **Link Refresh, Source Tracking & Retry Resilience:**
  - Preserved `sourceUrl` and `sourceCookie` across captured downloads, New Download / Link Refresh dialogs, completion notes, and `LinkRefresher`.
  - Added server-busy retry ETA display (`retryAt`) in `ProgressWindow` and `HeadlessCli`.
- **Networking, Platform & Accessibility Hardening:**
  - Added early **Force IPv4** (`java.net.preferIPv4Stack`) startup setting, `AssistiveTech` sanitization with `jdk.accessibility` module support, XDG Desktop Portal `KeepAwake` inhibition and `gdbus` shutdown fallback, and Light/Dark theme refinements.

---

## Verifying Release Assets

Every release publishes a `SHA256SUMS.txt` file containing cryptographic SHA-256 digests for every attached installer, archive, JAR, extension, plugin, and skin package.

```powershell
# Verify on Windows (PowerShell)
Get-FileHash -Algorithm SHA256 .\XDM-Enhanced-Setup-v1.1.0-win-x64.exe

# Verify on Linux / macOS
sha256sum -c SHA256SUMS.txt
```

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
