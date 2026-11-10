# Architecture Overview

This document describes the high-level architecture, module boundaries, and concurrency model of **XDM Enhanced**.

---

## 1. Modular Subsystem Layout

The codebase is organized into four decoupled submodules:

```
[ xdm-plugin-api ] ◄── (Public Java Interface / SPI v2)
        ▲
        │ Implements
[ xdm-app ] ────────► [ xdm-manager ] ────────► [ xdm-core ]
  (Swing GUI, FlatLaf,   (Queue Schedulers,      (Segmented Engine,
   Skins, Task Bridge,    Category Rules,         Sockets, Chunk Maps,
   Native Messaging)      Speed Throttles)        libtorrent4j)
```

### Module Responsibilities

| Submodule | Target Runtime | Core Responsibilities |
|:---|:---:|:---|
| **`xdm-plugin-api`** | Java 17+ | Public Service Provider Interface (SPI). Contains no internal application dependencies. Declares `PluginService`, `PluginContext`, `PluginEventBus`, and capability tags. Published independently as `xdm-plugin-api.jar`. |
| **`xdm-core`** | Kotlin / Java | High-throughput byte streaming engine, HTTP/HTTPS client pooling, direct disk block allocation, SHA-256 integrity verifiers, and `libtorrent4j` native wrappers. |
| **`xdm-manager`** | Kotlin | Download task lifecycle management, priority queues, automated category routing, bandwidth throttle controllers, and resume schedulers. |
| **`xdm-app`** | Kotlin / Swing | FlatLaf modern desktop user interface, `.xdmskin` package extraction, `PluginDownloadBridge` task adapter, local loopback HTTP server (`127.0.0.1:8597`), and system tray integration. |

---

## 2. Concurrency & Threading Model

To ensure a smooth, lag-free user experience, XDM Enhanced enforces strict separation between UI rendering and network I/O:

```
[ Swing Event Dispatch Thread (EDT) ]
      │ (Triggers UI updates, repaints, modal rendering)
      ▼
[ Background Worker Thread Pools ]
      ├── Preflight Daemon Pool   (Lightweight HTTP HEAD metadata probes)
      ├── Segment Worker Pool     (Multi-socket chunk download workers)
      ├── BitTorrent Engine Pool  (libtorrent4j native peer networking)
      └── Subprocess Daemon Pool  (Asynchronous yt-dlp stdout/stderr consumers)
```

- **EDT Protection:** No network operations, disk I/O, or external process execution is ever permitted on the Swing Event Dispatch Thread.
- **Direct Block Assembly:** Each chunk worker writes directly to calculated byte offsets in pre-allocated disk storage, eliminating thread lock contention.

---

## 3. Local Loopback Communication Architecture

The browser extension communicates with the desktop client over a local HTTP pipeline:

- **Host & Port:** `http://127.0.0.1:8597`
- **DNS Rebinding Defense:** The desktop server validates the `Host` and `Origin` headers on every incoming request. Non-localhost or malformed origin headers receive an immediate `403 Forbidden`.
- **Endpoints:**
  - `GET /sync`: Health check, active version verification, and settings exchange.
  - `POST /download`: Dispatches captured URL, cookies, and headers to the New Download dialog.
  - `POST /media`: Streams detected audio/video playlists to the floating sniffer menu.

---

## Related Developer Articles

* [[Building from Source|Building-from-Source]]: Compiling all submodules locally.
* [[Plugin SDK Quick Start|Plugin-SDK-Quick-Start]]: Building decoupled extensions.
* [[Skin Developer Quick Start|Skin-Developer-Quick-Start]]: Customizing application theming.

