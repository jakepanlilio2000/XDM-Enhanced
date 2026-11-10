package xdm.plugin.api.models;

public class DownloadInfo {
    private final long id;
    private final String url;
    private final String fileName;
    private final String destinationPath;
    private final long totalSize;
    private final long downloadedSize;
    private final int progressPercent;
    private final float transferSpeed;
    private final String status;
    private final String category;
    private final long date;

    public DownloadInfo(long id, String url, String fileName, String destinationPath,
                        long totalSize, long downloadedSize, int progressPercent,
                        float transferSpeed, String status, String category, long date) {
        this.id = id;
        this.url = url;
        this.fileName = fileName;
        this.destinationPath = destinationPath;
        this.totalSize = totalSize;
        this.downloadedSize = downloadedSize;
        this.progressPercent = progressPercent;
        this.transferSpeed = transferSpeed;
        this.status = status;
        this.category = category;
        this.date = date;
    }

    public long getId() { return id; }
    public String getUrl() { return url; }
    public String getFileName() { return fileName; }
    public String getDestinationPath() { return destinationPath; }
    public long getTotalSize() { return totalSize; }
    public long getDownloadedSize() { return downloadedSize; }
    public int getProgressPercent() { return progressPercent; }
    public float getTransferSpeed() { return transferSpeed; }
    public String getStatus() { return status; }
    public String getCategory() { return category; }
    public long getDate() { return date; }

    @Override
    public String toString() {
        return "DownloadInfo{id=" + id + ", fileName='" + fileName + "', status='" + status + "'}";
    }
}

