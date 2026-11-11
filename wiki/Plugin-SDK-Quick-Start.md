# Plugin SDK Quick Start

Learn how to build, compile, and package a decoupled third-party plugin for **XDM Enhanced** using the **Plugin API v2**.

---

## 1. Prerequisites

- Liberica JDK 25 or OpenJDK 25 (or JDK 8+ for plugin compilation).
- Apache Maven 3.9+.
- Download [`xdm-plugin-api-1.0.0-beta.6.jar`](https://github.com/jakepanlilio2000/XDM-Enhanced/releases/download/v1.0.0-beta.6/xdm-plugin-api-1.0.0-beta.6.jar) (or install it to your local Maven repository).

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
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>xdm</groupId>
            <artifactId>xdm-plugin-api</artifactId>
            <version>1.0.0-beta.6</version>
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
  "apiVersion": "[1.0.0, 2.0.0)",
  "author": "Community Developer",
  "description": "Demonstrates basic event listening in XDM Enhanced.",
  "entryClass": "com.example.plugin.SamplePlugin",
  "capabilities": ["SETTINGS_PANEL"]
}
```

---

## 4. Implementing `PluginService`

Create the Java entry class:

```java
package com.example.plugin;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.PluginService;
import xdm.plugin.api.events.PluginEventListener;
import xdm.plugin.api.events.PluginProgressEvent;

public class SamplePlugin implements PluginService {

    private PluginContext context;

    @Override
    public void onInitialize(PluginContext context) {
        this.context = context;
        context.getLogger().info("SamplePlugin initialized successfully.");

        // Subscribe to decoupled progress events
        context.getEventBus().register(PluginProgressEvent.class, new PluginEventListener<PluginProgressEvent>() {
            @Override
            public void onEvent(PluginProgressEvent event) {
                context.getLogger().debug("Task " + event.getTaskId() + " progress: " + event.getProgressPercent() + "%");
            }
        });
    }

    @Override
    public void onShutdown() {
        context.getLogger().info("SamplePlugin shutting down.");
    }
}
```

---

## 5. Building and Packaging (`.xdmplugin`)

1. Compile the plugin using Maven:
   ```bash
   mvn clean package
   ```
2. Rename the resulting JAR in `target/`:
   ```bash
   # Rename .jar to .xdmplugin
   mv target/sample-plugin-1.0.0.jar target/sample-plugin.xdmplugin
   ```
3. Install the plugin in XDM Enhanced via **Settings > Plugins > Install Plugin**.

---

## Related Developer Articles

* [[Plugin API Version Compatibility|Plugin-API-Version-Compatibility]]: Managing version ranges.
* [[Provider Task Integration|Provider-Task-Integration]]: Submitting tasks to the central download list.
* Reference sample plugins in repository: [`xdm-app/src/test/resources/`](https://github.com/jakepanlilio2000/XDM-Enhanced).

