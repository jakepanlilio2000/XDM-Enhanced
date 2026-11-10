# Browser Integration & Native Messaging Technical Overview

Technical architecture and protocol specifications for the local communication bridge connecting companion browser extensions with the **XDM Enhanced** desktop application.

---

## 1. Dual-Channel Bridge Architecture

XDM Enhanced uses a resilient two-tier architecture for browser integration:

```
[ Browser Extension (MV3 / MV2) ]
          │
          ├── Primary Channel: Local HTTP Loopback (http://127.0.0.1:8597)
          │     ├── Fast, asynchronous JSON exchange
          │     ├── Health check & settings synchronization
          │     └── Streaming media sniffer payload transfer
          │
          └── Fallback Channel: Native Messaging Bridge (xdm_bridge)
                ├── Process-level stdin/stdout binary framing
                └── Auto-launches desktop application if not currently running
```

---

## 2. Local Loopback HTTP Specification

- **Address:** `http://127.0.0.1:8597`
- **Origin & Host Security:** The embedded lightweight HTTP server strictly validates the incoming `Host` and `Origin` headers. Requests originating from external domains or public IPs receive an immediate `403 Forbidden` to prevent DNS rebinding attacks.

### Endpoints:

#### `GET /sync`
- Probes server health and retrieves active desktop configuration.
- **Response (200 OK):**
  ```json
  {
    "status": "ready",
    "version": "1.0.0-beta.6",
    "port": 8597,
    "fileTypes": [".zip", ".rar", ".exe", ".iso", ".mp4", ".mkv"],
    "blockedSites": ["*.bank.example.com"]
  }
  ```

#### `POST /download`
- Receives intercepted links and dispatches the New Download dialog.
- **Request Payload:**
  ```json
  {
    "url": "https://example.com/file.zip",
    "fileName": "file.zip",
    "cookies": "SESSIONID=xyz...",
    "userAgent": "Mozilla/5.0 ...",
    "referer": "https://example.com/download.html"
  }
  ```

#### `POST /media`
- Transmits detected HTML5 audio/video stream segments to the floating sniffer overlay.

---

## 3. Native Messaging Host Configuration

### Native Messaging Manifest (`xdm_bridge.json`)
```json
{
  "name": "xdm_bridge",
  "description": "XDM Enhanced Native Messaging Host",
  "path": "C:\\Program Files\\XDM-Enhanced\\xdm-app.exe",
  "type": "stdio",
  "allowed_origins": [
    "chrome-extension://fcefkckpnkfamocgfheigchbngjkkkme/",
    "chrome-extension://kgpckpbaegohkkgkknkffkldlkfgehbb/"
  ]
}
```

### Windows Registry Registration Keys:

| Browser Family | Windows Registry Path |
|:---|:---|
| **Google Chrome** | `HKCU\Software\Google\Chrome\NativeMessagingHosts\xdm_bridge` |
| **Microsoft Edge** | `HKCU\Software\Microsoft\Edge\NativeMessagingHosts\xdm_bridge` |
| **Mozilla Firefox** | `HKCU\Software\Mozilla\NativeMessagingHosts\xdm_bridge` |

> In Firefox manifests, `allowed_origins` is replaced with `allowed_extensions` containing the extension ID (e.g. `xdm-enhanced@jakepanlilio2000.github.io`).

---

## 4. Desktop Self-Repair Implementation

The desktop self-repair utility (invoked via Settings > Browser > Repair Integration) programmatically verifies and repairs these paths:
1. Detects installed browser configurations on the host system.
2. Checks that `xdm-app.exe` exists at its registered path.
3. Rewrites the JSON manifests and updates the Windows registry keys using native Win32 API calls (`RegCreateKeyEx`, `RegSetValueEx`).

---

## Related Developer Articles

* [[Architecture Overview|Architecture-Overview]]: Subsystem boundaries.
* [[Browser Extension Setup|Browser-Extension-Setup]]: User installation walkthrough.
* [[Browser Extension Disconnected|Browser-Extension-Disconnected]]: End-user troubleshooting steps.

