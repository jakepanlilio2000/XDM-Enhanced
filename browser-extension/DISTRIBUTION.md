# XDM Enhanced — Browser Extension Distribution & Update Architecture

This document specifies the supported update channels, manifest specifications, browser security constraints, and manual/automated distribution procedures for the XDM Enhanced browser extensions.

**Repository:** `https://github.com/jakepanlilio2000/XDM-Enhanced`  
**Official Release Feed:** `https://github.com/jakepanlilio2000/XDM-Enhanced/releases`  

---

## 1. Chromium Extension (Manifest V3)

### Supported Channels & Mechanisms

| Channel | Distribution Mechanism | Update Mechanism | User Intervention | Platform Policies |
| :--- | :--- | :--- | :--- | :--- |
| **Chrome Web Store** | Web Store listing | Automatic background update via Google Web Store | None | Fully compliant with Google Web Store Developer policies. |
| **Edge Add-ons** | Microsoft Edge Add-on listing | Automatic background update via Edge Store | None | Fully compliant with Microsoft Edge Add-ons policies. |
| **Developer / Unpacked** | GitHub Release archive (`.zip`) | In-extension release check + manual folder update | Download release `.zip`, replace directory files, click **Reload** in `chrome://extensions` | Windows consumer Chrome prohibits automatic `.crx` installs from outside the Web Store. Developer mode is required. |
| **Enterprise / Managed GPO** | Self-hosted HTTPS server | Policy-driven update check via `chrome-updates.xml` | None (managed by IT administrator) | Configured via Windows Registry / Group Policy using `ExtensionInstallForcelist` and `ExtensionInstallSources`. |

### Chromium Enterprise Update Manifest (`chrome-updates.xml`)

For enterprise networks deploying XDM Enhanced via Windows Active Directory GPO or Chrome enterprise policy:

```xml
<?xml version='1.0' encoding='UTF-8'?>
<gupdate xmlns='http://www.google.com/update2/response' protocol='2.0'>
  <app appid='xdmenhancedchromebrowserintegration0'>
    <updatecheck codebase='https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.5/XDM-Enhanced-Extension-Chromium-v1.0.0-beta.5.crx' version='3.5.3' />
  </app>
</gupdate>
```

#### Windows Registry GPO Configuration
- Key: `HKEY_LOCAL_MACHINE\SOFTWARE\Policies\Google\Chrome\ExtensionInstallForcelist`
- Value: `1` = `xdmenhancedchromebrowserintegration0;https://raw.githubusercontent.com/jakepanlilio2000/XDM-Enhanced/master/browser-extension/chrome-updates.xml`
- Key: `HKEY_LOCAL_MACHINE\SOFTWARE\Policies\Google\Chrome\ExtensionInstallSources`
- Value: `1` = `https://github.com/jakepanlilio2000/XDM-Enhanced/*`

### Windows Consumer Restrictions
On consumer Windows installations of Google Chrome (unmanaged), Google strictly enforces the requirement that extensions installed from outside the Chrome Web Store must be loaded unpacked via Developer Mode. Direct installation of `.crx` files from arbitrary GitHub URLs is blocked by Chrome security architecture. The extension therefore provides an in-popup and in-settings update detector that links directly to the latest release assets.

---

## 2. Firefox Extension (Manifest V2)

### Supported Channels & Mechanisms

| Channel | Distribution Mechanism | Update Mechanism | User Intervention | Signing / Security Requirements |
| :--- | :--- | :--- | :--- | :--- |
| **Mozilla Add-ons (AMO)** | AMO directory listing | Automatic background updates via AMO | None | Signed by Mozilla AMO during review. |
| **Self-Hosted Self-Distribution** | GitHub Releases (`.xpi`) | Mozilla update manifest (`updates.json`) via `browser_specific_settings.gecko.update_url` | Automatic update check in `about:addons` | **MANDATORY:** Firefox Release channel strictly requires Mozilla AMO unlisted signing (`web-ext sign`). Unsigned XPIs are rejected. |
| **Developer Edition / Nightly** | Direct `.xpi` download | Manual install or `updates.json` | Download & install `.xpi` | Allowed with `xpinstall.signatures.required = false` in `about:config`. |
| **Temporary Add-on** | Unpacked folder in `about:debugging` | Reload button in `about:debugging` | Click **Reload** | Unsigned extension runs until browser restarts. |

### Firefox Manifest Gecko Settings

In `manifest.json`:
```json
"browser_specific_settings": {
    "gecko": {
        "id": "xdm-enhanced-browser-monitor@jakepanlilio2000.github.io",
        "strict_min_version": "109.0",
        "update_url": "https://raw.githubusercontent.com/jakepanlilio2000/XDM-Enhanced/master/browser-extension/updates.json",
        "data_collection_permissions": {
            "required": [
                "none"
            ]
        }
    }
}
```

### Firefox Update Manifest (`updates.json`)

Hosted in repository at:
`https://raw.githubusercontent.com/jakepanlilio2000/XDM-Enhanced/master/browser-extension/updates.json`

```json
{
  "addons": {
    "xdm-enhanced-browser-monitor@jakepanlilio2000.github.io": {
      "updates": [
        {
          "version": "1.1.3",
          "update_link": "https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.5/XDM-Enhanced-Extension-Firefox-v1.0.0-beta.5.xpi",
          "applications": {
            "gecko": {
              "strict_min_version": "109.0"
            }
          }
        }
      ]
    }
  }
}
```

### Mozilla AMO Signing Workflow for Fork Releases
To sign self-distributed `.xpi` releases for standard Firefox users:
1. Create AMO API credentials at `https://addons.mozilla.org/developers/addon/api/key/`.
2. Package the extension: `powershell -File browser-extension/package-extensions.ps1`.
3. Sign using `web-ext`:
   ```bash
   npx web-ext sign --source-dir browser-extension/firefox-extension --api-key=$AMO_JWT_ISSUER --api-secret=$AMO_JWT_SECRET --channel=unlisted
   ```
4. Attach the generated signed `.xpi` to the GitHub Release.

---

## 3. Protocol Compatibility Matrix

The extension and desktop application exchange version identifiers across local loopback (`http://127.0.0.1:8597`):

| Component | Identifier Header / Field | Baseline | Compatible Range | Behavior on Mismatch |
| :--- | :--- | :--- | :--- | :--- |
| **Chromium Extension** | `X-XDM-Extension: chrome/3.5.2` | `3.5.2` | `3.5`+ | Desktop triggers `ExtensionUpdatePanel` if `< 3.5`. Extension warns if desktop `< 1.0.0`. |
| **Firefox Extension** | `X-XDM-Extension: firefox/1.1.2` | `1.1.2` | `1.1`+ | Desktop triggers `ExtensionUpdatePanel` if `< 1.1`. Extension warns if desktop `< 1.0.0`. |
| **Desktop Application** | `ConfigDto.appVersion` | `1.0.0-beta.3` | `1.0.0`+ | Sent in every `/sync` and long poll reply. |
| **Protocol Version** | `ConfigDto.protocolVersion` | `1` | `1` | Strict JSON schema handling with `ignoreUnknownKeys = true`. |

---

## 4. In-Extension Update Checks

Both extension popups and options pages include an active update detector:
- Direct queries to the GitHub Releases API (`https://api.github.com/repos/jakepanlilio2000/XDM-Enhanced/releases?per_page=5`).
- Clean SemVer comparison distinguishing release candidates, betas, and final releases.
- Safe DOM updates using strict `textContent` assignment (zero `eval`, zero inline script injection).
- Automatic caching in `chrome.storage.local` to respect GitHub API rate limits.

