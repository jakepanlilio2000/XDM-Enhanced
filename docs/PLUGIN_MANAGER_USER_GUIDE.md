# XDM Enhanced — Plugin Manager User Guide

The **Plugin Manager** in XDM Enhanced allows you to extend your download manager with community tools, streaming providers, custom proxy configurations, and UI enhancements.

---

## 1. Accessing the Plugin Manager

1. Open **XDM Enhanced**.
2. Click **Settings** (gear icon) in the navigation sidebar or menu.
3. Select the **Plugins** tab.

Keyboard shortcut: Press `Alt+I` while in the Plugins view to open the package installer immediately.

---

## 2. Installing a Plugin

1. In the Plugins tab, click **Install from File...** (`Alt+I`).
2. Select any compatible `.xdmplugin` or `.jar` plugin package.
3. Review the **Security & Trust Disclosure** dialog:
   - **Plugin Identity:** Verifies the plugin's name, version, author, and declared license.
   - **Capabilities:** Lists what features the plugin requests (e.g. Download Provider, Scoped Proxy, Context Menus).
   - **Checksum:** Displays the SHA-256 fingerprint of the archive to ensure integrity.
   - **In-Process Notice:** Explains that Java plugins run with the privileges of your user account.
4. Click **Trust & Enable** to activate the plugin, or **Cancel** to abort.

---

## 3. Managing Installed Plugins

Every installed plugin appears as a card in the Plugins list:

### Plugin Card Overview
- **Name & Version:** Displays the current version and developer credits (`By ...`).
- **Status Badge:**
  - `Active` (Green): The plugin is active and running.
  - `Disabled` (Gray): The plugin is turned off.
  - `Untrusted` (Orange): Newly installed plugin awaiting your explicit approval.
  - `Faulted` (Red): An error occurred during startup or execution (displays specific error reason).
  - `Incompatible` (Red): The plugin requires a newer version of XDM Enhanced or API.
  - `Restart Required` (Blue): Changes will take full effect after restarting XDM Enhanced.
- **Capabilities Pills:** Hover over any pill (e.g. `Download Provider`, `Network Proxy Provider`) to view a summary of external tool requirements and permissions.

### Available Actions (Zero Dead Buttons)
- **Configure:** Opens the plugin's custom settings dialog (only appears if the plugin contributes settings).
- **View Logs:** Opens a detailed diagnostic view displaying registered extension points, classloader status, and sanitized logs.
- **Enable / Disable / Retry:** Toggles the plugin on or off without blocking the user interface.
- **Remove:** Uninstalls external plugins and safely removes their files. Built-in plugins cannot be uninstalled.

---

## 4. Troubleshooting & Diagnostic Tools

### 4.1 Secret-Free Diagnostic Export
If you experience issues with missing tools (like `yt-dlp`), SOCKS proxies, or VPN client detection:
1. Click **Export Diagnostics...** (`Alt+E`) in the Plugins header.
2. Preview the comprehensive report containing system info, Java VM telemetry, external tool detection status, and active proxy/VPN routes.
3. **Zero Secrets Guarantee:** Passwords, API tokens, and authorization headers are strictly redacted (`***`).
4. Click **Copy to Clipboard** or **Save to File...** to share the report with community developers or bug trackers.

### 4.2 Capabilities & Requirements Guide
Click **Capabilities Guide** (`Alt+H`) in the header to view an interactive table explaining which plugin capabilities require external tools, operating system support, or explicit user consent.

### 4.3 Safe Mode Recovery
If a malfunctioning third-party plugin causes crashes or prevents XDM Enhanced from starting:
- Launch XDM Enhanced from terminal/command prompt with:
  ```powershell
  xdman --safe-mode
  ```
  or
  ```powershell
  xdman --no-plugins
  ```
- Safe Mode disables all plugins for diagnostic recovery and allows you to safely remove or update problematic extensions.
