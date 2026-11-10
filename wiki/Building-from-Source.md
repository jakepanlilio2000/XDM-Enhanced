# Building XDM Enhanced from Source

Complete instructions for cloning, compiling, and running automated regression tests for **XDM Enhanced** on your local development workstation.

---

## 1. Toolchain Prerequisites

| Component | Minimum Version | Recommended | Verification Command |
|:---|:---|:---|:---|
| **Java Development Kit** | OpenJDK 17 | Liberica JDK 25 | `java -version` |
| **Apache Maven** | 3.9.0 | 3.9.9+ | `mvn -v` |
| **Git** | 2.30 | Latest | `git --version` |

---

## 2. Cloning the Codebase

Clone the canonical repository from GitHub:

```bash
git clone https://github.com/jakepanlilio2000/XDM-Enhanced.git
cd XDM-Enhanced
```

---

## 3. Compiling with Apache Maven

XDM Enhanced is a multi-module Maven project. The root `pom.xml` orchestrates compilation across `xdm-plugin-api`, `xdm-core`, `xdm-manager`, and `xdm-app`.

```bash
# Full clean compilation and automated unit testing
mvn clean package

# Accelerated build (skips running automated tests)
mvn clean package -DskipTests
```

### Generated Build Artifacts:
- **Executable Desktop Application:** `xdm-app/target/xdm-app-1.0.0-beta.6.jar`
- **Standalone Plugin Developer API:** `xdm-plugin-api/target/xdm-plugin-api-1.0.0-beta.6.jar`

---

## 4. Running the Local Application

Launch the desktop client directly from the compiled target output:

```bash
java -jar xdm-app/target/xdm-app-1.0.0-beta.6.jar
```

To enable verbose debug logging to stdout:
```bash
java -Dxdm.debug=true -jar xdm-app/target/xdm-app-1.0.0-beta.6.jar
```

---

## 5. Running Automated Regression Tests

Run specific regression suites to verify core subsystem integrity:

```bash
# Run full project test suite
mvn test

# Test browser integration loopback handshake
mvn test -Dtest=BrowserIntegrationHandshakeTest

# Test full-UI declarative skin contract and theme fallback
mvn test -Dtest=FullUiSkinContractTest

# Test clipboard privacy filter and HEAD preflight discovery
mvn test -Dtest=ClipboardAndPreflightTest

# Test plugin API v2 range matching and task adapter bridge
mvn test -Dtest=PluginApiV2AndTaskAdapterTest

# Test yt-dlp download list integration
mvn test -Dtest=YtDlpDownloadListIntegrationTest
```

---

## 6. Native Packaging Scripts

Packaging scripts are located in `packaging/`:
- **Windows NSIS Installer (`.exe`):** Run `makensis packaging/windows/xdm-installer.nsi` (requires NSIS 3.x).
- **Windows WiX MSI (`.msi`):** Defined in `packaging/windows/xdm.wxs`.
- **Linux Packages (`.deb`, `.rpm`, `.pkg.tar.zst`):** Packaged via [NFPM](https://nfpm.goreleaser.com/) using `packaging/linux/nfpm.yaml`.

---

## Related Developer Articles

* [[Architecture Overview|Architecture-Overview]]: Modular layout and internal subsystems.
* [[Plugin SDK Quick Start|Plugin-SDK-Quick-Start]]: Building custom plugins with `xdm-plugin-api`.
* [[Contributing & Security|Contributing-and-Security]]: Guidelines for submitting pull requests.

