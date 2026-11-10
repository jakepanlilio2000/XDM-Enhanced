# Browser Extensions & Integration

The **XDM Enhanced Companion Extension** enables automatic link capture, video streaming sniffer integration, and one-click batch queuing between your browser and the desktop client.

---

## 1. Supported Browsers

<div class="feature-grid">
  <div class="card-box">
    <h3>Google Chrome & Chromium</h3>
    <p>Supports Google Chrome, Brave, Microsoft Edge, Opera, Vivaldi, and Arc. Implemented using <strong>Manifest V3</strong> with service-worker event routing.</p>
    <div style="margin-top: 1rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/XDM-Enhanced-Extension-Chromium-v1.0.0-beta.6.zip" class="btn-primary" style="font-size: 0.85rem;">Download Chromium Extension</a>
    </div>
  </div>

  <div class="card-box">
    <h3>Mozilla Firefox</h3>
    <p>Supports Mozilla Firefox (109+), LibreWolf, Waterfox, and Firefox ESR. Implemented using signed <strong>Manifest V2</strong> for maximum media streaming compatibility.</p>
    <div style="margin-top: 1rem;">
      <a href="https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/XDM-Enhanced-Extension-Firefox-v1.0.0-beta.6.xpi" class="btn-primary" style="font-size: 0.85rem;">Download Firefox Add-on (.XPI)</a>
    </div>
  </div>
</div>

---

## 2. Installation Walkthrough

### Chromium Browsers (Chrome, Edge, Brave, etc.)

1. Download [`XDM-Enhanced-Extension-Chromium-v1.0.0-beta.6.zip`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/XDM-Enhanced-Extension-Chromium-v1.0.0-beta.6.zip).
2. Unzip the archive into a permanent folder (e.g., `Documents\XDM-Extension`).
3. Open your browser and navigate to `chrome://extensions` (or `edge://extensions`).
4. Enable **Developer mode** using the toggle in the top-right corner.
5. Click **Load unpacked** and select the unzipped directory.
6. The XDM Enhanced icon will appear in your extensions toolbar. Pin it for quick access.

### Mozilla Firefox

1. Download [`XDM-Enhanced-Extension-Firefox-v1.0.0-beta.6.xpi`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/XDM-Enhanced-Extension-Firefox-v1.0.0-beta.6.xpi).
2. Open Firefox and navigate to `about:addons`.
3. Click the gear icon (⚙️) in the top-right and choose **Install Add-on From File...**.
4. Select the downloaded `.xpi` file and confirm the permission prompt.

---

## 3. Architecture & Privacy Guarantees

```
[ Web Browser ]
      │
      ├── Browser Extension (Isolated MV3 / MV2 Sandbox)
      │         │
      │         └── Local HTTP Handshake (http://127.0.0.1:8597)
      │                   │
      │                   ├── GET  /sync       (Health check & settings sync)
      │                   ├── POST /download   (Send captured download URL)
      │                   └── POST /media      (Send streaming video streams)
      ▼
[ Desktop Application ]
```

### Localhost Only Guarantee
- **Zero Cloud Communication:** The extension communicates exclusively with `127.0.0.1:8597` on your local computer. No browsing history, page contents, cookies, or download metadata are ever transmitted to any third-party or remote telemetry server.
- **DNS Rebinding Defense:** The desktop server validates the `Host` and `Origin` headers of all incoming HTTP requests. Any connection attempting DNS rebinding or origin spoofing receives an immediate `403 Forbidden` response.
- **Transient Memory Storage:** Captured media streams and URLs are held in browser extension memory only while the active tab is open.

---

## 4. Permissions Audit

The extension requests the following browser permissions:

| Permission | Purpose | Privacy Impact |
|:---|:---|:---|
| `webRequest` | Detects initiated downloads and intercepts media segments. | Evaluated locally; no external transmission. |
| `nativeMessaging` | Allows communication with the XDM desktop host launcher. | Local process communication only. |
| `contextMenus` | Adds "Download with XDM Enhanced" to right-click links. | Passive menu trigger. |
| `storage` | Saves user preferences (e.g. video button position). | Stored in local browser storage. |
| `activeTab` | Anchors floating video sniffer buttons to HTML5 players. | Scoped solely to the active video element. |

---

## 5. Troubleshooting Connectivity

If your extension displays a red **"Disconnected"** status:
1. Ensure XDM Enhanced desktop application is running in your taskbar / system tray.
2. Open the extension popup and click **Recheck Connection**.
3. If unresolved, consult the comprehensive [Troubleshooting & Diagnostics Guide](/troubleshooting) for self-repair steps.

