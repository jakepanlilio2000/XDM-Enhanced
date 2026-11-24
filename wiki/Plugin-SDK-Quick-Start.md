# Plugin SDK Quick Start

Learn how to build, compile, and package a decoupled third-party plugin for **XDM Enhanced** using the **Plugin API v2**.

---

## 1. Prerequisites

- Liberica JDK 25 or OpenJDK 25 (or JDK 8+ for plugin compilation).
- Apache Maven 3.9+.
- Public plugin API dependency: `xdm-plugin-api-1.1.0.jar` (available in local Maven repository or repository builds).

---

## 2. Setting Up `pom.xml`

Create a new Maven project with `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>sample-plugin</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>xdm</groupId>
            <artifactId>xdm-plugin-api</artifactId>
            <version>1.1.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

---

## 3. Creating the Plugin Manifest (`plugin.json`)

Create `src/main/resources/plugin.json`:

```json
{
  "id": "com.example.sample-plugin",
  "name": "Sample Verifier Plugin",
  "version": "1.0.0",
  "minApiVersion": "1.1.0",
  "author": "Community Developer",
  "description": "Demonstrates SDK v2 extension points and typed event bus.",
  "license": "Apache-2.0",
  "entryPoint": "com.example.plugin.SamplePlugin",
  "capabilities": [
    "SETTINGS_PANEL",
    "EVENT_BUS"
  ]
}
```

---

## 4. Implementing `XdmPlugin` (SDK v2)

Create the Java entry class implementing `xdm.plugin.api.XdmPlugin`:

```java
package com.example.plugin;

import xdm.plugin.api.*;
import xdm.plugin.api.events.EventSubscription;
import xdm.plugin.api.events.EventTopics;
import java.util.ArrayList;
import java.util.List;

public class SamplePlugin implements XdmPlugin {

    private final List<Registration> registrations = new ArrayList<>();
    private EventSubscription eventSub;
    private PluginContext context;

    @Override
    public String getId() {
        return "com.example.sample-plugin";
    }

    @Override
    public void init(PluginContext context) throws Exception {
        this.context = context;
        context.logInfo("SamplePlugin initialized successfully.");

        // Subscribe to decoupled progress/completion events via typed EventTopics
        eventSub = context.subscribeTopic(EventTopics.DOWNLOAD_COMPLETED, (topic, event) -> {
            context.logInfo("Received completion event on topic: " + topic);
            context.showNotification("Download Finished", "Task completed notification from sample plugin", NotificationLevel.INFO);
        });

        // Register custom settings panel or other UI components
        // registrations.add(context.registerSettings(new SampleSettingsPanel()));
    }

    @Override
    public void shutdown() {
        if (eventSub != null) {
            eventSub.close();
        }
        for (Registration reg : registrations) {
            reg.unregister();
        }
        registrations.clear();

        if (context != null) {
            context.logInfo("SamplePlugin shutting down.");
        }
    }
}
```

---

## 5. Building and Packaging (`.xdmplugin`)

1. Compile and package the plugin using Maven:
   ```bash
   mvn clean package
   ```
2. Rename the resulting JAR in `target/` to `.xdmplugin`:
   ```bash
   # Rename .jar to .xdmplugin
   mv target/sample-plugin-1.0.0.jar target/sample-plugin.xdmplugin
   ```
3. Install the plugin in XDM Enhanced via **Settings > Plugins > Install Plugin**.

---

## Related Developer Articles & Official Guides

* Detailed Guide: `docs/PLUGIN_DEVELOPMENT.md`
* Complete API Reference: `docs/PLUGIN_API_REFERENCE.md`
* V1 to V2 Migration Guide: `docs/PLUGIN_V1_TO_V2_MIGRATION_GUIDE.md`
* User Guide: `docs/PLUGIN_MANAGER_USER_GUIDE.md`
* [[Plugin API Version Compatibility|Plugin-API-Version-Compatibility]]: Managing version ranges.
* [[Provider Task Integration|Provider-Task-Integration]]: Submitting tasks to the central download list.
* Sample plugins in repository: `sample-plugins/` (`ytdlp-downloader-plugin`, `proxy-provider-sample`, `vpn-status-sample`, `ui-notification-sample`).
