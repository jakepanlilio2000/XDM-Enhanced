# Release Channels & Binary Checksums

Understand the release channel hierarchy, update mechanisms, and cryptographic integrity verification for **XDM Enhanced**.

---

## 1. Release Channel Structure

XDM Enhanced distributes builds through two release channels:

### Stable Channel (Recommended)
- **Current Version:** `v1.0.2` (Published: November 20, 2026).
- **Target Audience:** General users and mission-critical production environments.
- **Characteristics:** Verified against full 15-phase and maintenance audit suites. Includes high-performance multi-connection segmentation, native BitTorrent 2.0, dynamic FlatLaf themes, custom skins, and companion extensions.

### Pre-release Channel (Beta / RC)
- **Status:** Previous beta releases (`v1.0.0-beta.1` through `v1.0.0-beta.6`) and release candidate (`v1.0.0-rc.1`) remain permanently archived on GitHub Releases.

---

## 2. Cryptographic SHA-256 Checksum Verification

Every official release includes a signed `SHA256SUMS.txt` manifest. Verifying checksums ensures that your download was not tampered with, intercepted, or corrupted during transit.

### Verified Hashes for `v1.0.2`

| Package | SHA-256 Hash |
|:---|:---|
| `XDM-Enhanced-Setup-v1.0.2-win-x64.exe` | `1c2b03d1425bfc9415294a57ca29b9820b8eb42451e2da8b557a913159d3c6f5` |
| `XDM-Enhanced-v1.0.2-windows-x64.zip` | `46710f6b251b28ae0f94ceff98aedbcd0310c26a4ac1322819148d824184acf0` |
| `xdm-app-1.0.2.jar` | `c6b3191fd20b8bf4ef13611487a38738d20340a4995d97913cf48c5e6a17aebc` |
| `XDM-Enhanced-Extension-Chromium-v1.0.2.zip` | `ad21a2275fcea5214ccc943168d8ce496c81416b2f8adca4edf65f770c90f790` |
| `XDM-Enhanced-Extension-Firefox-v1.0.2.xpi` | `8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac` |
| `xdm-plugin-api-1.0.2.jar` | `f62619835012abe755763c7e4bd09e77c16c0259af145566321e263aba8520b3` |

### How to Verify Checksums Locally

#### On Windows (PowerShell):
```powershell
Get-FileHash -Algorithm SHA256 .\XDM-Enhanced-Setup-v1.0.2-win-x64.exe
```

#### On Linux or macOS (Terminal):
```bash
sha256sum XDM-Enhanced-Setup-v1.0.2-win-x64.exe
```

---

## 3. How to Update XDM Enhanced

### Updating the Windows Installer
1. Download the latest installer `.exe` from [GitHub Releases](https://github.com/jakepanlilio2000/XDM-Enhanced/releases).
2. Run the installer without uninstalling your previous version.
3. The installer automatically overwrites binaries while preserving all user settings, download history, and category rules located in `~/.xdm/`.

### Updating the Windows Portable Bundle
1. Exit the running XDM Enhanced portable instance.
2. Download and extract the newer portable `.zip` to a new folder.
3. Copy the `data/` folder (or `settings.json` and `downloads.db`) from your old portable folder into the new one.
4. Launch `xdm-app.exe`.

---

## Related Articles

* [[Installation Guide|Installation-Guide]]: Complete installation instructions.
* [[Troubleshooting by Symptom|Troubleshooting-by-Symptom]]: Common installation issues.
* Check latest builds on the [GitHub Releases Repository](https://github.com/jakepanlilio2000/XDM-Enhanced/releases).

