# Browser Connection Troubleshooting & Self-Diagnostics Guide

This guide provides end-user troubleshooting steps, technical self-diagnostics, and developer debugging procedures for resolving connectivity issues between the **XDM Enhanced Browser Extension** and the **XDM Enhanced Desktop Application**.

---

## 1. Quick Diagnostic Checklist

If your browser extension shows **"XDM Enhanced Disconnected"**, follow these 4 steps first:

1. **Check if XDM Enhanced is running:** Verify the XDM Enhanced tray icon or main window is active.
2. **Click "Recheck Connection":** Open the extension popup in your browser toolbar and click **Recheck Connection**.
3. **Run Desktop Self-Repair:** Open XDM Enhanced Desktop &rarr; **Settings** &rarr; **Browser** &rarr; **Diagnostics & Repair** &rarr; click **Repair Integration**.
4. **Reload the Extension:** Go to `chrome://extensions` or `about:addons`, find XDM Enhanced, and toggle it off and on (or click the reload icon).

---

## 2. Architecture & Privacy Guarantee

XDM Enhanced uses a secure, local-only communication pipeline:

```
[ Web Browser ]
   │
   ├─► Browser Extension (MV3 / MV2)
   │     │
   │     ├─► Loopback HTTP Handshake (http://127.0.0.1:8597)
   │     │     ├─► GET  /sync       (Health check & configuration exchange)
   │     │     ├─► POST /download   (Send captured download URL & metadata)
   │     │     ├─► POST /media      (Send captured audio/video streams)
   │     │     └─► POST /poll       (Long-poll event channel for instant sync)
   │     │
   │     └─► Application Launch Protocol (xdm-app://launch)
   │           └─► Launches or activates desktop application when closed
   │
[ Desktop Application (127.0.0.1:8597) ]
```

### Privacy & Security Guarantees
- **100% Local Loopback:** All communication occurs exclusively over `127.0.0.1`. The extension never transmits download history, captured media, cookies, or URLs to any external remote server.
- **DNS Rebinding Protection:** The desktop service validates the `Host` header (`127.0.0.1` or `localhost` only). Malicious external websites attempting DNS rebinding attacks receive HTTP 403 Forbidden.
- **Strict Origin Filtering:** State-changing endpoints (`/download`, `/media`, `/vid`, `/batch`) accept requests only from recognized browser extension origins (`chrome-extension://`, `moz-extension://`). Requests from arbitrary web pages are rejected.
- **No Background Native Messaging Dependencies:** Communication uses standardized loopback HTTP and URL protocol handlers, avoiding brittle binary native messaging manifests that fail across browser profiles and portable installations.

---

## 3. Supported Browser & OS Matrix

| Browser Family | Supported Browsers | Extension Type | Minimum Version | Protocol Handler Support |
| :--- | :--- | :--- | :--- | :--- |
| **Chromium** | Google Chrome, Microsoft Edge, Brave, Opera, Vivaldi, Arc | Manifest V3 (MV3) | Chrome 88+ | `xdm-app://launch` |
| **Firefox** | Mozilla Firefox, Floorp, Zen, LibreWolf, Waterfox | Manifest V2 (MV2) | Firefox 78+ | `xdm-app://launch` |

| Operating System | Supported Versions | Architecture | User-Level Protocol Registration |
| :--- | :--- | :--- | :--- |
| **Windows** | Windows 10, Windows 11, Windows Server 2016+ | x64, ARM64 | `HKCU\Software\Classes\xdm-app` (No elevation required) |
| **Linux** | Ubuntu/Debian, Fedora/RHEL, Arch Linux, openSUSE | x64, aarch64 | `~/.local/share/applications/xdm-app.desktop` (`x-scheme-handler/xdm-app`) |
| **macOS** | macOS 11 Big Sur through macOS 15 Sequoia | Apple Silicon, Intel | App Bundle URL Scheme (`CFBundleURLTypes`) |

---

## 4. Common Error States & Solutions

### Error 1: "XDM Enhanced Disconnected" (`desktop_unavailable`)
- **Symptoms:** Extension toolbar icon shows a disconnected status badge; popup banner says "Desktop application is not running."
- **Root Causes:**
  1. XDM Enhanced desktop application is closed.
  2. The desktop loopback listener on port 8597 failed to bind or was blocked by firewall.
- **Solution:**
  1. Start XDM Enhanced from your Start Menu, Applications launcher, or by clicking **Launch XDM** in the popup.
  2. Once the application is running, click **Recheck Connection**.
  3. If XDM is already running, verify port 8597 availability (see [Advanced Diagnostics](#5-advanced-developer-diagnostics)).

### Error 2: "Permission Denied" (`permission_denied` / HTTP 403)
- **Symptoms:** Extension reports "Local loopback permission denied" or status code HTTP 403.
- **Root Causes:**
  1. Chromium browser policy has restricted host permissions for `http://127.0.0.1:8597/*`.
  2. A third-party browser privacy extension or corporate policy blocks loopback connections.
- **Solution:**
  1. In Chrome/Edge/Brave, go to `chrome://extensions` &rarr; click **Details** on XDM Enhanced.
  2. Under **Site access**, ensure access is set to **On all sites** or specifically allows `http://127.0.0.1:8597/*`.
  3. Under **Permissions**, verify that "Access your data for all websites" or loopback communication is enabled.

### Error 3: "Connection Timed Out" (`connection_timeout`)
- **Symptoms:** Extension reports connection timed out after 3 seconds.
- **Root Causes:**
  1. The desktop application is temporarily unresponsive due to heavy disk I/O, large download assembly, or system hibernation resume.
  2. A local antivirus proxy or network filtering driver delayed the loopback socket.
- **Solution:**
  1. The extension automatically attempts recovery with exponential backoff (1s, 2s, 4s, 8s, 16s, max 30s).
  2. Click **Recheck Connection** in the popup once system activity normalizes.

### Error 4: "Incompatible Desktop Version" (`incompatible_version`)
- **Symptoms:** Extension shows warning: "Please update XDM Enhanced (v1.0.0 or newer required)."
- **Root Causes:**
  1. An old legacy version of XDM (such as XDM 7.2 or older upstream builds) is running and listening on port 8597.
- **Solution:**
  1. Exit the legacy XDM process completely.
  2. Download and run the latest **XDM Enhanced** release from [GitHub Releases](https://github.com/jakepanlilio2000/XDM-Enhanced/releases).

### Error 5: "Launch Failed" (`launch_failure`)
- **Symptoms:** Clicking "Launch XDM" in the popup fails to open the application.
- **Root Causes:**
  1. The `xdm-app://` protocol handler is missing from the operating system registry or points to a moved/deleted executable path.
- **Solution:**
  1. Open XDM Enhanced manually from your desktop or start menu.
  2. Go to **Settings** &rarr; **Browser** &rarr; **Self-Diagnostics & Repair**.
  3. Click **Repair Integration**. This registers `HKCU\Software\Classes\xdm-app` for your user profile without requiring administrator elevation.

### Error 6: "Endpoint Not Found" (`host_missing` / HTTP 404)
- **Symptoms:** Extension connects to `127.0.0.1:8597` but receives HTTP 404 responses.
- **Root Causes:**
  1. Another local service or development server is listening on port 8597.
- **Solution:**
  1. Identify the conflicting process using `netstat -ano | findstr 8597` (Windows) or `ss -tulpn | grep 8597` (Linux).
  2. Reconfigure the conflicting service or stop it before launching XDM Enhanced.

---

## 5. Advanced Developer Diagnostics

When troubleshooting complex environment issues, use the following command-line tools:

### Step 1: Probe Loopback HTTP Handshake
Test whether the desktop HTTP server is accepting connections:
```bash
curl -v http://127.0.0.1:8597/sync
```
**Expected response:**
```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8

{"enabled":true,"fileExts":[...],"instanceId":"...","appVersion":"1.0.0-beta.6",...}
```

### Step 2: Verify Listening Port
Check which process is listening on port 8597:

**Windows:**
```powershell
netstat -ano | findstr :8597
# If listening, find the process name:
Get-Process -Id (Get-NetTCPConnection -LocalPort 8597).OwningProcess
```

**Linux:**
```bash
ss -tulpn | grep :8597
# or
lsof -i :8597
```

**macOS:**
```bash
lsof -nP -iTCP:8597 -sTCP:LISTEN
```

### Step 3: Inspect Protocol Handler Registration

**Windows (PowerShell):**
```powershell
# Inspect the protocol open command:
Get-ItemProperty -Path "HKCU:\Software\Classes\xdm-app\shell\open\command" -Name "(default)"
```
Expected output:
```text
(default) : "C:\Program Files\XDM Enhanced\xdm-app.exe" "%1"
```

**Linux:**
```bash
# Query default handler for xdm-app:
xdg-mime query default x-scheme-handler/xdm-app
# Expected: xdm-app.desktop
```

### Step 4: Inspect Extension Service Worker Logs

**Chromium (Chrome, Edge, Brave, Opera):**
1. Navigate to `chrome://extensions`.
2. Enable **Developer mode** (top-right toggle).
3. Under **XDM Enhanced - Browser Integration**, click **Inspect views: service worker**.
4. In the DevTools Console, inspect live connector logs:
   - `[XDM] tryConnect(): attempting http://127.0.0.1:8597/sync`
   - `[XDM] Connected to XDM Enhanced v1.0.0-beta.6`
   - Look for any `ERR_CONNECTION_REFUSED` or timeout exceptions.

**Firefox:**
1. Navigate to `about:debugging#/runtime/this-firefox`.
2. Find **XDM Enhanced - Browser Integration** and click **Inspect**.
3. In the Console tab, inspect background worker messages and connection events.

---

## 6. Safe Desktop Self-Repair

XDM Enhanced includes a built-in repair utility accessible from the desktop UI:

1. Open **XDM Enhanced**.
2. Click the gear icon (**Settings**) in the top navigation bar.
3. Select **Browser** from the settings sidebar.
4. Locate the **Integration Diagnostics & Repair** section.
5. Review the live status indicators:
   - **Port 8597:** Active / Closed
   - **Protocol:** Registered / Missing
   - **Active browser(s):** Number of connected browser instances
6. Click **Repair Integration** to safely restore user-level protocol associations.

> [!NOTE]
> The repair operation writes strictly to `HKEY_CURRENT_USER\Software\Classes\xdm-app` on Windows. It never touches machine-wide registry keys (`HKLM`/`HKCR`), requires no administrative elevation, and never modifies browser security policies or files.

---

## 7. Clean Reinstallation & Upgrade Guide

If upgrading between major versions or repairing a corrupted browser installation:

### Chromium Clean Reinstall
1. Open `chrome://extensions` and click **Remove** on XDM Enhanced.
2. Download the latest `XDM-Enhanced-Extension-Chromium-v*.zip` from [GitHub Releases](https://github.com/jakepanlilio2000/XDM-Enhanced/releases).
3. Extract the ZIP file to a permanent folder (e.g., `Documents\XDM-Extension`).
4. In `chrome://extensions`, ensure **Developer mode** is enabled, click **Load unpacked**, and select the extracted folder.
5. Pin the extension to your toolbar and click **Recheck Connection**.

### Firefox Clean Reinstall
1. Open `about:addons`, click the three dots next to XDM Enhanced, and select **Remove**.
2. Download `XDM-Enhanced-Extension-Firefox-v*.xpi` from [GitHub Releases](https://github.com/jakepanlilio2000/XDM-Enhanced/releases).
3. In `about:addons`, click the gear icon &rarr; **Install Add-on From File…** &rarr; select the downloaded `.xpi`.
4. Grant the requested extension permissions and verify the active connection.

---

## 8. Reporting Issues & Log Sanitization

If you encounter an issue not resolved by this guide, please file a report on the [XDM Enhanced GitHub Issue Tracker](https://github.com/jakepanlilio2000/XDM-Enhanced/issues).

### Where to Find Logs
- **Windows:** `%APPDATA%\xdm-app\logs\`
- **Linux:** `~/.config/xdm-app/logs/` or `~/.local/share/xdm-app/logs/`
- **macOS:** `~/Library/Application Support/xdm-app/logs/`

### Sanitization Requirements
> [!CAUTION]
> Always inspect and redact sensitive data before sharing logs publicly:
> - **Redact Download URLs:** Replace confidential file download URLs with `https://example.com/file.zip`.
> - **Redact Passwords & Tokens:** Remove any `Authorization: Bearer ...` or cookie values.
> - **Redact Usernames & Paths:** Replace personal username paths (e.g., `C:\Users\YourRealName\...`) with `C:\Users\user\...`.

