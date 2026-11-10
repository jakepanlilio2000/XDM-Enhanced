# Building XDM Enhanced from Source

Follow this guide to set up your local development environment, compile the modular codebase, and run the automated regression test suite.

---

## 1. Prerequisites

Before building, verify the following tools are installed on your workstation:

| Tool | Version Requirement | Verification Command |
|:---|:---|:---|
| **Java Development Kit** | Liberica JDK 25 (or OpenJDK 17+) | `java -version` |
| **Apache Maven** | 3.9.0 or higher | `mvn -v` |
| **Git** | 2.30 or higher | `git --version` |

---

## 2. Cloning the Repository

Clone the master branch from GitHub:

```bash
git clone https://github.com/jakepanlilio2000/XDM-Enhanced.git
cd XDM-Enhanced
```

---

## 3. Compiling with Maven

Build all submodules (`xdm-plugin-api`, `xdm-core`, `xdm-manager`, and `xdm-app`):

```bash
# Standard compilation and automated test run
mvn clean package

# Fast build (skipping unit test execution)
mvn clean package -DskipTests
```

### Build Artifact Locations
- **Universal Application JAR:** `xdm-app/target/xdm-app-1.0.0-beta.6.jar`
- **Plugin API JAR:** `xdm-plugin-api/target/xdm-plugin-api-1.0.0-beta.6.jar`

---

## 4. Running the Application Locally

Launch the compiled desktop application directly from the target output:

```bash
java -jar xdm-app/target/xdm-app-1.0.0-beta.6.jar
```

To run with enhanced debug logging:
```bash
java -Dxdm.debug=true -jar xdm-app/target/xdm-app-1.0.0-beta.6.jar
```

---

## 5. Running the Test Suite

Execute specific regression test suites covering the core engine, browser handshake, and skin system:

```bash
# Run all tests across all modules
mvn test

# Run browser integration handshake test
mvn test -Dtest=BrowserIntegrationHandshakeTest

# Run full UI skin contract test
mvn test -Dtest=FullUiSkinContractTest

# Run clipboard & preflight privacy tests
mvn test -Dtest=ClipboardAndPreflightTest

# Run plugin API v2 & task adapter tests
mvn test -Dtest=PluginApiV2AndTaskAdapterTest
```

---

## 6. Packaging Native Installers

Native packaging scripts are located in the `packaging/` directory:

- **Windows NSIS Installer (`.exe`):** Requires NSIS 3.x installed. Run `makensis packaging/windows/xdm-installer.nsi`.
- **Windows MSI (`.msi`):** Built using WiX Toolset via `packaging/windows/xdm.wxs`.
- **Linux Packages (`.deb`, `.rpm`, `.pkg.tar.zst`):** Packaged using [NFPM](https://nfpm.goreleaser.com/) via `packaging/linux/nfpm.yaml`.

