# Contributing & Responsible Security Disclosure

Guidelines for contributing code, reporting defects, and disclosing security vulnerabilities for **XDM Enhanced**.

---

## 1. Code Contribution Guidelines

We welcome pull requests from community developers! To ensure seamless review and integration:

### Development Workflow:
1. Fork the canonical repository: `https://github.com/jakepanlilio2000/XDM-Enhanced`.
2. Create a focused topic branch:
   ```bash
   git checkout -b feature/my-enhancement master
   ```
3. Follow existing code conventions:
   - Kotlin: Follow standard [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
   - Java: Follow standard Oracle Java style conventions.
   - Preserve existing code comments and docstrings.
4. Add comprehensive unit and regression tests under `xdm-app/src/test/java/xdm/`.
5. Run the full regression suite to ensure zero regressions:
   ```bash
   mvn clean test
   ```

---

## 2. Pull Request (PR) Checklist

Before submitting your PR, confirm the following items:
- [ ] Code compiles without warnings on JDK 25.
- [ ] `mvn test` passes 100% cleanly.
- [ ] No internal application classes (`xdm.app.*`) are leaked into `xdm-plugin-api`.
- [ ] Subprocess executions strictly pass discrete array/list arguments (no command-line shell string concatenation).
- [ ] New features include corresponding documentation updates.

---

## 3. Responsible Security Vulnerability Disclosure

Security and user privacy are foundational principles of XDM Enhanced. We treat all security reports with high urgency.

### Core Security Boundaries:
- **Clipboard Guard:** URL length $\le 2048$ characters; credentials and non-URLs discarded immediately.
- **Preflight Isolation:** `HEAD` / 1-byte probes only; zero file payload pre-downloaded without user confirmation.
- **Loopback Origin Verification:** Strict `Host` and `Origin` validation on `127.0.0.1:8597` rejecting DNS rebinding with `403 Forbidden`.
- **Zip Slip Defense:** Archive extractors reject entries with `../` path traversal components.
- **Subprocess Hardening:** Command execution rejects string concatenation to prevent shell injection.

### How to Report a Vulnerability:
If you discover a security vulnerability or exploit:
1. **Do NOT open a public GitHub issue.** Public disclosure exposes active users before a patch can be verified.
2. Submit a private security report via [GitHub Security Advisories](https://github.com/jakepanlilio2000/XDM-Enhanced/security/advisories/new).
3. Include:
   - Vulnerability type and severity assessment.
   - Minimal reproducible proof-of-concept (PoC).
   - Affected versions and platforms.
4. Maintainers will acknowledge receipt within 48 hours and coordinate a coordinated disclosure timeline.

