# Queues & Category Management

XDM Enhanced includes automated file classification, queue scheduling, and bandwidth throttle management to keep your downloads structured and predictable.

---

## 1. Automatic Category Classification

Downloads are automatically sorted into pre-defined categories based on their file extensions:

| Category | Default File Extensions | Default Destination Path |
|:---|:---|:---|
| **Documents** | `.pdf`, `.doc`, `.docx`, `.xls`, `.xlsx`, `.ppt`, `.txt`, `.epub` | `Downloads\Documents` |
| **Compressed** | `.zip`, `.rar`, `.7z`, `.tar`, `.gz`, `.bz2`, `.iso` | `Downloads\Compressed` |
| **Programs** | `.exe`, `.msi`, `.dmg`, `.pkg`, `.deb`, `.rpm`, `.apk` | `Downloads\Programs` |
| **Music** | `.mp3`, `.flac`, `.wav`, `.aac`, `.m4a`, `.ogg` | `Downloads\Music` |
| **Videos** | `.mp4`, `.mkv`, `.avi`, `.mov`, `.webm`, `.flv` | `Downloads\Videos` |
| **Torrents** | `.torrent`, `magnet:?` payload transfers | `Downloads\Torrents` |

### Customizing & Adding Categories

1. Open **Settings** (⚙️) &rarr; **Categories**.
2. Select an existing category to edit its target folder or associated file extensions.
3. To add a new category, click **New Category**:
   - Provide a Category Name (e.g., *Disk Images*).
   - Enter comma-separated file extensions (e.g., `.iso, .img, .vdi, .qcow2`).
   - Choose a target folder.
4. Click **Save**. Future downloads matching these extensions will automatically route to the new directory.

---

## 2. Queue Scheduler & Timers

You can organize downloads into sequential queues to avoid saturating your connection:

### Creating a Download Queue
1. In the main window, click **Queue Manager** on the left sidebar.
2. Click **Create Queue** &rarr; give your queue a name (e.g. *Night Queue*).
3. Set concurrency limit:
   - **Sequential (1 download at a time):** Downloads finish one by one in priority order.
   - **Parallel (e.g. 3 downloads at a time):** Multiple downloads run concurrently.

### Adding Items to a Queue
- In the New Download confirmation dialog, select your queue under **Queue**.
- Or right-click an existing download in the list &rarr; **Move to Queue** &rarr; select target queue.

### Automated Queue Timers
1. Select your queue in the Queue Manager &rarr; click **Scheduler**.
2. Configure automated schedules:
   - **Start Queue At:** e.g. `02:00 AM` (ideal for off-peak bandwidth hours).
   - **Stop Queue At:** e.g. `07:00 AM`.
   - **Days of Week:** Select active days (Monday through Sunday).
   - **Action on Finish:** Optionally shut down computer or put system into sleep mode.

---

## 3. Bandwidth Throttling & Speed Limits

Prevent download transfers from lagging other household devices or gaming streams:

- **Global Speed Limit:** Click the speedometer icon on the bottom status bar &rarr; toggle **Enable Speed Limiter** &rarr; set maximum aggregate rate (e.g., `2.5 MB/s`).
- **Per-Download Speed Limit:** Right-click an individual transfer &rarr; **Properties** &rarr; set **Speed Limit**.

---

## Related Articles

* [[First Download Guide|First-Download-Guide]]: Basic download initiation.
* [[HTTP & HTTPS Transfers|HTTP-and-HTTPS-Transfers]]: Multi-segment acceleration.
* [[Troubleshooting by Symptom|Troubleshooting-by-Symptom]]: Resolving category routing issues.

