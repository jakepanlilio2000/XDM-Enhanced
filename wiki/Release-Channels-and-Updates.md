# Release Channels & Binary Checksums

Understand the release channel hierarchy, update mechanisms, and cryptographic integrity verification for **XDM Enhanced**.

---

## 1. Release Channel Structure

XDM Enhanced distributes builds through two release channels:

### Stable Channel (Recommended)
- **Current Version:** `v1.1.0` (Published: November 24, 2026).
- **Target Audience:** General users and production environments.
- **Characteristics:** Graduates the Modular Plugin SDK v2 (`xdm-plugin-api:1.1.0`, Specification 2.0.0), multi-platform packaging (Windows NSIS & MSI, macOS Apple Silicon & Intel DMGs, Linux native `.deb`, `.rpm`, `.pkg.tar.zst`, and portable `.tar.gz`), quarantine trust security, `--safe-mode`, HLS/DASH muxing and playlist hook capture improvements, link refresh `sourceUrl`/`sourceCookie` forwarding, early `preferIPv4Stack` toggle, and assistive-technology compatibility fixes.

### Pre-release Channel (Beta / Preview)
- **Status:** `v1.1.0` has graduated to the Stable Channel. When future minor releases enter preview, beta builds are published here.
- **Archive:** Historical preview and beta releases (`v1.1.0-beta.1`, `v1.0.0-rc.1`, and `v1.0.0-beta.1` through `v1.0.0-beta.6`) remain archived on GitHub Releases.

---

## 2. Cryptographic SHA-256 Checksum Verification

Every official release includes a CI-generated `SHA256SUMS.txt` manifest. Verifying checksums ensures that your download was not tampered with, intercepted, or corrupted during transit.

### Verified Hashes for `v1.1.0` (Current Stable)

Download and verify against [`SHA256SUMS.txt`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/SHA256SUMS.txt) attached to the [`v1.1.0` GitHub Release](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.1.0). *(Exact release binary hashes are synced here once the `v1.1.0` release workflow finishes publishing artifacts.)*

### Verified Hashes for `v1.1.0-beta.1` (Archived Beta Preview)

| Package Filename | Target System / Type | SHA-256 Hash |
|:---|:---|:---|
| `XDM-Enhanced-Setup-v1.1.0-beta.1-win-x64.exe` | Windows x64 NSIS Setup | `4af5dbf69780c5dec5f41fda351e996021d6bea1c5f5ff310c4cdf7ae1342f67` |
| `XDM-Enhanced-v1.1.0-beta.1-windows-x64.zip` | Windows x64 Portable | `f143832d3bb00e109331e7dfbd30ec15ddacdd3e7197c6b85b7c34609c44d598` |
| `xdm-1.1.0-beta.1-x64.msi` | Windows x64 MSI | `43c786304fcb838caa294faf4a21b5a5cb1ec1135b60b79f07ce6ed2d5ee5374` |
| `xdm-1.1.0-beta.1-arm64.msi` | Windows ARM64 MSI | `0fac3aed25ac275b36bd3923f0970903a59ed17acaa79cca1a15a43ebef55de5` |
| `xdmsetup-1.1.0-beta.1.msi` | Windows x64 Legacy Update MSI | `43c786304fcb838caa294faf4a21b5a5cb1ec1135b60b79f07ce6ed2d5ee5374` |
| `xdm-app-1.1.0-beta.1-arm64.dmg` | macOS Apple Silicon DMG | `8219c49bf70387faffb8ee8d7f6e5546f6744784b44edc96ab4ec47c63492a82` |
| `xdm-app-1.1.0-beta.1-x64.dmg` | macOS Intel DMG | `9b0f81683087f546fbb00379c2fc71623025a7af02b4a817880a20f188fc3148` |
| `xdm-app_1.1.0~beta.1_amd64.deb` | Debian / Ubuntu Linux x64 | `3895cda9621cf80afc92577309d9ef50f35db48e448c3a47f035d17ba244b8ab` |
| `xdm-app_1.1.0~beta.1_arm64.deb` | Debian / Ubuntu Linux ARM64 | `6b5eaf69c8bb564f81411a33634a52b4324aa6726b02903f76e587bac7a4becb` |
| `xdm-app-1.1.0~beta.1-1.x86_64.rpm` | Fedora / RHEL Linux x64 | `cf96cbb023204bd2d998c88e454ea794ec5a172c95ae0095474f05846d3e80ec` |
| `xdm-app-1.1.0~beta.1-1.aarch64.rpm` | Fedora / RHEL Linux ARM64 | `3ebd2ee9dbf510251f57e60b275853c5e5e1f7f53ca762f025cd18ea0e912549` |
| `xdm-app-1.1.0beta.1-1-x86_64.pkg.tar.zst` | Arch Linux / Manjaro x64 | `5caa0298a20ddec8d1e4757e47d8377c5aa7a62a472848ee3f0b99509895d84c` |
| `xdm-app-1.1.0beta.1-1-aarch64.pkg.tar.zst` | Arch Linux / Manjaro ARM64 | `b4f842f6524f79ef28466fc2f7597dd3481b36cd706e28fe147fd5b30e5e3891` |
| `xdm-app-1.1.0-beta.1-linux-x64.tar.gz` | Linux x64 Portable | `d32b4fed21abe4693549a3f0b7432c7f9b955241a9be3565a1332b2ca0265074` |
| `xdm-app-1.1.0-beta.1-linux-arm64.tar.gz` | Linux ARM64 Portable | `b9cf91053d3d0b623ddb2739816b199ef15ac5c61d35b1135613533e943a9e31` |
| `xdm-app-1.1.0-beta.1.jar` | Universal Java 25 Application | `63c2f259e2b43785aaa973e6c1f89696fa87c9d8fe7b4f911fcfa543591cd0d9` |
| `XDM-Enhanced-Extension-Chromium-v1.1.0-beta.1.zip` | Chromium Extension (MV3) | `67291d1d4ed133564ba4ff33781bc3fbddf1bd16467a99e90bf38930a9e385ce` |
| `XDM-Enhanced-Extension-Firefox-v1.1.0-beta.1.xpi` | Firefox Add-on (MV2) | `f0a91ef0a6b8969fe6c2670b67a044f36a3cf5cab34755984690ad72679c32f6` |
| `ytdlp-downloader.xdmplugin` | Media Engine Plugin | `1a000e7b63d3c64d43571d24b6dcc17cb18540e9924dd370dadf832b5b652009` |
| `proxy-provider.xdmplugin` | Network Proxy Plugin | `fa1eb3e2bc1ef54fe03ba1b3d9c8c8c03fe8585bbcc620ff5c15b6ae8b4d2755` |
| `vpn-status.xdmplugin` | VPN Telemetry Plugin | `4da24225a5137efda49a35dfdfbdef04c8453a38324bd44e90f40ef0b1255f90` |
| `ui-notification.xdmplugin` | UI & Toast Alerts Plugin | `d7dace8427aaea5883e4cc3e3e4763bb387754a6676e0c2f7db36b14f5b67a69` |
| `v2-sample-plugin.xdmplugin` | Reference SDK v2 Plugin | `7d94e132f25e76361e6356c8321edddd5d0a3f8132d8492fb578d03c175d95d5` |
| `checksum-verifier.xdmplugin` | Checksum Plugin | `c8f915c32a984515db8991537089f9324be18ab5c67421d419c5d30a83582806` |
| `cyber-neon.xdmskin` | Custom FlatLaf Theme Skin | `476cbd73d0ae68105dc6eab00cfdf1c8e1ddf377e59677e301f4ec3191a85a29` |

### Verified Hashes for `v1.0.2` (Previous Stable)

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
Get-FileHash -Algorithm SHA256 .\XDM-Enhanced-Setup-v1.1.0-win-x64.exe
```

#### On Linux or macOS (Terminal):
```bash
sha256sum XDM-Enhanced-Setup-v1.1.0-win-x64.exe
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

