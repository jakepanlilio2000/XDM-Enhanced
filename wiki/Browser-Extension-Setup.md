# Browser Extension Setup Guide

The **XDM Enhanced Companion Browser Extension** provides automatic one-click link interception, streaming video sniffer integration, and session cookie forwarding.

---

## 1. Prerequisites

- XDM Enhanced desktop client is running in the background ([[Installation Guide|Installation-Guide]]).
- A supported browser:
  - **Chromium-based:** Google Chrome, Microsoft Edge, Brave, Opera, Vivaldi, Arc.
  - **Gecko-based:** Mozilla Firefox (109+), LibreWolf, Waterfox, Firefox ESR.

---

## 2. Installation Walkthrough

### Chromium Browsers (Chrome, Edge, Brave, etc.)

1. Download [`XDM-Enhanced-Extension-Chromium-v1.0.0-beta.6.zip`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/XDM-Enhanced-Extension-Chromium-v1.0.0-beta.6.zip).
2. Extract the archive into a permanent folder (e.g. `C:\Users\<YourUser>\Documents\XDM-Extension`).
3. Open your browser and navigate to the extensions page:
   - Chrome: `chrome://extensions`
   - Edge: `edge://extensions`
   - Brave: `brave://extensions`
4. Toggle **Developer mode** in the top-right corner to **ON**.
5. Click **Load unpacked** (top-left) and select the extracted folder.
6. Click the extension puzzle icon in the browser toolbar and pin **XDM Enhanced** for quick access.

---

### Mozilla Firefox

1. Download [`XDM-Enhanced-Extension-Firefox-v1.0.0-beta.6.xpi`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/XDM-Enhanced-Extension-Firefox-v1.0.0-beta.6.xpi).
2. Open Firefox and navigate to `about:addons`.
3. Click the gear icon (⚙️) above your installed extensions and choose **Install Add-on From File...**.
4. Select the downloaded `.xpi` file and click **Add** when prompted to confirm permissions.

---

## 3. Verifying the Connection

1. Click the XDM Enhanced icon in your browser toolbar to open the extension popup.
2. Confirm the status header displays a green **"Connected"** indicator:
   - **Protocol:** HTTP Loopback (`127.0.0.1:8597`)
   - **Desktop Version:** `v1.0.0-beta.6`
3. Click **Settings** in the extension popup to customize video sniffer button placement and minimum video size thresholds.

---

## 4. Using the Floating Video Downloader

1. Navigate to any website containing HTML5 video players.
2. Begin playing a video stream.
3. A non-intrusive floating **Download Video** button appears anchored to the player:
   - Click the button to inspect detected streaming streams (MP4, WebM, HLS/DASH).
   - Select your preferred resolution (e.g., *1080p*, *720p*, *Audio Only*).
4. XDM Enhanced desktop immediately opens the New Download dialog with the authenticated stream URL and cookies pre-populated.

---

## 5. Privacy & Architecture Guarantees

- **Localhost Only:** The extension communicates strictly with `http://127.0.0.1:8597`. No URLs, cookies, browsing history, or personal data are ever sent to cloud servers or third parties.
- **DNS Rebinding Defense:** The desktop server strictly validates incoming `Host` and `Origin` headers, returning `403 Forbidden` for any non-local requests.

---

## 6. Common Failure States & Solutions

| Failure Symptom | Cause | Solution |
|:---|:---|:---|
| **Extension shows red "Disconnected"** | Desktop app not running or port 8597 blocked | See [Browser Extension Disconnected](Browser-Extension-Disconnected) for the recovery checklist. |
| **Download button does not appear on video** | Encrypted stream (DRM) or video is too short | DRM-protected media (Widevine/FairPlay) cannot be intercepted. Check extension threshold settings. |
| **Browser prompts "Download blocked"** | Native browser download conflict | Disable other competing download manager extensions. |

---

## Related Articles & Next Steps

* [[Browser Extension Disconnected|Browser-Extension-Disconnected]]: Complete self-repair guide for connectivity issues.
* [[Clipboard Monitoring & Privacy|Clipboard-Monitoring-and-Privacy]]: Alternative URL capture without browser extensions.
* [[Troubleshooting by Symptom|Troubleshooting-by-Symptom]]: Common browser handoff failure resolutions.

