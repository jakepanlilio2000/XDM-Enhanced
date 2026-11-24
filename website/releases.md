# Release Notes & Version History

Detailed changelog and binary checksum records for all published releases of **XDM Enhanced**.

---

## [1.2.0] — 2026-11-24 (Latest Stable Release)

Feature release introducing **Skin Schema v3** custom window chrome and widget styling, **5 new sample `.xdmskin` theme packages**, and **1:1 Original XDM (`subhra74/xdm`)** UI parity while keeping full **Modular Plugin SDK v2** compatibility.

### Highlights &amp; Key Improvements

- **Skin Schema v3 Custom Window &amp; Widget Chrome (`chromeMode: "custom"`):**
  - Declarative custom window titlebars (`SkinnedTitleBar`) with gradients, textures, alignment, live transfer stats ticker (`LcdStatsTicker`), toggleable compact **WindowShade** frame collapse, custom control sprites (`mac`, `beveled`, `anime`, `steampunk`, `minimal`, plus PNG overrides), and 3D beveled/riveted outer chassis borders (`SkinnedFrameBorder`).
  - Custom `SkinnedProgressBarUI` renderers (`segmented_led`, `brass_gauge`, `striped_candy`, `gradient_glow`), `SkinnedButtonStyler` presets (`beveled_3d`, `riveted_brass`, `neon_outline`, `pill_glow`), CRT scanline overlays, and high-contrast light/dark sidebar tree selection rendering (`CategorySidebar`).
- **Five New Verified Sample Skins (`sample-skins/`):**
  - Published `retro-hifi.xdmskin`, `anime-sakura.xdmskin`, `steampunk-brass.xdmskin`, `modern-aurora.xdmskin`, and `synthwave-outrun.xdmskin` alongside refreshed `cyber-neon.xdmskin`.
- **1:1 Original XDM (`classic_xdm` / `subhra74/xdm`) UI Parity:**
  - Updated `Original XDM` (`chromeMode: "upstream"`) to reproduce `subhra74/xdm` pixel-for-pixel across `AppToolBar`, `FilterListPanel`, `UpstreamClassicListView`, and FlatMac Dark/Light palettes.

### Published Assets &amp; Checksums (`v1.2.0`)

Verify all release binaries against [`SHA256SUMS.txt`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.2.0/SHA256SUMS.txt) on the [`v1.2.0` GitHub Release](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.2.0):

<table class="checksum-table">
  <thead>
    <tr>
      <th style="width: 45%;">Asset Filename</th>
      <th>SHA-256 Checksum</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>XDM-Enhanced-Setup-v1.2.0-win-x64.exe</strong></td>
      <td><code>66085730c6974d8b9fe446e765cf2bb4cadb084141016c32b02a7f33697a782c</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-v1.2.0-windows-x64.zip</strong></td>
      <td><code>f13bbba5c3c7aba67ab6ea42d901745b9a1afb215ea89fcf6d56dc4b0a7392fd</code></td>
    </tr>
    <tr>
      <td><strong>xdm-1.2.0-x64.msi</strong></td>
      <td><code>b7aa6922f5739b6d91864f24b349cf1ea922ea903c0d88cdb92f9c7e938e8d98</code></td>
    </tr>
    <tr>
      <td><strong>xdm-1.2.0-arm64.msi</strong></td>
      <td><code>af74795226fa79988d12994872eb1b4dec6b3a23ced5b55c01fdb6e6663dda87</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.2.0-arm64.dmg</strong></td>
      <td><code>5491c6f498653186320194d798be2289de56b455e664876c20dabf5d5c8d5910</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.2.0-x64.dmg</strong></td>
      <td><code>1c6457974ed90c314bce97c6ba9802ef18f7ffa7d28a6cc60e9f6a5663349cdc</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app_1.2.0_amd64.deb</strong></td>
      <td><code>9e74190b1c7e8ed1c889d49897d08d9b518d4a28ee449e92f01b19d184917080</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app_1.2.0_arm64.deb</strong></td>
      <td><code>64d95fdc316b79c7b4c45aa037c23c166d306289c150de4392ec4d9cfb7229e2</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.2.0-1.x86_64.rpm</strong></td>
      <td><code>672de2a3c63a5b29788c2aa01aaffc0ca556d83afb5d0ef3d8a4326fc3c1f760</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.2.0-1.aarch64.rpm</strong></td>
      <td><code>0a8e8bd1fda77674b02927eb6b82809c2356887f0a4023e0da555287dc541d39</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.2.0-1-x86_64.pkg.tar.zst</strong></td>
      <td><code>0141892bc9ef7a3af1eb95548c8bb06c868050eebc02a638dec9a7ee01c72c2f</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.2.0-1-aarch64.pkg.tar.zst</strong></td>
      <td><code>30fd4faccd5683239d5a697c54e173a7528870f27b961544802c8ccb5c5da246</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.2.0-linux-x64.tar.gz</strong></td>
      <td><code>349416b0eefa72a54f52e0a047a235116683e52f2ad483590cbd50344cc63f0a</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.2.0-linux-arm64.tar.gz</strong></td>
      <td><code>b257770603a1833daf7a77ebdeffe9f0d7276e785f14771c877255368322e900</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.2.0.jar</strong></td>
      <td><code>8b64d7c62d61a97e210700f71ecd759a7549e41273b18ed13d1d1d6c2d22f25b</code></td>
    </tr>
    <tr>
      <td><strong>xdm-plugin-api-1.2.0.jar</strong></td>
      <td><code>9627829ea3f499f4d7d125e61111535ce8ec1347ff0e8d3ca3a6546adac92400</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Chromium-v1.2.0.zip</strong></td>
      <td><code>b384768b277f28d5c1727ae5303466ac5a59e7ef63e25eccddc81c07a25fbc7e</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Firefox-v1.2.0.xpi</strong></td>
      <td><code>e0b56e616a53ee008292040a98b65edaeef1e65e271198119ecbef05d4512e6d</code></td>
    </tr>
    <tr>
      <td><strong>retro-hifi.xdmskin</strong></td>
      <td><code>5e833a5e2fa403f0d602946442c64218f0e18f23ac995a178ded74f21c1b59f0</code></td>
    </tr>
    <tr>
      <td><strong>anime-sakura.xdmskin</strong></td>
      <td><code>43c3332d72db84025b5271682da4d628b88f992c040942b511dc7e9a831999bd</code></td>
    </tr>
    <tr>
      <td><strong>steampunk-brass.xdmskin</strong></td>
      <td><code>5c59e99971066a25e7998fd3923be8c99f9f5f78b8e9640004f2eb002d0d8cf1</code></td>
    </tr>
    <tr>
      <td><strong>modern-aurora.xdmskin</strong></td>
      <td><code>cdc94af5313fc58331343879540a3e641018541c91f21318b6bd8abfbcc0bbdf</code></td>
    </tr>
    <tr>
      <td><strong>synthwave-outrun.xdmskin</strong></td>
      <td><code>88e38ffab4a392faa3f28925b6c5b1b456444e321922713aacf223d534278bca</code></td>
    </tr>
    <tr>
      <td><strong>cyber-neon.xdmskin</strong></td>
      <td><code>684d2ff6bb77c337ab93b34347100e40a4b8c6b3800fb29c8bc16d47e0bb1e23</code></td>
    </tr>
    <tr>
      <td><strong>checksum-verifier.xdmplugin</strong></td>
      <td><code>e5abfa34e8b18f3bff45f835edbecfc838e4089050df729b81a54a6933f672ee</code></td>
    </tr>
    <tr>
      <td><strong>proxy-provider.xdmplugin</strong></td>
      <td><code>dd83ff8bc27adc4615ac928ba7ef4eeb8080c6a3996c8ab43281c6ec1a29ea22</code></td>
    </tr>
    <tr>
      <td><strong>vpn-status.xdmplugin</strong></td>
      <td><code>62406b0fa6b30ec08b941ac1223149d083013e98e1cc869f5cd0aabff7dbfec4</code></td>
    </tr>
    <tr>
      <td><strong>ui-notification.xdmplugin</strong></td>
      <td><code>5d252e11b9a56de2830913d5e4320ef8437e63ffd607a5d6b67202c24926bcc6</code></td>
    </tr>
    <tr>
      <td><strong>v2-sample-plugin.xdmplugin</strong></td>
      <td><code>a8190202d94c06c1fc5119102278bd1e097e12ef281170515980d1765c7c1195</code></td>
    </tr>
    <tr>
      <td><strong>ytdlp-downloader.xdmplugin</strong></td>
      <td><code>bde0b6288b4b695779605ecd8f44c32f96080d9363c4c9c1b31cdca94bd70dd8</code></td>
    </tr>
  </tbody>
</table>

---

## [1.1.0] — 2026-11-24

First stable release of the `1.1.x` line — graduating the **Modular Plugin SDK v2** (`xdm-plugin-api:1.1.0`, Specification 2.0.0) and syncing upstream core media muxing, HLS playlist capture, link refresh, IPv4 networking, and desktop accessibility fixes.

### Highlights & Key Improvements

- **Plugin SDK v2 Graduation & Bridge Fix (`xdm-plugin-api:1.1.0`):**
  - Stabilized the decoupled public SPI framework (`XdmPlugin`, `PluginDownloadEngine`, `ProxyProvider`, `VpnStatusProvider`, `PostDownloadProcessor`, `EventTopics`, `Registration`) with classloader isolation (`PluginFilteringClassLoader`), quarantine trust prompts, and `--safe-mode` / `--no-plugins` recovery flags.
  - Fixed a signed ID collision in `PluginDownloadBridge.submitPluginTask` where negative values from `CoreUtils.uniqueId()` could violate the `< 0` error-return convention expected by plugins such as `ytdlp-downloader`.
- **Media Muxing & Streaming Improvements:**
  - Ported upstream `xdm-core` muxer and codec handling fixes (Ogg, TS, MKV, MP4/ISO, AAC/Opus/FLAC/AC3/Vorbis, container commit paths).
  - Added browser extension captured HLS playlist body capture & caching (`hls-hook.js` / `hls-relay.js` in Chromium MV3 and `readPlaylistBody` in Firefox MV2) backed by `HlsPlaylistStore` and `PlaylistTextCache`.
  - Added the **Save streams as MP4** setting across settings UI, config persistence, and new video download dialogs.
- **Link Refresh, Source Tracking & Retry Resilience:**
  - Preserved `sourceUrl` and `sourceCookie` across captured downloads, New Download / Link Refresh dialogs, and `LinkRefresher`, with UI copy-page hints in `RefreshLinkWindow` and completion notes in `DownloadCompleteWindow`.
  - Surfaced server-busy retry ETAs (`retryAt`) in `ProgressWindow` and `HttpDownloader` / `RetryPolicy`.
- **Networking, Platform & Accessibility Hardening:**
  - Added early `Force IPv4` startup setting (`preferIPv4Stack`), `AssistiveTech` sanitization at startup (`jdk.accessibility`), XDG Desktop Portal `keepAwake` inhibition and `gdbus` shutdown fallback on Linux, and Dark/Light theme row & contrast refinements.

### Published Assets &amp; Checksums (`v1.1.0`)

Verify all release binaries against [`SHA256SUMS.txt`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.1.0/SHA256SUMS.txt) on the [GitHub Release](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/tag/v1.1.0):

<table class="checksum-table">
  <thead>
    <tr>
      <th style="width: 45%;">Asset Filename</th>
      <th>SHA-256 Checksum</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>XDM-Enhanced-Setup-v1.1.0-win-x64.exe</strong></td>
      <td><code>32a07432de279420934fad99c324ce65e23d402c33a2ef1e958364373373c4fa</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-v1.1.0-windows-x64.zip</strong></td>
      <td><code>24caf7a59901e9c46747d6ff6dd83de4216b8016f5c0fc12ea8ddbbd13831418</code></td>
    </tr>
    <tr>
      <td><strong>xdm-1.1.0-x64.msi</strong></td>
      <td><code>07e8c18dc0c7b13f241b5a45d5141b0b75a15789dcf5f04a1032f9c408cff88c</code></td>
    </tr>
    <tr>
      <td><strong>xdm-1.1.0-arm64.msi</strong></td>
      <td><code>c465e72b833bb3f5b0f940c9d1eef740d964502f0c745dd7de6a5c6a31f02a22</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0-arm64.dmg</strong></td>
      <td><code>4897a8a4b6800bcbb8f45443cf5346ff203d3a9f20c89cf6b6fe8735478d0252</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0-x64.dmg</strong></td>
      <td><code>75ef36ad1a9837811cd23e740411b78e91da7dd26e0b1a64765101c1fbb76646</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app_1.1.0_amd64.deb</strong></td>
      <td><code>e6f6af843acc3f3d67e639d7b64e9967ddecfce6e5ef5599f159b89acbf5bb16</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app_1.1.0_arm64.deb</strong></td>
      <td><code>f44d84357ed7c62e6b2b808e5bc90c474f741d3ed1c52bf0b026223024a81f0a</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0-1.x86_64.rpm</strong></td>
      <td><code>e660e4368da461b0013d8b03a24c5cde603a7e6f4a9c423534e8cc6f4ebc5df1</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0-1.aarch64.rpm</strong></td>
      <td><code>018d85134edd64b00641cec17d85e8daa948e74dac7c1eb9e9b5d7aed8e8f693</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0-1-x86_64.pkg.tar.zst</strong></td>
      <td><code>06ddb88e6859361bc7aca6fe7148d1029120e563a22068809ed10091cc79c025</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0-1-aarch64.pkg.tar.zst</strong></td>
      <td><code>5f448dd18c2f795cce362e8c3f4b2cd9b4b58ac981fbbd9c4aa8ca20c692d566</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0-linux-x64.tar.gz</strong></td>
      <td><code>1e99116c3e5d22d0500dcb26b980039c0538b267d527d68ea98badaff7ed0794</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0-linux-arm64.tar.gz</strong></td>
      <td><code>4fa802b7b790d86c605e62058c21597580d5baa3c4b1f5eef46c4fb33ac63c5b</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0.jar</strong></td>
      <td><code>6e0a23347d95187dee7f7f0883d1c55f3c778d7ba7e16b12125015474e5e19ff</code></td>
    </tr>
    <tr>
      <td><strong>xdm-plugin-api-1.1.0.jar</strong></td>
      <td><code>42edba3f3895853bca0e2450aa2355d2646f6e7f8de2debd1f7690b4830e58ea</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Chromium-v1.1.0.zip</strong></td>
      <td><code>884993cd0acd8b95543532b695dc3a9191294e20d594cbbad3dfcef5de166f0e</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Firefox-v1.1.0.xpi</strong></td>
      <td><code>b178768eca3835a483ccdddd5d3a2610827124cb78b24eefdaaac685a99a7814</code></td>
    </tr>
    <tr>
      <td><strong>checksum-verifier.xdmplugin</strong></td>
      <td><code>e5abfa34e8b18f3bff45f835edbecfc838e4089050df729b81a54a6933f672ee</code></td>
    </tr>
    <tr>
      <td><strong>proxy-provider.xdmplugin</strong></td>
      <td><code>dd83ff8bc27adc4615ac928ba7ef4eeb8080c6a3996c8ab43281c6ec1a29ea22</code></td>
    </tr>
    <tr>
      <td><strong>vpn-status.xdmplugin</strong></td>
      <td><code>62406b0fa6b30ec08b941ac1223149d083013e98e1cc869f5cd0aabff7dbfec4</code></td>
    </tr>
    <tr>
      <td><strong>ui-notification.xdmplugin</strong></td>
      <td><code>5d252e11b9a56de2830913d5e4320ef8437e63ffd607a5d6b67202c24926bcc6</code></td>
    </tr>
    <tr>
      <td><strong>v2-sample-plugin.xdmplugin</strong></td>
      <td><code>a8190202d94c06c1fc5119102278bd1e097e12ef281170515980d1765c7c1195</code></td>
    </tr>
    <tr>
      <td><strong>ytdlp-downloader.xdmplugin</strong></td>
      <td><code>bde0b6288b4b695779605ecd8f44c32f96080d9363c4c9c1b31cdca94bd70dd8</code></td>
    </tr>
    <tr>
      <td><strong>cyber-neon.xdmskin</strong></td>
      <td><code>476cbd73d0ae68105dc6eab00cfdf1c8e1ddf377e59677e301f4ec3191a85a29</code></td>
    </tr>
  </tbody>
</table>

---

## [1.1.0-beta.1] — 2026-11-20 (Archived Beta Preview)

Beta Preview Release of **XDM Enhanced** — introducing the **Modular Plugin SDK v2**, a modern, extensible service provider framework that empowers developers to build native download engines, proxy profile providers, VPN client integration listeners, and custom UI views—while preserving complete backward compatibility with all legacy v1 plugins and maintaining an honest, transparent security trust model.

### Highlights & Key Improvements

- **Decoupled Public SPI (`xdm-plugin-api`):**
  - Plugins compile exclusively against `xdm-plugin-api:1.0.2` (Specification 2.0.0) without access to internal host classes (`xdm.app.*`, `xdm.core.*`, `xdm.manager.*`).
  - Runtime classloaders enforce strict boundary isolation between host internals and plugin extensions.
- **Extended Service Provider Interfaces:**
  - `PluginDownloadEngine`: Register external or custom download engines (e.g., yt-dlp, aria2, FFmpeg streams) with first-class progress tracking, cancellation, and pause/resume lifecycle.
  - `PluginNetworkManager` & `ProxyProfile`: Programmatically supply authenticated SOCKS5/HTTP/HTTPS proxy configurations.
  - `PluginVpnIntegration`: Provide non-privileged status and connectivity monitoring for WireGuard and OpenVPN clients.
  - `PluginUiContext` & `PluginView`: Contribute custom tab views, side-panel widgets, and desktop toast notifications adhering to active Light, Dark, System, and custom FlatLaf skins.
  - `PluginSettings`: Type-safe configuration persistence with JSON backing and masked credential storage.
- **Transparent Legacy v1 Compatibility:**
  - Seamless coexistence via `LegacyPluginAdapter`. Legacy plugins targeting `xdm.plugin.Plugin` load and operate without recompilation or manifest modifications.
- **Honest Security Posture & Quarantine:**
  - In-process Java plugins execute with user OS account privileges. Newly discovered plugins default to `UNTRUSTED` quarantine until explicitly approved.
  - Extractors enforce strict zip-slip, symlink, archive bomb, and binary blacklists.
- **Modernized Plugin Manager UX:**
  - Zero dead buttons. Comprehensive status badges (`ACTIVE`, `DISABLED`, `QUARANTINED`, `CRASHED`, `RECOVERY_MODE`), toggle controls, and one-click diagnostic bundle export with credential scrubbing.
- **Fault Tolerance & Safe Mode:**
  - 10-second watchdog timers isolate plugin startup from the UI Event Dispatch Thread.
  - Starting with `--safe-mode` disables third-party plugins for disaster recovery.
- **Multi-Platform Native Installers:**
  - Windows: NSIS Setup `.exe`, portable `.zip`, and enterprise MSI installers for x64 and ARM64.
  - macOS: Universal DMG disk images for Apple Silicon (M1-M4) and Intel x64.
  - Linux: Native packages for Debian/Ubuntu (`.deb`), Fedora/RHEL (`.rpm`), Arch/Manjaro (`.pkg.tar.zst`), and portable tarball.
- **5 Standalone Compilable Samples:**
  - Reference projects located in `sample-plugins/`: `proxy-provider-sample`, `vpn-status-sample`, `ui-notification-sample`, `v2-sample-plugin`, and `ytdlp-downloader-plugin`.

### Published Assets & Checksums (`v1.1.0-beta.1`)

<table class="checksum-table">
  <thead>
    <tr>
      <th style="width: 45%;">Asset Filename</th>
      <th>SHA-256 Checksum</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>XDM-Enhanced-Setup-v1.1.0-beta.1-win-x64.exe</strong></td>
      <td><code>4af5dbf69780c5dec5f41fda351e996021d6bea1c5f5ff310c4cdf7ae1342f67</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-v1.1.0-beta.1-windows-x64.zip</strong></td>
      <td><code>f143832d3bb00e109331e7dfbd30ec15ddacdd3e7197c6b85b7c34609c44d598</code></td>
    </tr>
    <tr>
      <td><strong>xdm-1.1.0-beta.1-x64.msi</strong></td>
      <td><code>43c786304fcb838caa294faf4a21b5a5cb1ec1135b60b79f07ce6ed2d5ee5374</code></td>
    </tr>
    <tr>
      <td><strong>xdm-1.1.0-beta.1-arm64.msi</strong></td>
      <td><code>0fac3aed25ac275b36bd3923f0970903a59ed17acaa79cca1a15a43ebef55de5</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0-beta.1-arm64.dmg</strong></td>
      <td><code>8219c49bf70387faffb8ee8d7f6e5546f6744784b44edc96ab4ec47c63492a82</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0-beta.1-x64.dmg</strong></td>
      <td><code>9b0f81683087f546fbb00379c2fc71623025a7af02b4a817880a20f188fc3148</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app_1.1.0~beta.1_amd64.deb</strong></td>
      <td><code>3895cda9621cf80afc92577309d9ef50f35db48e448c3a47f035d17ba244b8ab</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app_1.1.0~beta.1_arm64.deb</strong></td>
      <td><code>6b5eaf69c8bb564f81411a33634a52b4324aa6726b02903f76e587bac7a4becb</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0~beta.1-1.x86_64.rpm</strong></td>
      <td><code>cf96cbb023204bd2d998c88e454ea794ec5a172c95ae0095474f05846d3e80ec</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0~beta.1-1.aarch64.rpm</strong></td>
      <td><code>3ebd2ee9dbf510251f57e60b275853c5e5e1f7f53ca762f025cd18ea0e912549</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0beta.1-1-x86_64.pkg.tar.zst</strong></td>
      <td><code>5caa0298a20ddec8d1e4757e47d8377c5aa7a62a472848ee3f0b99509895d84c</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0beta.1-1-aarch64.pkg.tar.zst</strong></td>
      <td><code>b4f842f6524f79ef28466fc2f7597dd3481b36cd706e28fe147fd5b30e5e3891</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0-beta.1-linux-x64.tar.gz</strong></td>
      <td><code>d32b4fed21abe4693549a3f0b7432c7f9b955241a9be3565a1332b2ca0265074</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0-beta.1-linux-arm64.tar.gz</strong></td>
      <td><code>b9cf91053d3d0b623ddb2739816b199ef15ac5c61d35b1135613533e943a9e31</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.1.0-beta.1.jar</strong></td>
      <td><code>63c2f259e2b43785aaa973e6c1f89696fa87c9d8fe7b4f911fcfa543591cd0d9</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Chromium-v1.1.0-beta.1.zip</strong></td>
      <td><code>67291d1d4ed133564ba4ff33781bc3fbddf1bd16467a99e90bf38930a9e385ce</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Firefox-v1.1.0-beta.1.xpi</strong></td>
      <td><code>f0a91ef0a6b8969fe6c2670b67a044f36a3cf5cab34755984690ad72679c32f6</code></td>
    </tr>
    <tr>
      <td><strong>checksum-verifier.xdmplugin</strong></td>
      <td><code>c8f915c32a984515db8991537089f9324be18ab5c67421d419c5d30a83582806</code></td>
    </tr>
    <tr>
      <td><strong>proxy-provider.xdmplugin</strong></td>
      <td><code>fa1eb3e2bc1ef54fe03ba1b3d9c8c8c03fe8585bbcc620ff5c15b6ae8b4d2755</code></td>
    </tr>
    <tr>
      <td><strong>vpn-status.xdmplugin</strong></td>
      <td><code>4da24225a5137efda49a35dfdfbdef04c8453a38324bd44e90f40ef0b1255f90</code></td>
    </tr>
    <tr>
      <td><strong>ui-notification.xdmplugin</strong></td>
      <td><code>d7dace8427aaea5883e4cc3e3e4763bb387754a6676e0c2f7db36b14f5b67a69</code></td>
    </tr>
    <tr>
      <td><strong>v2-sample-plugin.xdmplugin</strong></td>
      <td><code>7d94e132f25e76361e6356c8321edddd5d0a3f8132d8492fb578d03c175d95d5</code></td>
    </tr>
    <tr>
      <td><strong>ytdlp-downloader.xdmplugin</strong></td>
      <td><code>1a000e7b63d3c64d43571d24b6dcc17cb18540e9924dd370dadf832b5b652009</code></td>
    </tr>
    <tr>
      <td><strong>cyber-neon.xdmskin</strong></td>
      <td><code>476cbd73d0ae68105dc6eab00cfdf1c8e1ddf377e59677e301f4ec3191a85a29</code></td>
    </tr>
  </tbody>
</table>

---

## [1.0.2] — 2026-11-20 (Previous Stable Release)

### Highlights & Key Improvements

- **Scheduler Concurrency & Shutdown Safety:**
  - Synchronized all operations on `DownloadScheduler.entries` (`synchronized(entries)`), eliminating race conditions and `ConcurrentModificationException` during concurrent download schedule updates.
  - Added explicit `DownloadScheduler.stop()` invocation during `AppContext.shutdown()` to ensure background timer tasks terminate cleanly without thread leaks or JVM hanging.
- **Atomic Schedule Persistence:**
  - Serialized scheduled download job states strictly within the entries monitor, guaranteeing atomic persistence writes and preventing partial writes or corrupted schedule state during concurrent triggers.
- **BitTorrent Corrupt Piece Recovery (`HashFailedAlert`):**
  - Added explicit handling and dispatch of `HashFailedAlert` in the BitTorrent session lifecycle, ensuring corrupted pieces are immediately detected, dropped, re-requested, and logged without silent data corruption.
- **Browser Integration Diagnostic Details:**
  - Included loopback listener health status in diagnostic copy output when custom protocol handler registration is absent or restricted, eliminating diagnostic false negatives during troubleshooting.
- **Sustained Operation & 5,000-Record Scale Verification:**
  - Validated long-duration memory stability, zero thread leaks, responsive UI rendering under 5,000 active/completed download records, smooth skin/theme switching, and resilient recovery under simulated disk-full and network-severed faults.
- **Release Protection & CI Governance:**
  - Enforced CI release tag protection guards, preventing accidental branch-push overwrites and safeguarding immutable release tags.

### Published Assets & Checksums (`v1.0.2`)

<table class="checksum-table">
  <thead>
    <tr>
      <th style="width: 35%;">Asset Filename</th>
      <th style="width: 15%;">File Size</th>
      <th>SHA-256 Checksum</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>XDM-Enhanced-Setup-v1.0.2-win-x64.exe</strong></td>
      <td>64.0 MB</td>
      <td><code>1c2b03d1425bfc9415294a57ca29b9820b8eb42451e2da8b557a913159d3c6f5</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-v1.0.2-windows-x64.zip</strong></td>
      <td>74.8 MB</td>
      <td><code>46710f6b251b28ae0f94ceff98aedbcd0310c26a4ac1322819148d824184acf0</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.0.2.jar</strong></td>
      <td>95.1 MB</td>
      <td><code>c6b3191fd20b8bf4ef13611487a38738d20340a4995d97913cf48c5e6a17aebc</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Chromium-v1.0.2.zip</strong></td>
      <td>104.0 KB</td>
      <td><code>ad21a2275fcea5214ccc943168d8ce496c81416b2f8adca4edf65f770c90f790</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Firefox-v1.0.2.xpi</strong></td>
      <td>101.2 KB</td>
      <td><code>8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac</code></td>
    </tr>
    <tr>
      <td><strong>xdm-plugin-api-1.0.2.jar</strong></td>
      <td>22.0 KB</td>
      <td><code>f62619835012abe755763c7e4bd09e77c16c0259af145566321e263aba8520b3</code></td>
    </tr>
    <tr>
      <td><strong>checksum-verifier.xdmplugin</strong></td>
      <td>6.2 KB</td>
      <td><code>c8f915c32a984515db8991537089f9324be18ab5c67421d419c5d30a83582806</code></td>
    </tr>
    <tr>
      <td><strong>ytdlp-downloader.xdmplugin</strong></td>
      <td>28.6 KB</td>
      <td><code>8a484eff37e95d961e028c80b353ff84580d275a51e166860f5cfec29a5bf189</code></td>
    </tr>
    <tr>
      <td><strong>cyber-neon.xdmskin</strong></td>
      <td>34.4 KB</td>
      <td><code>5e8293da37215dbafff6a41a88495bdefcc0faa3f942c878ada69664ec05ef0f</code></td>
    </tr>
  </tbody>
</table>

---

## [1.0.1] — 2026-11-19

### Highlights & Key Improvements

- **Active Chunk Visualization & Live Connection Details:**
  - Added real-time chunk pulse animation in `SegmentVisualizationBar` to visually distinguish active receiving stream segments from idle or completed buffers.
  - Integrated detailed connection table (`SegmentDetailTable`) displaying real-time stream bounds, byte offsets, transfer rates, status flags, and IP addresses.
- **High-Frequency Telemetry & UI Responsiveness:**
  - Transitioned transfer rate calculation to a ~10ms moving-average sampling window with 100ms batched UI coalescing, preventing EDT thread congestion under gigabit download speeds while providing fluid transfer telemetry.
- **BitTorrent Modal Workspace Bounding:**
  - Strictly constrained the Add Torrent dialog within visible monitor workspace and restricted magnet link input field dimensions, preventing oversized modals when processing long magnet links.
  - Added a dedicated "Copy Source" action button for easy magnet URI copying.
- **Context Menu Action Parity:**
  - Unified `DownloadContextMenu` across Table View, Modern Cards View, and Classic View with left-click misfire guards.
- **NSIS License RTF Formatting:**
  - Fixed control word escaping (`\widowctrl\hyphauto`) in `gpl-3.0-notice.rtf`, preventing literal RTF tokens from rendering in the installer license screen.
- **Installer Process Management:**
  - Implemented directory-verified process detection, authenticated loopback graceful shutdown (`/system/shutdown`), interactive escalation prompts, and silent `/FORCE_CLOSE` support in NSIS installer.
- **Canonical Documentation & Help Destinations:**
  - Updated all application Help menus, sidebar items, and support links to canonical fork documentation (`https://jakepanlilio2000.github.io/XDM-Enhanced/`), issue trackers, and guides with URI scheme validation and fallback handlers.

### Published Assets & Checksums (`v1.0.1`)

<table class="checksum-table">
  <thead>
    <tr>
      <th style="width: 35%;">Asset Filename</th>
      <th style="width: 15%;">File Size</th>
      <th>SHA-256 Checksum</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>XDM-Enhanced-Setup-v1.0.1-win-x64.exe</strong></td>
      <td>64.0 MB</td>
      <td><code>68be3296248f3f7704dba8cf1945ecc0dda125ed9ba898e468c9bc207b8de76c</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-v1.0.1-windows-x64.zip</strong></td>
      <td>74.3 MB</td>
      <td><code>2fc4b10348f2eb198cdc01159d6d8fc7bd14eeabd3d3d991645902487a6537f1</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.0.1.jar</strong></td>
      <td>95.1 MB</td>
      <td><code>31870f5068bfb6f49ad260fff4a010c2bb2efa3a6df1573bb2bb84399d830011</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Chromium-v1.0.1.zip</strong></td>
      <td>106.5 KB</td>
      <td><code>6db9d41834af3decb57d4c36a13d16e0eb72c8441556d667e82b6fbd3d72b1cd</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Firefox-v1.0.1.xpi</strong></td>
      <td>103.6 KB</td>
      <td><code>8c3f2f1b90a113dbccd1ce771223779cb7dbcaa164bb024250a4e7b7ef9eeaac</code></td>
    </tr>
    <tr>
      <td><strong>xdm-plugin-api-1.0.1.jar</strong></td>
      <td>22.0 KB</td>
      <td><code>5636b47925d6957554fb5a4313af0e478fa1ac764d2c5cf30f5c30cba4de4cc7</code></td>
    </tr>
    <tr>
      <td><strong>checksum-verifier.xdmplugin</strong></td>
      <td>6.2 KB</td>
      <td><code>c8f915c32a984515db8991537089f9324be18ab5c67421d419c5d30a83582806</code></td>
    </tr>
    <tr>
      <td><strong>ytdlp-downloader.xdmplugin</strong></td>
      <td>28.6 KB</td>
      <td><code>8a484eff37e95d961e028c80b353ff84580d275a51e166860f5cfec29a5bf189</code></td>
    </tr>
    <tr>
      <td><strong>cyber-neon.xdmskin</strong></td>
      <td>34.4 KB</td>
      <td><code>5e8293da37215dbafff6a41a88495bdefcc0faa3f942c878ada69664ec05ef0f</code></td>
    </tr>
  </tbody>
</table>

---

## [1.0.0] — 2026-11-11 (Stable Release)

Official Stable Release of **XDM Enhanced** — high-speed multi-connection segmented HTTP/HTTPS downloading and streaming media capture with native BitTorrent and magnet support, modern FlatLaf dynamic theming, custom skin packages, a public plugin framework, automated post-download rules, and companion browser extensions.

- Includes all features, performance improvements, bug fixes, and security hardening developed through the v1.0.0 release cycle and verified against the 15-phase audit suites.

---

## [1.0.0-beta.6] — 2026-11-10 (Pre-release)

### Highlights & Key Improvements

- **Skin Background & Modal Customization System:**
  - Extended the declarative skin engine to support custom background wallpapers and colors for main windows, sidebars, transfer tables, and dialogs via `skin.json`.
  - Added `SkinBackgroundPanel` with support for image tiling, scaling, centering, and adjustable opacity.
  - Automatic contrast scrims protect text legibility across all modal dialogs.
  - Live skin hot-reloading with regression test coverage in `FullUiSkinContractTest`.
- **User-Controlled Clipboard Monitoring & Non-Intrusive Link Preflight:**
  - Strict privacy protections: only single HTTP/HTTPS URLs under 2,048 characters are parsed. Passwords, secret tokens, and multi-line blocks are discarded immediately.
  - Non-intrusive metadata preflight: performs lightweight HTTP `HEAD` probes (with `Range: bytes=0-0` fallback) to discover file name, Content-Length, Content-Type, and server resume support.
  - **Zero-Payload Guarantee:** Absolutely no payload data or segmented chunks are downloaded prior to explicit user confirmation.
  - Clean socket cancellation when dismissing the New Download dialog.
- **Public Plugin API v2 & Task Adapter Bridge:**
  - Introduced semantic version-range compatibility matching (`[1.0.0, 2.0.0)`) for third-party plugins.
  - Decoupled Plugin Event Bus (`PluginEventBus`) supporting publish-subscribe event dispatching.
  - Implemented public task adapter bridge (`PluginDownloadBridge`) routing plugin tasks into XDM Enhanced's central download scheduler without tight coupling to internal engine classes.
- **Central Download List Integration for yt-dlp Plugin:**
  - Fixed yt-dlp plugin downloads missing from the main download table/cards by routing tasks through the public task adapter bridge with stable IDs and `CAT_VIDEOS` categorization.
  - Real-time progress, speed, ETA, and status synchronization.
  - Safe subprocess execution via `ProcessBuilder` with explicit array argument passing to eliminate shell injection vulnerabilities.
- **Browser Connection Self-Diagnostics & Troubleshooting:**
  - Added self-diagnostics panel in extension options and error pages displaying live connection state, detected issues (`host_missing`, `permission_denied`, `app_not_running`), and a "Check Connection" retry button.
  - Safe desktop repair utility in Settings > Browser: checks and repairs Windows native messaging manifest paths and protocol handler registrations (`xdm-app://`).
  - Comprehensive technical troubleshooting guide: [Browser Troubleshooting Guide](/troubleshooting).

### Published Assets & Checksums (`v1.0.0-beta.6`)

<table class="checksum-table">
  <thead>
    <tr>
      <th style="width: 35%;">Asset Filename</th>
      <th style="width: 15%;">File Size</th>
      <th>SHA-256 Checksum</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>XDM-Enhanced-Setup-v1.0.0-beta.6-win-x64.exe</strong></td>
      <td>64.8 MB</td>
      <td><code>da95a6b3beda92a29357544dc448211f01d6c3ef48ca68182a0cd130a47d3636</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-v1.0.0-beta.6-windows-x64.zip</strong></td>
      <td>75.6 MB</td>
      <td><code>5bbb8edfd6d4d6a32540b309271c6b1cceefa0569f74f66c7bd2ef542876201d</code></td>
    </tr>
    <tr>
      <td><strong>xdm-app-1.0.0-beta.6.jar</strong></td>
      <td>3.8 MB</td>
      <td><code>5f14df837cab6ada72b3f8fad437ee73183f64d21238e2f1bf03b8ed80c26c9c</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Chromium-v1.0.0-beta.6.zip</strong></td>
      <td>106 KB</td>
      <td><code>0ee9bb3edf49e11d126eca200ad67b2938e2d33b306a493bc31821ad0977e981</code></td>
    </tr>
    <tr>
      <td><strong>XDM-Enhanced-Extension-Firefox-v1.0.0-beta.6.xpi</strong></td>
      <td>103 KB</td>
      <td><code>14dfc9637085267dc098c56d49b882e69bdb5cca22eaa9cf75b6d4471951feaa</code></td>
    </tr>
    <tr>
      <td><strong>xdm-plugin-api-1.0.0-beta.6.jar</strong></td>
      <td>22 KB</td>
      <td><code>ddec81692aa22354ff3f5df7c70bdd290c922e89d9077bf0c00ccff40d273db4</code></td>
    </tr>
    <tr>
      <td><strong>ytdlp-downloader.xdmplugin</strong></td>
      <td>28.4 KB</td>
      <td><code>0b6524b86d835c03948d0260598d31e27049e3292f6f67b569b369e95e5b0a19</code></td>
    </tr>
    <tr>
      <td><strong>checksum-verifier.xdmplugin</strong></td>
      <td>6.2 KB</td>
      <td><code>152766aa1ee2fc491cb06a7c4f36ee8e3fea95a6ffcbc856e8d44163da5b5a43</code></td>
    </tr>
    <tr>
      <td><strong>cyber-neon.xdmskin</strong></td>
      <td>34.4 KB</td>
      <td><code>74e80ededf6d7f41f29b5cfafa79a06bef8b53fc20b077523122ffaeaff2f213</code></td>
    </tr>
  </tbody>
</table>

---

## [1.0.0-beta.5] — 2026-11-10

- **Extensible Skin Engine & Custom Skin Packages (`.xdmskin`):**
  - Integrated skin engine supporting built-in skins (**XDM Enhanced** FlatLaf and **Original XDM** classic theme) alongside portable `.xdmskin` ZIP archives.
  - Custom window backgrounds, toolbar icons, category SVGs, buttons, fonts, border radii, and accent overrides.
  - Security-hardened archive validation defending against Zip Slip path traversal and prohibiting executable payloads.
  - Dedicated Skin Management interface in Settings > Appearance with preview cards, install/export, and live hot-reloading.
- **Core Plugin Framework & Public Developer API (`xdm-plugin-api`):**
  - Standalone Maven submodule defining the decoupled public SPI contract.
  - Dynamic discovery and loading of `.xdmplugin` archives.
  - Isolated classloader hierarchies preventing plugin conflicts.

---

## [1.0.0-beta.4] — 2026-11-09

- **BitTorrent 2.0 Engine Upgrades:**
  - Upgraded `libtorrent4j` native bindings for enhanced multi-peer swarm stability.
  - Improved magnet metadata resolution over DHT/PEX networks.
  - Added piece availability visualizer in the torrent details dialog.
- **Memory & Socket Optimizations:**
  - Reduced memory footprint during multi-segment transfers on gigabit connections.
  - Improved HTTP connection pooling and keep-alive socket reuse.

---

## [1.0.0-beta.3] — 2026-11-09

- **Browser Extension Improvements:**
  - Floating video sniffer button with Shadow DOM CSS isolation.
  - Multi-stream quality selector (4K, 1080p, 720p, 480p).
  - Improved loopback handshake reliability on `127.0.0.1:8597`.

---

## [1.0.0-beta.2] — 2026-11-09

- **Modern FlatLaf UI Overhaul:**
  - Introduced modern FlatLaf light and dark theme palettes.
  - Category sidebar navigation with badge counts for active transfers.
  - Compact Table View and Classic Cards view modes.

---

## [1.0.0-beta.1] — 2026-11-08

- **Initial Community Fork Release:**
  - Forked independently from Xtreme Download Manager by subhra74.
  - Modularized project structure into Maven submodules.
  - Initial Liberica JDK 25 bundling and modern NSIS packaging pipeline.

