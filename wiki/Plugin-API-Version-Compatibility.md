# Plugin API Version Compatibility Guide

Learn how **XDM Enhanced** ensures long-term plugin stability and prevents host updates from breaking third-party extensions through semantic version range matching and legacy bridging.

---

## 1. The Public SPI Contract vs Application Internals

To ensure that plugin code remains completely decoupled from desktop application refactoring:

```
[ Your Third-Party Plugin (.xdmplugin) ]
               │
               ▼ (Only compiles against public SPI)
┌──────────────────────────────────────────────┐
│ xdm-plugin-api.jar (Public API v2)           │
│   ├── XdmPlugin                              │
│   ├── PluginContext                          │
│   ├── Registration                           │
│   ├── DownloadProvider / PluginDownloadTask  │
│   ├── ProxyProvider / VpnStatusProvider      │
│   ├── ContextMenu / Toolbar / SettingsPanel  │
│   └── EventTopics / EventSubscription        │
└──────────────────────────────────────────────┘
               ▲
               │ (Implements & provides at runtime)
[ XDM Enhanced Host Application (xdm-app) ]
```

### Strict Architectural Boundaries:
- **Permitted Imports:** Only packages beginning with `xdm.plugin.api.*`.
- **Prohibited Imports:** Plugins must **never** import `xdm.app.*`, `xdm.core.*`, or internal engine classes. The host application classloader enforces strict boundaries; plugins referencing internal engine classes are rejected upon startup.

---

## 2. API Versioning & Semantic Ranges (`apiVersion` / `minApiVersion`)

In your plugin's `plugin.json` manifest, declare API version requirements:

```json
"minApiVersion": "1.1.0"
```
Or with version range syntax:
```json
"apiVersion": "[2.0.0, 3.0.0)"
```

### Range Syntax Reference Table:

| Manifest Syntax | Range Meaning | Compatible Host Versions |
|:---|:---|:---|
| `[2.0.0, 3.0.0)` | $\ge 2.0.0$ and $< 3.0.0$ | `v2.0.0`, `v2.1.0` (Rejects `v3.0.0`) |
| `[1.0.0, 2.0.0)` | Legacy v1 plugins | Handled via backward compatibility bridge |
| `minApiVersion: "1.1.0"` | Requires host $\ge 1.1.0$ | Current XDM Enhanced host release |

### Legacy v1 Compatibility:
Legacy plugins compiled against `PluginService` and early v1 interfaces continue to load seamlessly via internal `LegacyPluginAdapter`, ensuring no existing extensions are stranded.

---

## 3. Host Verification & Error Reporting

During desktop startup or manual installation:
1. The host plugin manager parses `plugin.json`.
2. It extracts `apiVersion` or `minApiVersion` and validates it against current host API capabilities (`PluginApiVersion.CURRENT`).
3. If the host version falls outside the declared range:
   - The plugin is marked as **Incompatible**.
   - An informative status message is surfaced in **Settings > Plugins**:
     `"Plugin 'SamplePlugin' requires API range [2.0.0, 3.0.0), but host is 1.0.0."`
   - The host application continues running normally without crashing or corrupting task states.

---

## Related Developer Articles & Official Guides

* Detailed Guide: `docs/PLUGIN_DEVELOPMENT.md`
* Complete API Reference: `docs/PLUGIN_API_REFERENCE.md`
* Migration Guide: `docs/PLUGIN_V1_TO_V2_MIGRATION_GUIDE.md`
* [[Plugin SDK Quick Start|Plugin-SDK-Quick-Start]]: Writing your first plugin.
* [[Provider Task Integration|Provider-Task-Integration]]: Submitting tasks to the central scheduler.
* [[Architecture Overview|Architecture-Overview]]: Subsystem boundaries.
