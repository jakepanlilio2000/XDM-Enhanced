# Browser Extension Disconnected Troubleshooting Guide

If the XDM Enhanced browser extension icon shows a red badge or the popup displays **"XDM Enhanced Disconnected"**, use this verified guide to diagnose and repair the connection.

---

## 1. Quick 4-Step Recovery Checklist

Follow these 4 steps in order before attempting advanced troubleshooting:

1. **Verify Desktop Application is Running:**
   Ensure the XDM Enhanced icon is visible in your Windows system tray (bottom-right taskbar) or Linux/macOS notification area. If not running, launch XDM Enhanced from the Start Menu.
2. **Click "Recheck Connection":**
   Open the extension popup in your browser toolbar and click the **Recheck Connection** button to retry the loopback handshake.
3. **Run Desktop Self-Repair:**
   Open XDM Enhanced desktop &rarr; **Settings** (⚙️) &rarr; **Browser** &rarr; under **Diagnostics & Repair**, click **Repair Integration**.
4. **Reload the Extension:**
   Open `chrome://extensions` or `about:addons`, find XDM Enhanced, toggle it off and on, or click the reload button.

---

## 2. Understanding the Diagnostic Error Codes

Click **Diagnostics** inside the extension popup or error screen to inspect the active failure code:

### `app_not_running`
- **What it means:** The extension probed `http://127.0.0.1:8597/sync` and received a connection refused (`ECONNREFUSED`) socket error.
- **Resolution:** Launch XDM Enhanced. In **Settings > General**, check **Start XDM Enhanced on System Startup** so the background listener is always available.

### `port_in_use` (Port 8597 Conflict)
- **What it means:** Another application on your computer is occupying local TCP port `8597`, preventing XDM Enhanced from binding its HTTP server.
- **Verification Command (PowerShell):**
  ```powershell
  Get-NetTCPConnection -LocalPort 8597 -ErrorAction SilentlyContinue | Select-Object OwningProcess, State
  ```
- **Resolution:** If another process owns port 8597, terminate the conflicting process or reboot your workstation.

### `host_missing` / `host_not_installed` (Integration Host or Protocol Handler Missing)
- **What it means:** The browser extension attempted to connect to the desktop integration service or invoke the application launcher, but the endpoint returned HTTP 404 or the `xdm-app://` URL protocol handler is missing/points to a stale or moved executable path.
- **Registry Key Verification (Windows):**
  - Protocol Handler: `HKCU\Software\Classes\xdm-app\shell\open\command`
- **Resolution:** Open desktop XDM Enhanced &rarr; **Settings > Browser** &rarr; click **Repair Integration**. This automatically re-registers the `xdm-app://` protocol handler with the verified executable path on disk.

### `permission_denied` / Firewall Block
- **What it means:** Localhost loopback packets on `127.0.0.1` are being dropped by aggressive third-party firewall software.
- **Resolution:** Configure your firewall to permit local loopback connections for `xdm-app.exe`. Note: XDM Enhanced loopback traffic stays strictly on your computer and never crosses your router or internet.

---

## 3. Desktop Self-Repair Utility Walkthrough

XDM Enhanced includes an integrated diagnostic and repair wizard:

1. Launch XDM Enhanced desktop.
2. Click **Settings** (⚙️) on the top toolbar &rarr; select **Browser Integration**.
3. Under **Diagnostics & Repair**, click **Check Integration**.
4. The tool automatically tests:
   - Registry manifest entries for installed Chromium and Firefox browsers.
   - Executable path existence on disk.
   - URI protocol handler registration (`xdm-app://`).
   - Loopback HTTP port `8597` server readiness.
5. If any check displays a red cross, click **Repair Integration** to automatically restore all registry keys and permissions.

---

## 4. Safe Reinstallation Procedure

If the connection remains broken after running self-repair:

1. In your browser (`chrome://extensions` or `about:addons`), click **Remove** to uninstall the extension.
2. Completely exit XDM Enhanced desktop via system tray &rarr; **Exit**.
3. Restart XDM Enhanced desktop as Administrator (right-click `xdm-app.exe` &rarr; **Run as administrator**).
4. Re-install the latest extension package matching your desktop client version ([[Browser Extension Setup|Browser-Extension-Setup]]).
5. Open the extension popup; the status will refresh to green **Connected**.

---

## Related Articles

* [[Browser Extension Setup|Browser-Extension-Setup]]: Reinstalling extensions for Chrome, Edge, and Firefox.
* [[Troubleshooting by Symptom|Troubleshooting-by-Symptom]]: Broader application troubleshooting decision tree.
* Still disconnected? [Open an issue on GitHub](https://github.com/jakepanlilio2000/XDM-Enhanced/issues) with diagnostic log details.

