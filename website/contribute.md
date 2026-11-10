# Contributing to XDM Enhanced

Thank you for your interest in contributing to **XDM Enhanced**! As an open-source, community-driven project, we welcome code contributions, bug reports, documentation enhancements, and new translations.

---

## 1. Reporting Issues & Filing Bugs

Before opening a new issue on GitHub:
1. Search the [GitHub Issue Tracker](https://github.com/jakepanlilio2000/XDM-Enhanced/issues) to see if the issue has already been reported.
2. Check the [Troubleshooting & Diagnostics Guide](/troubleshooting) to see if self-repair resolves the problem.

### Bug Report Checklist
When filing a bug, please include:
- **Application Version:** (e.g., `v1.0.0-beta.6`)
- **Operating System:** (e.g., Windows 11 x64, Ubuntu 24.04, macOS 14.5)
- **Browser & Extension Version:** (e.g., Google Chrome 124, Chromium MV3 v1.0.0-beta.6)
- **Steps to Reproduce:** Exact URLs or actions that trigger the failure.
- **Log Output:** Relevant error logs from `~/.xdm/logs/` or extension console.

---

## 2. Development & Pull Request Workflow

1. **Fork the Repository:** Create your own fork on GitHub.
2. **Branch from `master`:**
   ```bash
   git checkout -b feature/my-enhancement master
   ```
3. **Write Clean, Tested Code:**
   - Adhere to existing Kotlin and Java code formatting conventions.
   - Preserve existing code comments and docstrings.
   - Add unit tests for any new features or bug fixes under `xdm-app/src/test/java/xdm/`.
4. **Run Regression Tests:**
   Ensure the full test suite passes before submitting:
   ```bash
   mvn clean test
   ```
5. **Submit a Pull Request:**
   - Provide a clear, concise description of what the PR accomplishes and reference any related issue numbers (`Fixes #123`).

---

## 3. Contributing Translations

XDM Enhanced supports multi-language interfaces. Translations are stored as UTF-8 property files in:
```
xdm-app/src/main/resources/lang/
├── messages_en.txt      # English (Base template)
├── messages_de.txt      # German
├── messages_es.txt      # Spanish
├── messages_fr.txt      # French
├── messages_zh.txt      # Chinese (Simplified)
└── ...
```

To add or update a language:
1. Copy `messages_en.txt` to `messages_<language_code>.txt`.
2. Translate the values on the right side of the `=` delimiter.
3. Test the language by changing your system locale or launching with `-Duser.language=<code_code>`.
4. Submit your translation file via a pull request.

