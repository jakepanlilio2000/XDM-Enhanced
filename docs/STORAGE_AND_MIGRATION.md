# XDM Enhanced — Storage, Preference Architecture & Migration Specification

**Document Version:** 1.0.0  
**Scope:** Core Configuration, Task Persistence, Storage Layout, Migration Safety, and Credential Protection  
**Status:** Canonical Reference Specification  

---

## 1. File System Layout & Profile Paths

XDM Enhanced stores application state, preferences, download databases, task metadata, custom skins, and plugins within an isolated user-scoped application data directory.

### 1.1 Operating System Standard Locations
The base profile directory is resolved dynamically by `xdm.app.utils.AppDirs`:

| Platform | Profile Directory Location | Environment / Fallback |
| :--- | :--- | :--- |
| **Windows** | `%LOCALAPPDATA%\xdm-enhanced` | `%LOCALAPPDATA%`, fallback `%APPDATA%`, fallback `user.home\xdm-enhanced` |
| **macOS** | `~/Library/Application Support/xdm-enhanced` | `user.home/Library/Application Support/xdm-enhanced` |
| **Linux / BSD** | `$XDG_DATA_HOME/xdm-enhanced` | `$XDG_DATA_HOME` (if absolute), fallback `~/.local/share/xdm-enhanced` |

> [!NOTE]
> On all platforms, `-Dxdm.data.dir=<path>` overrides the profile directory. This is primarily used for integration test isolation and ephemeral profiling sessions.

### 1.2 Profile Directory Contents
Within the `xdm-enhanced` directory:
- `xdm-app.config` — Tagged binary file storing application settings, category definitions, BitTorrent preferences, and network tuning parameters.
- `active-downloads.dat` — Tagged binary file containing downloads currently queued, active, assembling, or moving.
- `paused-downloads.dat` — Tagged binary file containing stopped or paused downloads.
- `finished-downloads.dat` — Tagged binary file containing completed downloads.
- `task-<id>.info` — Metadata and resumption parameters for download `<id>`.
- `task-<id>.state` — Stream segment progress and piece assembly state.
- `tmp/` — Temporary download chunks and segment cache. Located under the user profile to prevent OS `/tmp` cleaners from purging long-paused downloads.
- `logs/` — Rotating per-launch application log files (`xdm-YYYYMMDD-HHmmss-SSS.log`). Restricted to the owner user (`chmod 600` on POSIX).
- `skins/` — Unpacked custom appearance packages (`.xdmskin`).
- `plugins/` — Installed extensibility plugins (`.xdmplugin`).

---

## 2. Binary Tag Schema Specification (`TaggedFile` & `AtomicIO`)

Configuration and download lists are encoded using a crash-safe tagged binary protocol (`xdm.core.util.TaggedFile` and `xdm.core.util.AtomicIO`).

### 2.1 Atomic Transaction Protocol
To survive sudden power loss, OS crashes, or process termination:
1. All file modifications write to `<filename>.bak1`.
2. Upon completion of writing, the ASCII footer `XDM-END!` is appended and the OS file descriptor is flushed (`fs.fd.sync()`).
3. The existing `<filename>` is moved to `<filename>.bak2` (retaining a valid prior state).
4. `<filename>.bak1` is atomically renamed into `<filename>`.
5. On read, `AtomicIO.readTransacted` checks `<filename>`, `<filename>.bak1`, and `<filename>.bak2` in order, validating that the trailing `XDM-END!` footer is present. If the primary file was interrupted midway, the system automatically falls back to the intact backup without data corruption.

### 2.2 Schema Evolution & Unknown Field Preservation
To ensure forward and backward compatibility across application versions:
- Each field is identified by a static integer tag.
- Field tags are strictly monotonic and never reused or retyped.
- When an older version reads a file produced by a newer version, unrecognized tags are retained in memory via `unknownFields: ByteArray` and written back intact during subsequent `save()` operations.

### 2.3 `AppConfig` Tag Reference Table

| Tag | Identifier | Type | Description |
| :---: | :--- | :---: | :--- |
| `1` | `F_RUN_VIRUS_SCAN` | Boolean | Trigger external antivirus on completion |
| `2` | `F_RUN_COMMAND` | Boolean | Execute custom command on completion |
| `3` | `F_SHOW_PROGRESS_WINDOW` | Boolean | Display floating download progress window |
| `4` | `F_DEFAULT_DOWNLOAD_FOLDER` | String | Target directory for completed downloads |
| `5` | `F_TEMP_FOLDER` | String | Directory for chunk assembly and segment buffers |
| `6` | `F_AUTO_RENAME` | Boolean | Auto-increment filename on destination conflict |
| `7` | `F_SHUTDOWN_AFTER_ALL_DONE` | Boolean | Shutdown operating system when queue finishes |
| `8` | `F_MAX_PARALLEL_DOWNLOADS` | Int | Concurrency limit for parallel active downloads |
| `9` | `F_REMEMBER_NONE` | Boolean | Memorize unassigned category behavior |
| `10` | `F_NONE_FOLDER` | String? | Destination path for unassigned category downloads |
| `11` | `F_SORT_KEY` | Int (enum) | Active sort column in main table |
| `12` | `F_SORT_ASCENDING` | Boolean | Sort direction |
| `13` | `F_MIN_VIDEO_SIZE` | Long | Minimum video capture threshold (in KB) |
| `14` | `F_LANG` | String | Active locale code (e.g. `en`, `de`, `fr`, `zh_CN`) |
| `15` | `F_SPEED_LIMITER_ENABLED` | Boolean | Global download throttling toggle |
| `16` | `F_SPEED_LIMIT` | Int | Bandwidth ceiling in KB/s |
| `17` | `F_START_AUTOMATICALLY` | Boolean | Auto-start transfers on capture |
| `18` | `F_OVERWRITE_EXISTING` | Boolean | Overwrite existing files on collision |
| `19` | `F_FILE_EXTENSIONS` | List\<String\> | Monitored file extensions |
| `20` | `F_VIDEO_EXTENSIONS` | List\<String\> | Monitored video extensions |
| `21` | `F_BLOCKED_HOSTS` | List\<String\> | Blacklisted capture domains |
| `22` | `F_GET_SERVER_TIME` | Boolean | Sync file modified time with HTTP `Last-Modified` |
| `23` | `F_MAX_SEGMENTS` | Int | Multi-segmented connection count (1–32) |
| `24` | `F_MAX_RETRIES` | Int | Connection retry threshold on transfer timeout |
| `25` | `F_USE_PROXY` | Boolean | Proxy activation flag |
| `26` | `F_SOCKS_PROXY` | Boolean | SOCKS proxy protocol flag |
| `27` | `F_PROXY_HOST` | String | Proxy server hostname or IP |
| `28` | `F_PROXY_PORT` | Int | Proxy server port |
| `29` | *Reserved* | — | Reserved tag (proxy password is never saved to disk) |
| `30` | `F_PROXY_USER` | String | Proxy authentication username |
| `37` | `F_THEME` | String | UI appearance (`dark`, `light`, `system`) |
| `41` | `F_CATEGORIES` | Message | Serialized user-defined categories |
| `42` | `F_COMPLETE_NOTIFICATION` | Int (enum) | Completion behavior (`DIALOG`, `NOTIFICATION`, `NONE`) |
| `48` | `F_LIST_SELECTION_MODE` | Int (enum) | Row layout mode (`MODERN`, `CLASSIC`, `TABLE`) |
| `73` | `F_ACCENT_COLOR` | String | Custom hex accent color (e.g. `#3B82F6`) |
| `74-78` | `F_CLEAR_*` | Mixed | Clear Downloads dialog filter settings |
| `79` | `F_POST_DOWNLOAD_RULES` | Message | Extension-based post-download trigger rules |
| `81` | `F_SHOW_CATEGORIES_SIDEBAR` | Boolean | Sidebar visibility toggle |
| `87` | `F_SKIN_ID` | String | Visual skin preset (`enhanced`, `classic_xdm`, custom) |
| `88-90` | `F_PLUGIN_*` | Mixed | Plugin enabled, trusted, and safe mode states |

---

## 3. Migration Architecture & Isolation

### 3.1 Side-by-Side Coexistence with Official Upstream XDM
Official upstream XDM stores data in `xdm-app`. To eliminate accidental data clobbering, database corruption, or preference collisions:
- XDM Enhanced isolates all state under `xdm-enhanced`.
- On fresh installation, XDM Enhanced does **not** automatically import or mutate `xdm-app`. Both programs can run concurrently or side-by-side with zero interference.

### 3.2 Explicit Migration with Transactional Staging & Rollback
If a user transitions from official upstream XDM to XDM Enhanced, migration can be triggered explicitly via the CLI argument `--import-legacy` or JVM property `-Dxdm.import.legacy=true`:
1. `AppDirs.migrateLegacyData(target, legacy)` inspects the legacy folder (`xdm-app`).
2. Candidate files (`xdm-app.config`, `*.dat`, `xdm.db`, `.info` files) are copied to an isolated temporary staging directory (`xdm-enhanced.migration-staging-<timestamp>`).
3. Volatile operational folders (`tmp/`, `logs/`) are excluded.
4. If all files copy cleanly, the staged files are committed to `xdm-enhanced`, and the staging folder is deleted.
5. If an I/O error or interruption occurs at any point during staging or commit:
   - The staging directory is purged.
   - Any files newly written to the target directory during the failed attempt are rolled back (`deleteRecursively()`).
   - The original legacy source directory (`xdm-app`) remains 100% untouched.

---

## 4. Credential & Privacy Safeguards

XDM Enhanced enforces strict privacy and security invariants across storage, exports, and logging:

1. **Proxy Passwords in Memory Only:**  
   `proxyPass` is held solely in volatile memory (`AppConfig.proxyPass`). Tag `29` is reserved and never emitted to disk by `TagWriter`.
2. **Authorization Headers Stripped from Disk:**  
   Browser-captured `Authorization` headers (e.g. `Basic ...`, `Bearer ...`) are intercepted by `SensitiveHeaders.strip()`. The sensitive credentials are kept in memory for active retry sessions and stripped before `task-<id>.info` or `task-<id>.state` is saved to disk.
3. **Log Sanitization:**  
   All URLs logged by `LinkPreflightService` and `YtDlpDownloadTask` are sanitized:
   - Basic authentication credentials (`https://user:password@host/path`) are redacted to `***:***@host` or completely stripped.
   - Query parameter tokens (e.g. `?token=...`, `?key=...`, `?auth=...`) are purged.
4. **Export Cleanliness:**  
   `SkinPackager.exportSkinPackage()` packages only validated skin manifest files, color schemes, wallpapers, and icons. Application configs, databases, and history are never included in exported archives.
