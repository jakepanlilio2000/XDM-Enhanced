# Release Channels & Binary Checksums

Understand the release channel hierarchy, update mechanisms, and cryptographic integrity verification for **XDM Enhanced**.

---

## 1. Release Channel Structure

XDM Enhanced distributes builds through two release channels:

### Stable Channel (Recommended)
- **Current Version:** `v1.0.1` (Published: November 19, 2026).
- **Target Audience:** General users and mission-critical production environments.
- **Characteristics:** Verified against full 15-phase and maintenance audit suites. Includes high-performance multi-connection segmentation, native BitTorrent 2.0, dynamic FlatLaf themes, custom skins, and companion extensions.

### Pre-release Channel (Beta / RC)
- **Status:** Previous beta releases (`v1.0.0-beta.1` through `v1.0.0-beta.6`) and release candidate (`v1.0.0-rc.1`) remain permanently archived on GitHub Releases.

---

## 2. Cryptographic SHA-256 Checksum Verification

Every official release includes a signed `SHA256SUMS.txt` manifest. Verifying checksums ensures that your download was not tampered with, intercepted, or corrupted during transit.

### Verified Hashes for `v1.0.1`

| Package | SHA-256 Hash |
|:---|:---|
| `XDM-Enhanced-Setup-v1.0.1-win-x64.exe` | `68be3296248f3f7704dba8cf1945ecc0dda125ed9ba898e468c9bc207b8de76c` |
| `XDM-Enhanced-v1.0.1-windows-x64.zip` | `2fc4b10348f2eb198cdc01159d6d8fc7bd14eeabd3d3d991645902487a6537f1` |
| `xdm-app-1.0.1.jar` | `31870f5068bfb6f49ad260fff4a010c2bb2efa3a6df1573bb2bb84399d830011` |
| `XDM-Enhanced-Extension-Chromium-v1.0.1.zip` | `6db9d41834af3decb57d4c36a13d16e0eb72c8441556d667e82b6fbd3d72b1cd` |
| `XDM-Enhanced-Extension-Firefox-v1.0.1.xpi` | `8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac` |
| `xdm-plugin-api-1.0.1.jar` | `5636b47925d6957554fb5a4313af0e478fa1ac764d2c5cf30f5c30cba4de4cc7` |

### How to Verify Checksums Locally

#### On Windows (PowerShell):
```powershell
Get-FileHash -Algorithm SHA256 .\XDM-Enhanced-Setup-v1.0.1-win-x64.exe
```

#### On Linux or macOS (Terminal):
```bash
sha256sum XDM-Enhanced-Setup-v1.0.1-win-x64.exe
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

