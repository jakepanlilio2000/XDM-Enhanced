# Release Channels & Binary Checksums

Understand the release channel hierarchy, update mechanisms, and cryptographic integrity verification for **XDM Enhanced**.

---

## 1. Release Channel Structure

XDM Enhanced distributes builds through two release channels:

### Pre-release Channel (Active Beta)
- **Current Version:** `v1.0.0-beta.6` (Published: November 10, 2026).
- **Target Audience:** General users, early adopters, testers, and extension developers.
- **Characteristics:** Receives active feature additions, UI improvements, and experimental plugin API capabilities. Extensively tested across automated CI suites prior to tagging.

### Stable Channel
- **Current Version:** Pending formal `v1.0.0` release.
- **Target Audience:** Mission-critical enterprise environments requiring long-term API stability.
- **Notice:** There are currently **no older stable releases** of XDM Enhanced. All releases prior to `v1.0.0` are tagged as pre-releases on GitHub.

---

## 2. Cryptographic SHA-256 Checksum Verification

Every official release includes a signed `SHA256SUMS.txt` manifest. Verifying checksums ensures that your download was not tampered with, intercepted, or corrupted during transit.

### Verified Hashes for `v1.0.0-beta.6`

| Package | SHA-256 Hash |
|:---|:---|
| `XDM-Enhanced-Setup-v1.0.0-beta.6-win-x64.exe` | `da95a6b3beda92a29357544dc448211f01d6c3ef48ca68182a0cd130a47d3636` |
| `XDM-Enhanced-v1.0.0-beta.6-windows-x64.zip` | `5bbb8edfd6d4d6a32540b309271c6b1cceefa0569f74f66c7bd2ef542876201d` |
| `xdm-app-1.0.0-beta.6.jar` | `5f14df837cab6ada72b3f8fad437ee73183f64d21238e2f1bf03b8ed80c26c9c` |
| `XDM-Enhanced-Extension-Chromium-v1.0.0-beta.6.zip` | `0ee9bb3edf49e11d126eca200ad67b2938e2d33b306a493bc31821ad0977e981` |
| `XDM-Enhanced-Extension-Firefox-v1.0.0-beta.6.xpi` | `14dfc9637085267dc098c56d49b882e69bdb5cca22eaa9cf75b6d4471951feaa` |

### How to Verify Checksums Locally

#### On Windows (PowerShell):
```powershell
Get-FileHash -Algorithm SHA256 .\XDM-Enhanced-Setup-v1.0.0-beta.6-win-x64.exe
```

#### On Linux or macOS (Terminal):
```bash
sha256sum XDM-Enhanced-Setup-v1.0.0-beta.6-win-x64.exe
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

