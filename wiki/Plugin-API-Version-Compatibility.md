# Plugin API Version Compatibility Guide

Learn how **XDM Enhanced** ensures long-term plugin stability and prevents host updates from breaking third-party extensions through semantic version range matching.

---

## 1. The Public SPI Contract vs Application Internals

To ensure that plugin code remains completely decoupled from desktop application refactoring:

```
[ Your Third-Party Plugin (.xdmplugin) ]
               │
               ▼ (Only compiles against public SPI)
┌──────────────────────────────────────────────┐
│ xdm-plugin-api.jar (Public API v2)           │
│   ├── PluginService                          │
│   ├── PluginContext                          │
│   ├── PluginEventBus                         │
│   └── models/PluginDownloadTask              │
└──────────────────────────────────────────────┘
               ▲
               │ (Implements & provides at runtime)
[ XDM Enhanced Host Application (xdm-app) ]
```

### Strict Architectural Boundaries:
- **Permitted Imports:** Only packages beginning with `xdm.plugin.api.*`.
- **Prohibited Imports:** Plugins must **never** import `xdm.app.*`, `xdm.core.*`, or `xdm.manager.*`. The host application classloader enforces strict boundaries; plugins referencing internal engine classes are rejected upon startup.

---

## 2. Semantic Version Range Syntax (`apiVersion`)

In your plugin's `plugin.json` manifest, declare the supported host API range using standard Maven version range syntax:

```json
"apiVersion": "[1.0.0, 2.0.0)"
```

### Range Syntax Reference Table:

| Manifest Syntax | Range Meaning | Compatible Host Versions |
|:---|:---|:---|
| `[1.0.0, 2.0.0)` | $\ge 1.0.0$ and $< 2.0.0$ | `v1.0.0-beta.6`, `v1.1.0`, `v1.9.9` (Rejects `v2.0.0`) |
| `[1.0.0, 1.5.0]` | $\ge 1.0.0$ and $\le 1.5.0$ | Up to `1.5.0` inclusive |
| `1.0.0` | Exactly `1.0.0` | Only `1.0.0` (Not recommended) |

### Why `[1.0.0, 2.0.0)` Is the Recommended Standard:
Semantic Versioning guarantees backwards compatibility across minor and patch releases (`1.x.x`). Using `[1.0.0, 2.0.0)` ensures that when XDM Enhanced updates from `v1.0.0-beta.6` to future `1.x` releases, your plugin continues to load and execute without requiring re-compilation.

---

## 3. Host Verification & Error Reporting

During desktop startup:
1. The host plugin manager parses `plugin.json`.
2. It extracts `apiVersion` and validates it against the active `PluginApiVersion.CURRENT` constant.
3. If the host version falls outside the declared range:
   - The plugin is marked as **Incompatible**.
   - An informative message is surfaced in **Settings > Plugins**:
     `"Plugin 'SamplePlugin' requires API range [1.0.0, 2.0.0), but host is 2.1.0."`
   - The host application continues running normally without crashing or corrupting task states.

---

## Related Developer Articles

* [[Plugin SDK Quick Start|Plugin-SDK-Quick-Start]]: Writing your first plugin.
* [[Provider Task Integration|Provider-Task-Integration]]: Submitting tasks to the central scheduler.
* [[Architecture Overview|Architecture-Overview]]: Subsystem boundaries.

