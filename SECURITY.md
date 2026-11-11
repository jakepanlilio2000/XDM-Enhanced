# Security Policy

## Supported Versions

Only the current stable release and release candidates receive security patches and updates.

| Version | Supported |
| :--- | :--- |
| `v1.0.0` (stable) | :white_check_mark: Supported |
| `v1.0.0-rc.1` | :white_check_mark: Supported |
| `< 1.0.0-rc.1` | :x: End of Life / Upgrade required |

---

## Reporting a Vulnerability

The XDM Enhanced team takes security, user privacy, and data isolation seriously. If you discover a security vulnerability, we appreciate your cooperation in disclosing it responsibly.

### ⚠️ Please Do NOT Open a Public Issue
Publicly disclosing an unmitigated vulnerability puts all users at risk. Please report security issues privately through our coordinated disclosure channel.

### How to Report Privately:
1. **GitHub Security Advisories:** Submit a private report directly at:  
   **[https://github.com/jakepanlilio2000/XDM-Enhanced/security/advisories/new](https://github.com/jakepanlilio2000/XDM-Enhanced/security/advisories/new)**
2. Provide a detailed summary including:
   - Vulnerability category (e.g., Path Traversal, SSRF, Deserialization, Memory Leakage, IPC Abuse).
   - Exact steps or minimal reproducible test case.
   - Affected components (`xdm-core`, `xdm-app`, `xdm-torrent`, browser extension, or installer).
   - Potential impact and recommended mitigations.

### Response Timeline:
- **Initial Acknowledgment:** Within 48 hours of receipt.
- **Triage & Assessment:** Within 5 business days.
- **Fix & Verification:** Released in the next scheduled point release or an emergency security patch.
- **Public Disclosure:** Coordinated only after the patch is published and available to users.

---

## Security Boundaries & Model

XDM Enhanced enforces strict defensive security controls:

1. **Loopback IPC Protection:**  
   The desktop integration service on `127.0.0.1:8597` verifies incoming `Host` headers (`127.0.0.1:8597` or `localhost:8597` only, preventing DNS rebinding) and strictly verifies `Origin` headers (restricting requests to browser extension origins `chrome-extension://`, `moz-extension://`, `extension://`, `safari-web-extension://`). Non-extension web origins are rejected with `403 Forbidden`. State-changing endpoints require `POST`.

2. **Archive Extraction & Zip Slip Defense:**  
   Plugin packages (`.xdmplugin`), skins (`.xdmskin`), and torrent file structures are validated using canonical `java.nio.file.Path` boundary checks to ensure all extracted files remain strictly within target staging directories. Entries containing path traversal (`../`), root prefixes, or illegal extensions (`.exe`, `.dll`, `.so`, scripts) are rejected.

3. **Credential & Sensitive Data Privacy:**  
   - Authorization headers (`Authorization`, `Proxy-Authorization`) are retained in-memory only and never persisted to plain-text `.info` or `.state` task metadata files on disk.
   - Embedded URL credentials (`http://user:pass@host/`) and auth tokens are automatically masked by the logging engine to prevent leakage into log files or error traces.
   - Download origin marks (`Zone.Identifier` on Windows, `com.apple.quarantine` on macOS) strip credentials before writing to filesystem metadata.

4. **External Code & Command Isolation:**  
   - Post-download actions tokenize commands into structured argument lists (`argv`) and never interpolate commands through shell interpreters (`cmd.exe /c` or `/bin/sh -c`).
   - Running downloaded executable files directly as programs is prohibited.
   - Auto-updating strictly verifies SHA-256 checksums and requires explicit user confirmation before executing any platform installer.

5. **DRM & Technical Access Controls:**  
   XDM Enhanced does not circumvent, crack, or bypass Digital Rights Management (DRM) technologies (e.g., Google Widevine, Apple FairPlay, Microsoft PlayReady). Streams with Encrypted Media Extensions (`mediaKeys`) are detected and presented with an honest informational notice explaining that encrypted media cannot be intercepted.

