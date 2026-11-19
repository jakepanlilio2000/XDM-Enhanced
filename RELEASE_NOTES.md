# XDM Enhanced v1.0.2 (Maintenance Release)

Maintenance Release of **XDM Enhanced** — an independent, open-source download manager forked from Xtreme Download Manager (XDM). It pairs high-speed multi-connection segmented HTTP/HTTPS downloading and streaming media capture with native BitTorrent and magnet support, modern FlatLaf dynamic theming, custom skin packages, a public plugin framework, automated post-download rules, and companion browser extensions.

---

## Release Highlights

- **Scheduler Concurrency & Shutdown Safety:** Synchronized all operations on `DownloadScheduler.entries` (`synchronized(entries)`), eliminating race conditions and `ConcurrentModificationException` during concurrent download schedule updates. Added explicit `DownloadScheduler.stop()` invocation during `AppContext.shutdown()` to ensure background timer tasks terminate cleanly without thread leaks or JVM hanging.
- **Atomic Schedule Persistence:** Serialized scheduled download job states strictly within the entries monitor, guaranteeing atomic persistence writes and preventing partial writes or corrupted schedule state during concurrent triggers.
- **BitTorrent Corrupt Piece Recovery (`HashFailedAlert`):** Added explicit handling and dispatch of `HashFailedAlert` in the BitTorrent session lifecycle, ensuring corrupted pieces are immediately detected, dropped, re-requested, and logged without silent data corruption.
- **Browser Integration Diagnostic Details:** Included loopback listener health status in diagnostic copy output when custom protocol handler registration is absent or restricted, eliminating diagnostic false negatives during troubleshooting.
- **Sustained Operation & 5,000-Record Scale Verification:** Validated long-duration memory stability, zero thread leaks, responsive UI rendering under 5,000 active/completed download records, smooth skin/theme switching, and resilient recovery under simulated disk-full and network-severed faults.
- **Release Protection & CI Governance:** Enforced CI release tag protection guards, preventing accidental branch-push overwrites and safeguarding immutable release tags.

---

## Changes & Fixes

### Added
- **Sustained Scale & Soak Audit Test Suite (`xdm-app`):** Added `Phase21SustainedOperationAndScaleAuditTest` validating system behavior under 5,000 active/completed items, rapid table/card view switching, high-frequency progress events, and memory bounds.
- **Release Protection Workflow Guards (`.github`):** Hardened CI workflows against branch-push release overwrites and verified release tag immutability.

### Fixed
- **DownloadScheduler Concurrency (`xdm-app`):** Synchronized `entries` access across add, remove, reschedule, and iterate operations; ensured timer tasks are stopped during graceful shutdown.
- **DownloadScheduler Persistence (`xdm-app`):** Atomic snapshot serialization within the monitor lock preventing partial state writes to disk.
- **Torrent Corrupt Hash Alert Dispatch (`xdm-torrent`):** Added dispatching and listener handling for `HashFailedAlert`, re-downloading corrupt blocks immediately upon verification failure.
- **Browser Extension Diagnostic Reporting (`xdm-app`):** Confirmed loopback listener status even when Windows custom URI scheme registration is missing or restricted.

---

## Verified Release Assets & Checksums

| Asset | Size | SHA-256 Checksum |
|---|---|---|
| `XDM-Enhanced-Extension-Chromium-v1.0.2.zip` | 106,503 bytes | `ad21a2275fcea5214ccc943168d8ce496c81416b2f8adca4edf65f770c90f790` |
| `XDM-Enhanced-Extension-Firefox-v1.0.2.xpi` | 103,600 bytes | `8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac` |
| `XDM-Enhanced-Extension-Firefox-v1.0.2.zip` | 103,600 bytes | `8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac` |
| `XDM-Enhanced-Setup-v1.0.2-win-x64.exe` | 63,963,784 bytes | `1c2b03d1425bfc9415294a57ca29b9820b8eb42451e2da8b557a913159d3c6f5` |
| `XDM-Enhanced-v1.0.2-windows-x64.zip` | 74,786,944 bytes | `46710f6b251b28ae0f94ceff98aedbcd0310c26a4ac1322819148d824184acf0` |
| `checksum-verifier.xdmplugin` | 6,211 bytes | `c8f915c32a984515db8991537089f9324be18ab5c67421d419c5d30a83582806` |
| `cyber-neon.xdmskin` | 34,446 bytes | `5e8293da37215dbafff6a41a88495bdefcc0faa3f942c878ada69664ec05ef0f` |
| `xdm-app-1.0.2.jar` | 95,113,126 bytes | `c6b3191fd20b8bf4ef13611487a38738d20340a4995d97913cf48c5e6a17aebc` |
| `xdm-plugin-api-1.0.2.jar` | 21,966 bytes | `f62619835012abe755763c7e4bd09e77c16c0259af145566321e263aba8520b3` |
| `ytdlp-downloader.xdmplugin` | 28,561 bytes | `8a484eff37e95d961e028c80b353ff84580d275a51e166860f5cfec29a5bf189` |
| `SHA256SUMS.txt` | 988 bytes | Included in release |

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
