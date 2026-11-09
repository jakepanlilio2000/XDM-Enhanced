# XDM Enhanced v1.0.0-beta.2

Second beta release of XDM Enhanced.

XDM Enhanced is an independent, open-source download manager forked from Xtreme Download Manager (XDM). It pairs high-speed multi-connection segmented HTTP/HTTPS downloading and streaming video capture with native BitTorrent and magnet support, modern FlatLaf dynamic theming, automated post-download rules, download completion power controls, and overhauled browser extensions.

---

## Release Highlights

- **Post-Download Action Rules Engine:** Execute user-defined actions and programs automatically upon file completion based on file extension matching, complete with variable placeholders and dry-run testing.
- **Download Completion Power Actions:** Selectable system power behavior when all downloads finish (Do nothing, Sleep, Shutdown, Hibernate) with a 30-second cancellable countdown warning.
- **Drag-and-Drop Completed Downloads:** Seamlessly drag completed files directly from the download table into Windows Explorer, media players, or external programs.
- **Browser Extension Overhaul:** Full support for website exclusions ("Block this site"), multi-keyword link filtering in the bulk grabber, compact clickable desktop browser settings tiles, and refreshed extension UI.
- **NSIS Windows Installer:** Professional Modern UI 2 installer bundling a trimmed Liberica JDK 25 runtime, AppCDS metadata cache for sub-500ms launch times, Conscrypt OpenSSL TLS acceleration, and full uninstaller.
- **Download Folder & Preferences Persistence:** Fixed upstream bugs where user-selected default directories and dialog preferences were reset.

---

## Added Features

### Automation & Power Management
- **Post-Download Rules (`xdm.app.rules`):**
  - Priority-ordered rule system matching file extensions.
  - Contextual variable tokens: `%file%`, `%file_name%`, `%file_extension%`, `%file_directory%`, `%absolute_file_path%`, `%absolute_directory_path%`.
  - Manual dry-run test dialog to verify command execution before saving rules.
  - *Addresses upstream report: [subhra74/xdm#1409](https://github.com/subhra74/xdm/issues/1409).*
- **Completion Power Actions (`xdm.app.power`):**
  - Selectable completion behavior: *Do nothing*, *Sleep / Suspend*, *Shutdown*, *Hibernate*.
  - Platform capability detection to ensure sleep and hibernation are supported by the operating system before presenting choices.
  - 30-second cancellable warning countdown dialog to prevent accidental system interruptions.
  - *Addresses upstream report: [subhra74/xdm#1430](https://github.com/subhra74/xdm/issues/1430).*

### User Experience & Theming
- **Direct File Drag-and-Drop:** Drag completed downloads from the table directly into Explorer, desktop folders, email clients, or VLC (*Addresses upstream report: [subhra74/xdm#1383](https://github.com/subhra74/xdm/issues/1383)*).
- **Curated Accent Palettes:** 8 curated FlatLaf accent presets (Indigo, Blue, Cyan, Emerald, Amber, Rose, Purple, Orange) plus custom hex picker with WCAG contrast enforcement (*Addresses upstream report: [subhra74/xdm#1395](https://github.com/subhra74/xdm/issues/1395)*).
- **System Theme Follow Mode:** Real-time synchronization with OS Light/Dark mode (*Addresses upstream report: [subhra74/xdm#1349](https://github.com/subhra74/xdm/issues/1349)*).
- **Compact Browser Integration Cards:** Replaced legacy plain text links with compact browser cards displaying official SVG icons, detected browser installation status, and connection states (*Addresses upstream reports: [subhra74/xdm#1391](https://github.com/subhra74/xdm/issues/1391) & [#1372](https://github.com/subhra74/xdm/issues/1372)*).

### Companion Browser Extensions
- **Website Exclusions ("Block This Site"):** One-click blacklisting from extension popup, supporting exact hostnames and wildcard subdomains (`*.example.com`).
- **Multi-Keyword Filter Bar:** Tokenized keyword chips in the bulk link grabber with regex support, case-insensitivity, and AND/OR matching (*Addresses upstream report: [subhra74/xdm#1406](https://github.com/subhra74/xdm/issues/1406)*).
- **Automated Packaging:** Dedicated PowerShell build pipeline (`browser-extension/package-extensions.ps1`) for clean Chromium MV3 and Firefox MV2 release archives.

### Platform & Installer
- **NSIS Modern UI 2 Windows Installer:** Dual installation scope (`perMachineOrUser`), desktop and start menu shortcuts, and clean uninstaller (`XDM-Enhanced-Setup-v1.0.0-beta.2-win-x64.exe`).
- **Linux Desktop Integration:** Standard FreeDesktop `.desktop` entry generation and MIME type associations for `.torrent` and `magnet:` links (*Addresses upstream report: [subhra74/xdm#1412](https://github.com/subhra74/xdm/issues/1412)*).

---

## Improvements & Bug Fixes

- **Download Folder Persistence:** Resolved bug where user-configured default download directories were not preserved across application restarts or browser-intercepted downloads (*Addresses upstream report: [subhra74/xdm#1426](https://github.com/subhra74/xdm/issues/1426)*).
- **Clear Downloads Dialog:** Persisted user selection between "Clear from list only" and "Delete downloaded files from disk" with explicit safety confirmation (*Addresses upstream report: [subhra74/xdm#1417](https://github.com/subhra74/xdm/issues/1417)*).
- **Sub-500ms Startup:** Bundled AppCDS class metadata archives (`xdm.jsa` and `xdm-zgc.jsa`) on Liberica JDK 25.
- **MessageBox Positioning:** Centered dialogs relative to parent windows across multi-monitor setups.
- **Extension Handshake:** Updated minimum version threshold to recognize desktop `1.0.0-beta.2` as current.

---

## Security Hardening

- **Loopback Origin Verification:** Loopback IPC server validates `Origin` headers strictly against `chrome-extension://` and `moz-extension://` extension identifiers.
- **DNS Rebinding Mitigation:** Enforced `Host` header checks restricting loopback requests to `127.0.0.1` and `localhost`.
- **Command Injection Protection:** Execution of external system commands in post-download rules is disabled by default; arguments are tokenized and quoted before passing to the system process builder.
- **Path Traversal Guards:** File names and download destination paths are validated against directory traversal tokens (`..`, null bytes, Windows reserved device names).
- **Extension Content Security Policy:** Extensions enforce strict `script-src 'self'` with zero remote script execution or `eval()`.

---

## Installation Instructions

### Windows (Recommended Installer)
1. Download `XDM-Enhanced-Setup-v1.0.0-beta.2-win-x64.exe`.
2. Run the installer and choose your installation scope (Current User or All Users).
3. Follow the wizard prompts to install shortcuts and file associations.
4. Launch **XDM Enhanced** from the Start Menu or Desktop shortcut.

### Windows Portable (No Installation)
1. Download `XDM-Enhanced-v1.0.0-beta.2-windows-x64.zip`.
2. Extract the archive into any folder.
3. Run `xdm-app.exe`. All data and the bundled runtime remain self-contained.

---

## Browser Extension Packages

Companion extensions intercept downloads and capture streaming web videos.

### Chromium Browsers (Google Chrome, Microsoft Edge, Brave, Opera, Vivaldi)
1. Download `XDM-Enhanced-Extension-Chromium-v1.0.0-beta.2.zip`.
2. Extract the archive into a dedicated folder.
3. In your browser, navigate to `chrome://extensions` (or `edge://extensions`).
4. Enable **Developer mode** (top-right).
5. Click **Load unpacked** and select the extracted folder.

### Mozilla Firefox (Firefox, Floorp, LibreWolf)
1. Download `XDM-Enhanced-Extension-Firefox-v1.0.0-beta.2.zip`.
2. In Firefox, open `about:debugging#/runtime/this-firefox`.
3. Click **Load Temporary Add-on…** and select the `.zip` archive.
*(For persistent installation in Firefox Developer Edition, set `xpinstall.signatures.required` to `false` in `about:config` and drag the `.zip` into Firefox).*

---

## Release Artifacts & SHA-256 Checksums

| Artifact | File Size | SHA-256 Checksum |
| :--- | :---: | :--- |
| `XDM-Enhanced-Setup-v1.0.0-beta.2-win-x64.exe` | 63,315,007 bytes | `DC58205EA86A73CAD16611145AEEF9AAC4A86EC7952707AF9C65D16022340DF6` |
| `XDM-Enhanced-v1.0.0-beta.2-windows-x64.zip` | 74,073,776 bytes | `A4075120907C08AA9962BF725A28572D0954649F3759A725A0C862B0F983B94F` |
| `XDM-Enhanced-Extension-Chromium-v1.0.0-beta.2.zip` | 84,171 bytes | `9376B7BB0B39054C3CE27EB99441029074DDD8845A51D9280D284534393D2031` |
| `XDM-Enhanced-Extension-Firefox-v1.0.0-beta.2.zip` | 80,353 bytes | `A84D9FAFB91C9059C680044C60E5D891F02B738EB9040EBE2B1645C017679AFC` |

---

## Known Issues

- Browser extensions must be loaded via Developer Mode / temporary add-on loading until published on the Chrome Web Store and Mozilla Add-on Developer Hub.
- 32-bit Windows (x86) is deprecated upstream by `libtorrent4j` and is not supported.
- Linux package (.deb / .rpm) generation is in active testing; standalone JAR or bundled tarball requires 64-bit Java 21+.

---

## Upstream Acknowledgments & Credits

- Original Xtreme Download Manager (XDM) codebase and architecture created by Subhra Das Gupta ([@subhra74](https://github.com/subhra74)) and upstream contributors.
- Upstream issue reporters:
  - Subhra Das Gupta and community reporters for issues [#1430](https://github.com/subhra74/xdm/issues/1430), [#1426](https://github.com/subhra74/xdm/issues/1426), [#1417](https://github.com/subhra74/xdm/issues/1417), [#1412](https://github.com/subhra74/xdm/issues/1412), [#1409](https://github.com/subhra74/xdm/issues/1409), [#1406](https://github.com/subhra74/xdm/issues/1406), [#1383](https://github.com/subhra74/xdm/issues/1383), [#1395](https://github.com/subhra74/xdm/issues/1395), [#1349](https://github.com/subhra74/xdm/issues/1349), [#1372](https://github.com/subhra74/xdm/issues/1372), [#1391](https://github.com/subhra74/xdm/issues/1391).
- Contributor Abhishek Mallav ([@abhishekmallav](https://github.com/abhishekmallav)) for browser extension UI refresh concept inspiration (PR reference for #1372).
- `libtorrent4j` wrapper created by Alden Torres and the FrostWire team.
- `libtorrent` (Rasterbar) BitTorrent library developed by Arvid Norberg.
- `FlatLaf` look-and-feel developed by FormDev Software GmbH.
- Nullsoft Scriptable Install System (NSIS) project team.
- BellSoft for the Liberica JDK 25 runtime.

---

## License

XDM Enhanced is free, open-source software licensed under the **GNU General Public License v3.0 (GPL-3.0)**.
See [LICENSE](LICENSE) for the full license text.

