# Post-Download Rules & Automation

XDM Enhanced includes automated post-processing pipelines that organize downloaded files, trigger antivirus scans, and execute system power management tasks upon transfer completion.

---

## 1. Automatic Category Routing

Downloads are automatically sorted into dedicated directories based on their file extensions:

| Category | Default File Extensions | Target Directory |
|:---|:---|:---|
| **Documents** | `.pdf`, `.doc`, `.docx`, `.xls`, `.xlsx`, `.ppt`, `.txt`, `.epub` | `Downloads\Documents` |
| **Compressed** | `.zip`, `.rar`, `.7z`, `.tar`, `.gz`, `.bz2`, `.iso` | `Downloads\Compressed` |
| **Programs** | `.exe`, `.msi`, `.dmg`, `.pkg`, `.deb`, `.rpm`, `.apk` | `Downloads\Programs` |
| **Music** | `.mp3`, `.flac`, `.wav`, `.aac`, `.m4a`, `.ogg` | `Downloads\Music` |
| **Videos** | `.mp4`, `.mkv`, `.avi`, `.mov`, `.webm`, `.flv` | `Downloads\Videos` |

### Customizing Category Rules

1. Open **Settings** &rarr; **File Categories**.
2. Select an existing category or click **New Category**.
3. Edit the extension list and set a custom destination path.
4. You can also specify URL match patterns to force specific domains into designated folders.

---

## 2. Antivirus Scanner Integration

Automatically scan downloaded files with your installed antivirus software before opening them:

1. Open **Settings** &rarr; **Post-Processing**.
2. Enable **Execute Antivirus on Download Complete**.
3. Enter the executable path and argument template:

```ini
; Example for Windows Defender CLI
Program: "C:\Program Files\Windows Defender\MpCmdRun.exe"
Arguments: -Scan -ScanType 3 -File "%file%"

; Example for ClamAV
Program: "/usr/bin/clamscan"
Arguments: --bell -i "%file%"
```

The `%file%` placeholder is automatically replaced with the absolute path of the completed file.

---

## 3. Power Management Actions

When downloading large queues or multi-gigabyte ISOs overnight, you can automate power actions when all transfers finish:

- **Shutdown Computer:** Powers down the system safely.
- **Sleep / Standby:** Puts the computer into low-power sleep state.
- **Hibernate:** Saves system state to disk and powers down.
- **Close XDM Enhanced:** Exits the application and releases system memory.

### Safety Countdown Dialog

To prevent unexpected data loss while you are working:
- Whenever a completion action (such as Shutdown or Sleep) is triggered, XDM Enhanced displays a **30-second warning countdown dialog** with an audible chime.
- You can click **Cancel** at any time during the countdown to abort the power action.

