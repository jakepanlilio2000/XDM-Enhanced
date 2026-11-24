# Release Channels & Binary Checksums

Understand the release channel hierarchy, update mechanisms, and cryptographic integrity verification for **XDM Enhanced**.

---

## 1. Release Channel Structure

XDM Enhanced distributes builds through two release channels:

### Stable Channel (Recommended)
- **Current Version:** `v1.2.0` (Published: November 24, 2026).
- **Target Audience:** General users and production environments.
- **Characteristics:** Introduces Skin Schema v3 custom window chrome and widgets, 5 new sample `.xdmskin` packages (`retro-hifi`, `anime-sakura`, `steampunk-brass`, `modern-aurora`, `synthwave-outrun`), and 1:1 `subhra74/xdm` Original XDM UI parity alongside graduated Modular Plugin SDK v2 (`API 1.1.0` / Spec `2.0.0`), multi-platform packaging, HLS/DASH muxing fixes, and early `preferIPv4Stack`.

### Pre-release Channel (Beta / Preview)
- **Status:** `v1.2.0` is active on the Stable Channel. When upcoming releases enter preview, beta builds are published here.
- **Archive:** Historical stable and beta releases (`v1.1.0`, `v1.1.0-beta.1`, `v1.0.2`, `v1.0.0-rc.1`, and `v1.0.0-beta.1` through `v1.0.0-beta.6`) remain archived on GitHub Releases.

---

## 2. Cryptographic SHA-256 Checksum Verification

Every official release includes a CI-generated `SHA256SUMS.txt` manifest. Verifying checksums ensures that your download was not tampered with, intercepted, or corrupted during transit.

### Verified Hashes for `v1.2.0` (Current Stable)

Download and verify against [`SHA256SUMS.txt`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.2.0/SHA256SUMS.txt) attached to the [`v1.2.0` GitHub Release](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.2.0):

| Package Filename | Target System / Type | SHA-256 Hash |
|:---|:---|:---|
| `XDM-Enhanced-Setup-v1.2.0-win-x64.exe` | Windows x64 NSIS Setup | `66085730c6974d8b9fe446e765cf2bb4cadb084141016c32b02a7f33697a782c` |
| `XDM-Enhanced-v1.2.0-windows-x64.zip` | Windows x64 Portable | `f13bbba5c3c7aba67ab6ea42d901745b9a1afb215ea89fcf6d56dc4b0a7392fd` |
| `xdm-1.2.0-x64.msi` | Windows x64 MSI | `b7aa6922f5739b6d91864f24b349cf1ea922ea903c0d88cdb92f9c7e938e8d98` |
| `xdm-1.2.0-arm64.msi` | Windows ARM64 MSI | `af74795226fa79988d12994872eb1b4dec6b3a23ced5b55c01fdb6e6663dda87` |
| `xdmsetup-1.2.0.msi` | Windows x64 Legacy Update MSI | `b7aa6922f5739b6d91864f24b349cf1ea922ea903c0d88cdb92f9c7e938e8d98` |
| `xdm-app-1.2.0-arm64.dmg` | macOS Apple Silicon DMG | `5491c6f498653186320194d798be2289de56b455e664876c20dabf5d5c8d5910` |
| `xdm-app-1.2.0-x64.dmg` | macOS Intel DMG | `1c6457974ed90c314bce97c6ba9802ef18f7ffa7d28a6cc60e9f6a5663349cdc` |
| `xdm-app_1.2.0_amd64.deb` | Debian / Ubuntu Linux x64 | `9e74190b1c7e8ed1c889d49897d08d9b518d4a28ee449e92f01b19d184917080` |
| `xdm-app_1.2.0_arm64.deb` | Debian / Ubuntu Linux ARM64 | `64d95fdc316b79c7b4c45aa037c23c166d306289c150de4392ec4d9cfb7229e2` |
| `xdm-app-1.2.0-1.x86_64.rpm` | Fedora / RHEL Linux x64 | `672de2a3c63a5b29788c2aa01aaffc0ca556d83afb5d0ef3d8a4326fc3c1f760` |
| `xdm-app-1.2.0-1.aarch64.rpm` | Fedora / RHEL Linux ARM64 | `0a8e8bd1fda77674b02927eb6b82809c2356887f0a4023e0da555287dc541d39` |
| `xdm-app-1.2.0-1-x86_64.pkg.tar.zst` | Arch Linux / Manjaro x64 | `0141892bc9ef7a3af1eb95548c8bb06c868050eebc02a638dec9a7ee01c72c2f` |
| `xdm-app-1.2.0-1-aarch64.pkg.tar.zst` | Arch Linux / Manjaro ARM64 | `30fd4faccd5683239d5a697c54e173a7528870f27b961544802c8ccb5c5da246` |
| `xdm-app-1.2.0-linux-x64.tar.gz` | Linux x64 Portable | `349416b0eefa72a54f52e0a047a235116683e52f2ad483590cbd50344cc63f0a` |
| `xdm-app-1.2.0-linux-arm64.tar.gz` | Linux ARM64 Portable | `b257770603a1833daf7a77ebdeffe9f0d7276e785f14771c877255368322e900` |
| `xdm-app-1.2.0.jar` | Universal Java 25 Application | `8b64d7c62d61a97e210700f71ecd759a7549e41273b18ed13d1d1d6c2d22f25b` |
| `xdm-plugin-api-1.2.0.jar` | Public Plugin API JAR | `9627829ea3f499f4d7d125e61111535ce8ec1347ff0e8d3ca3a6546adac92400` |
| `XDM-Enhanced-Extension-Chromium-v1.2.0.zip` | Chromium Extension (MV3) | `b384768b277f28d5c1727ae5303466ac5a59e7ef63e25eccddc81c07a25fbc7e` |
| `XDM-Enhanced-Extension-Firefox-v1.2.0.xpi` | Firefox Add-on (MV2) | `e0b56e616a53ee008292040a98b65edaeef1e65e271198119ecbef05d4512e6d` |
| `retro-hifi.xdmskin` | Schema v3 Retro Console Skin | `5e833a5e2fa403f0d602946442c64218f0e18f23ac995a178ded74f21c1b59f0` |
| `anime-sakura.xdmskin` | Schema v3 Anime Pastel Skin | `43c3332d72db84025b5271682da4d628b88f992c040942b511dc7e9a831999bd` |
| `steampunk-brass.xdmskin` | Schema v3 Clockwork Brass Skin | `5c59e99971066a25e7998fd3923be8c99f9f5f78b8e9640004f2eb002d0d8cf1` |
| `modern-aurora.xdmskin` | Schema v3 Modern Glass Skin | `cdc94af5313fc58331343879540a3e641018541c91f21318b6bd8abfbcc0bbdf` |
| `synthwave-outrun.xdmskin` | Schema v3 80s Outrun Skin | `88e38ffab4a392faa3f28925b6c5b1b456444e321922713aacf223d534278bca` |
| `cyber-neon.xdmskin` | Schema v2 Cyber Reference Skin | `684d2ff6bb77c337ab93b34347100e40a4b8c6b3800fb29c8bc16d47e0bb1e23` |
| `ytdlp-downloader.xdmplugin` | Media Engine Plugin | `bde0b6288b4b695779605ecd8f44c32f96080d9363c4c9c1b31cdca94bd70dd8` |
| `proxy-provider.xdmplugin` | Network Proxy Plugin | `dd83ff8bc27adc4615ac928ba7ef4eeb8080c6a3996c8ab43281c6ec1a29ea22` |
| `vpn-status.xdmplugin` | VPN Telemetry Plugin | `62406b0fa6b30ec08b941ac1223149d083013e98e1cc869f5cd0aabff7dbfec4` |
| `ui-notification.xdmplugin` | UI & Toast Alerts Plugin | `5d252e11b9a56de2830913d5e4320ef8437e63ffd607a5d6b67202c24926bcc6` |
| `v2-sample-plugin.xdmplugin` | Reference SDK v2 Plugin | `a8190202d94c06c1fc5119102278bd1e097e12ef281170515980d1765c7c1195` |
| `checksum-verifier.xdmplugin` | Checksum Plugin | `e5abfa34e8b18f3bff45f835edbecfc838e4089050df729b81a54a6933f672ee` |

### Verified Hashes for `v1.1.0` (Previous Stable)

Download and verify against [`SHA256SUMS.txt`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/SHA256SUMS.txt) attached to the [`v1.1.0` GitHub Release](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.1.0):

| Package Filename | Target System / Type | SHA-256 Hash |
|:---|:---|:---|
| `XDM-Enhanced-Setup-v1.1.0-win-x64.exe` | Windows x64 NSIS Setup | `32a07432de279420934fad99c324ce65e23d402c33a2ef1e958364373373c4fa` |
| `XDM-Enhanced-v1.1.0-windows-x64.zip` | Windows x64 Portable | `24caf7a59901e9c46747d6ff6dd83de4216b8016f5c0fc12ea8ddbbd13831418` |
| `xdm-1.1.0-x64.msi` | Windows x64 MSI | `07e8c18dc0c7b13f241b5a45d5141b0b75a15789dcf5f04a1032f9c408cff88c` |
| `xdm-1.1.0-arm64.msi` | Windows ARM64 MSI | `c465e72b833bb3f5b0f940c9d1eef740d964502f0c745dd7de6a5c6a31f02a22` |
| `xdmsetup-1.1.0.msi` | Windows x64 Legacy Update MSI | `07e8c18dc0c7b13f241b5a45d5141b0b75a15789dcf5f04a1032f9c408cff88c` |
| `xdm-app-1.1.0-arm64.dmg` | macOS Apple Silicon DMG | `4897a8a4b6800bcbb8f45443cf5346ff203d3a9f20c89cf6b6fe8735478d0252` |
| `xdm-app-1.1.0-x64.dmg` | macOS Intel DMG | `75ef36ad1a9837811cd23e740411b78e91da7dd26e0b1a64765101c1fbb76646` |
| `xdm-app_1.1.0_amd64.deb` | Debian / Ubuntu Linux x64 | `e6f6af843acc3f3d67e639d7b64e9967ddecfce6e5ef5599f159b89acbf5bb16` |
| `xdm-app_1.1.0_arm64.deb` | Debian / Ubuntu Linux ARM64 | `f44d84357ed7c62e6b2b808e5bc90c474f741d3ed1c52bf0b026223024a81f0a` |
| `xdm-app-1.1.0-1.x86_64.rpm` | Fedora / RHEL Linux x64 | `e660e4368da461b0013d8b03a24c5cde603a7e6f4a9c423534e8cc6f4ebc5df1` |
| `xdm-app-1.1.0-1.aarch64.rpm` | Fedora / RHEL Linux ARM64 | `018d85134edd64b00641cec17d85e8daa948e74dac7c1eb9e9b5d7aed8e8f693` |
| `xdm-app-1.1.0-1-x86_64.pkg.tar.zst` | Arch Linux / Manjaro x64 | `06ddb88e6859361bc7aca6fe7148d1029120e563a22068809ed10091cc79c025` |
| `xdm-app-1.1.0-1-aarch64.pkg.tar.zst` | Arch Linux / Manjaro ARM64 | `5f448dd18c2f795cce362e8c3f4b2cd9b4b58ac981fbbd9c4aa8ca20c692d566` |
| `xdm-app-1.1.0-linux-x64.tar.gz` | Linux x64 Portable | `1e99116c3e5d22d0500dcb26b980039c0538b267d527d68ea98badaff7ed0794` |
| `xdm-app-1.1.0-linux-arm64.tar.gz` | Linux ARM64 Portable | `4fa802b7b790d86c605e62058c21597580d5baa3c4b1f5eef46c4fb33ac63c5b` |
| `xdm-app-1.1.0.jar` | Universal Java 25 Application | `6e0a23347d95187dee7f7f0883d1c55f3c778d7ba7e16b12125015474e5e19ff` |
| `xdm-plugin-api-1.1.0.jar` | Public Plugin API JAR | `42edba3f3895853bca0e2450aa2355d2646f6e7f8de2debd1f7690b4830e58ea` |
| `XDM-Enhanced-Extension-Chromium-v1.1.0.zip` | Chromium Extension (MV3) | `884993cd0acd8b95543532b695dc3a9191294e20d594cbbad3dfcef5de166f0e` |
| `XDM-Enhanced-Extension-Firefox-v1.1.0.xpi` | Firefox Add-on (MV2) | `b178768eca3835a483ccdddd5d3a2610827124cb78b24eefdaaac685a99a7814` |
| `ytdlp-downloader.xdmplugin` | Media Engine Plugin | `bde0b6288b4b695779605ecd8f44c32f96080d9363c4c9c1b31cdca94bd70dd8` |
| `proxy-provider.xdmplugin` | Network Proxy Plugin | `dd83ff8bc27adc4615ac928ba7ef4eeb8080c6a3996c8ab43281c6ec1a29ea22` |
| `vpn-status.xdmplugin` | VPN Telemetry Plugin | `62406b0fa6b30ec08b941ac1223149d083013e98e1cc869f5cd0aabff7dbfec4` |
| `ui-notification.xdmplugin` | UI & Toast Alerts Plugin | `5d252e11b9a56de2830913d5e4320ef8437e63ffd607a5d6b67202c24926bcc6` |
| `v2-sample-plugin.xdmplugin` | Reference SDK v2 Plugin | `a8190202d94c06c1fc5119102278bd1e097e12ef281170515980d1765c7c1195` |
| `checksum-verifier.xdmplugin` | Checksum Plugin | `e5abfa34e8b18f3bff45f835edbecfc838e4089050df729b81a54a6933f672ee` |
| `cyber-neon.xdmskin` | Custom FlatLaf Theme Skin | `476cbd73d0ae68105dc6eab00cfdf1c8e1ddf377e59677e301f4ec3191a85a29` |

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
Get-FileHash -Algorithm SHA256 .\XDM-Enhanced-Setup-v1.2.0-win-x64.exe
```

#### On Linux or macOS (Terminal):
```bash
sha256sum XDM-Enhanced-Setup-v1.2.0-win-x64.exe
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

