# First Download Guide

This step-by-step tutorial demonstrates how to add, accelerate, and manage your first download using **XDM Enhanced**.

---

## 1. Prerequisites

- XDM Enhanced is installed and running on your system ([[Installation Guide|Installation-Guide]]).
- An active internet connection.

---

## 2. Step-by-Step Walkthrough

### Step 1: Open the New Download Dialog
Click the blue **Add Download (+)** button on the top toolbar, or press `Ctrl + N` (`Cmd + N` on macOS).

### Step 2: Enter the Download URL
Paste any downloadable direct HTTP or HTTPS link into the **Address** field. For testing, you can use an open-source Linux ISO or test file:
```
https://cdimage.debian.org/debian-cd/current/amd64/iso-cd/debian-12.8.0-amd64-netinst.iso
```

### Step 3: Observe Automatic Preflight Discovery
As soon as the URL is entered, XDM Enhanced issues a lightweight HTTP `HEAD` preflight query:
- The **File Name** field automatically populates from the server's `Content-Disposition` header or URL path.
- The **File Size** field shows the exact remote byte size (e.g. `628.00 MB`).
- The **Resume Supported** indicator shows whether the remote server supports segmented byte-range requests (`Accept-Ranges: bytes`).

> **Zero-Payload Guarantee:** The preflight query only fetches HTTP headers. No file payload data is downloaded until you explicitly confirm.

### Step 4: Choose Destination & Category
- **Category:** XDM Enhanced automatically selects a category (e.g. *Programs* or *Compressed*) based on the file extension.
- **Save In:** Defaults to your configured category folder (e.g. `Downloads\Programs`). Click **Browse** if you wish to change the target directory.

### Step 5: Start or Queue the Download
- Click **Download Now** to immediately initiate segmented transfer streams.
- Or click **Download Later** to place the task into the inactive queue for scheduled downloading.

---

## 3. Monitoring & Controlling the Transfer

While the download is active:
- **Speed & ETA:** The main window displays live transfer rates (e.g. `14.5 MB/s`) and remaining time estimates.
- **Progress Dialog:** Double-click the download item to view the visual multi-segment progress dialog displaying the active byte streams assembling on disk.
- **Pause & Resume:** Click the **Pause** button on the toolbar at any time. Click **Resume** to continue downloading without re-fetching completed chunks.

---

## 4. Common Failure States & Solutions

| Problem | Cause | Solution |
|:---|:---|:---|
| **Size shows "Unknown"** | Remote server omits `Content-Length` | The file can still be downloaded; segmented parallel connections will be disabled for that task. |
| **Download fails with HTTP 403 Forbidden** | Temporary authentication token expired | Right-click the transfer &rarr; select **Refresh Link** &rarr; capture a new URL to resume from the exact byte where it paused. |
| **Download immediately stops at 0%** | Disk full or missing write permissions | Verify sufficient free space in your configured temporary download directory. |

---

## Related Articles & Next Steps

* [[Browser Extension Setup|Browser-Extension-Setup]]: Intercept downloads directly from Chrome, Edge, and Firefox.
* [[Queues & Categories|Queues-and-Categories]]: Organize transfers and configure automated queue timers.
* [[Troubleshooting by Symptom|Troubleshooting-by-Symptom]]: Diagnostics for stuck or interrupted downloads.

