"use strict";

const APP_BASE_URL = "http://127.0.0.1:8597";

/**
 * Sent with every request so XDM can tell which extension and version is talking to it, and ask
 * for an update when it is too old. Extensions before this header existed send nothing, which XDM
 * takes as too old.
 */
const XDM_HEADERS = {
    "X-XDM-Extension": "firefox/" + (typeof browser !== "undefined" && browser.runtime && browser.runtime.getManifest
        ? browser.runtime.getManifest().version
        : (typeof chrome !== "undefined" && chrome.runtime && chrome.runtime.getManifest ? chrome.runtime.getManifest().version : "1.1.3"))
};

/** Floor on how fast the long poll may be re-issued after an immediate empty reply. */
const MIN_POLL_INTERVAL_MS = 1000;

/**
 * Floor on how often the extension may reach out to XDM during background polling.
 * Explicit user actions (popup opened, recheck button) pass force=true to bypass this throttle.
 */
const MIN_CONNECT_INTERVAL_MS = 5000;

/** How often the idle backstop runs when nothing else is triggering a lookup. */
const WATCHDOG_INTERVAL_MS = 30000;

/** How long a /sync may take before XDM is taken to be absent. */
const SYNC_TIMEOUT_MS = 3000;

/** Bounded exponential retry backoff schedule in milliseconds (max 30s). */
const RETRY_BACKOFF_SCHEDULE_MS = [1000, 2000, 4000, 8000, 16000, 30000];

/** Minimum supported desktop application version. */
const MIN_XDM_VERSION = "1.0.0";

/**
 * Structured handshake connection states.
 */
const STATUS = {
    CONNECTING: "connecting",
    CONNECTED: "connected",
    INCOMPATIBLE_VERSION: "incompatible_version",
    HOST_MISSING: "host_missing",
    LAUNCH_FAILURE: "launch_failure",
    PERMISSION_DENIED: "permission_denied",
    CONNECTION_TIMEOUT: "connection_timeout",
    DESKTOP_UNAVAILABLE: "desktop_unavailable",
    RECOVERING: "recovering"
};

function isOutdatedDesktopVersion(version) {
    if (!version) return true;
    const parts = v => v.replace(/^v/i, "").split("-")[0].split(".").map(n => parseInt(n, 10) || 0);
    const a = parts(version);
    const b = parts(MIN_XDM_VERSION);
    for (let i = 0; i < Math.max(a.length, b.length); i++) {
        const d = (a[i] || 0) - (b[i] || 0);
        if (d !== 0) return d < 0;
    }
    return false;
}

function createTimeoutSignal(ms) {
    if (typeof AbortSignal !== "undefined" && typeof AbortSignal.timeout === "function") {
        return AbortSignal.timeout(ms);
    }
    const controller = new AbortController();
    setTimeout(() => controller.abort(new Error("TimeoutError")), ms);
    return controller.signal;
}

class Connector {
    constructor(onMessage, onDisconnect) {
        this.logger = new Logger();
        this.onMessage = onMessage;
        this.onDisconnect = onDisconnect;
        this.connected = false;
        this.status = STATUS.CONNECTING;
        this.statusDetails = "Initializing connection to desktop application...";

        // State version last applied; sent with each poll and used to drop snapshots that arrive
        // out of order, since poll and /sync replies travel on separate connections.
        this.version = 0;
        this.instanceId = undefined;
        this.polling = false;

        /** When the last request to XDM went out, for the MIN_CONNECT_INTERVAL_MS floor. */
        this.lastAttemptAt = 0;
        /** The lookup under way, if any; callers arriving meanwhile share its outcome. */
        this.attempt = undefined;
        // The MV2 background page is persistent, so an in-memory id lasts as long as the extension.
        this.clientId = crypto.randomUUID();

        /** Bounded retry counters and timer handles. */
        this.retryCount = 0;
        this.retryTimer = null;
    }

    connect() {
        this.status = STATUS.CONNECTING;
        this.statusDetails = "Connecting to desktop application on 127.0.0.1:8597...";
        setInterval(this.onTimer.bind(this), WATCHDOG_INTERVAL_MS);
        return this.tryConnect("worker started", true);
    }

    onTimer() {
        this.tryConnect("watchdog timer", false);
    }

    disconnect(status = STATUS.DESKTOP_UNAVAILABLE, details = "", scheduleRetry = true) {
        const wasConnected = this.connected === true;
        this.polling = false;
        this.connected = false;
        this.version = 0;
        this.instanceId = undefined;

        this.status = wasConnected ? STATUS.RECOVERING : (status || STATUS.DESKTOP_UNAVAILABLE);
        this.statusDetails = details || (wasConnected
            ? "Connection to desktop interrupted. Recovering..."
            : "Desktop application is not running on 127.0.0.1:8597.");

        this.onDisconnect();

        if (scheduleRetry) {
            this.scheduleNextBackoffRetry();
        }
    }

    isConnected() {
        return this.connected === true;
    }

    getStatusInfo() {
        return {
            status: this.status,
            statusDetails: this.statusDetails,
            connected: this.connected === true,
            version: this.version,
            instanceId: this.instanceId,
            retryCount: this.retryCount
        };
    }

    /**
     * Explicitly forces a recheck of desktop connectivity, bypassing any throttling floor.
     */
    recheck() {
        this.logger.log("Manual recheck requested by user");
        return this.tryConnect("user recheck", true);
    }

    /**
     * Reaches out to desktop application on 127.0.0.1:8597/sync.
     * When force is true (e.g. user recheck, popup opening, app launch), interval throttling is bypassed.
     */
    tryConnect(reason, force = false) {
        if (!force && this.polling && this.connected) {
            return Promise.resolve(this.getStatusInfo());
        }
        if (this.attempt) {
            return this.attempt;
        }

        const since = Date.now() - this.lastAttemptAt;
        if (!force && since < MIN_CONNECT_INTERVAL_MS) {
            this.logger.log("Not looking for XDM (" + reason + "): last attempt " + since + "ms ago");
            return Promise.resolve(this.getStatusInfo());
        }

        this.clearRetryTimer();
        this.lastAttemptAt = Date.now();
        if (this.status !== STATUS.RECOVERING) {
            this.status = STATUS.CONNECTING;
            this.statusDetails = "Checking desktop integration (" + reason + ")...";
        }

        this.logger.log("Looking for XDM (" + reason + ", force=" + force + ")");
        this.attempt = fetch(APP_BASE_URL + "/sync", {
            headers: XDM_HEADERS,
            signal: createTimeoutSignal(SYNC_TIMEOUT_MS)
        })
            .then(res => this.onSyncResponse(res))
            .catch(err => this.onSyncError(err))
            .finally(() => {
                this.attempt = undefined;
            });

        return this.attempt;
    }

    onSyncResponse(res) {
        if (res.status === 200) {
            return res.json().then(json => {
                this.retryCount = 0;
                this.clearRetryTimer();

                const desktopVer = json && json.appVersion;
                if (isOutdatedDesktopVersion(desktopVer)) {
                    this.status = STATUS.INCOMPATIBLE_VERSION;
                    this.statusDetails = "Desktop XDM Enhanced is outdated (" + (desktopVer || "unknown") + "). Version " + MIN_XDM_VERSION + "+ required.";
                } else {
                    this.status = STATUS.CONNECTED;
                    this.statusDetails = "Connected to XDM Enhanced" + (desktopVer ? " v" + desktopVer : "");
                }

                this.connected = true;
                this.startLongPoll();
                this.applyMessage(json);
                return this.getStatusInfo();
            }).catch(err => {
                this.disconnect(STATUS.DESKTOP_UNAVAILABLE, "Malformed JSON reply from desktop application.", true);
                return this.getStatusInfo();
            });
        }

        if (res.status === 403) {
            this.status = STATUS.PERMISSION_DENIED;
            this.statusDetails = "Desktop integration rejected request (HTTP 403 Forbidden). Check Host/Origin permissions.";
            this.disconnect(STATUS.PERMISSION_DENIED, this.statusDetails, false);
            return this.getStatusInfo();
        }

        if (res.status === 404) {
            this.status = STATUS.HOST_MISSING;
            this.statusDetails = "Desktop integration endpoint not found (HTTP 404).";
            this.disconnect(STATUS.HOST_MISSING, this.statusDetails, true);
            return this.getStatusInfo();
        }

        this.status = STATUS.DESKTOP_UNAVAILABLE;
        this.statusDetails = "Desktop integration server returned HTTP " + res.status;
        this.disconnect(STATUS.DESKTOP_UNAVAILABLE, this.statusDetails, true);
        return this.getStatusInfo();
    }

    onSyncError(err) {
        const isTimeout = err && (err.name === "TimeoutError" || String(err).toLowerCase().includes("timeout") || String(err).toLowerCase().includes("aborterror"));
        const isPermission = err && (String(err).toLowerCase().includes("permission") || String(err).toLowerCase().includes("security"));

        if (isTimeout) {
            this.status = STATUS.CONNECTION_TIMEOUT;
            this.statusDetails = "Connection timed out after " + SYNC_TIMEOUT_MS + "ms waiting for 127.0.0.1:8597.";
        } else if (isPermission) {
            this.status = STATUS.PERMISSION_DENIED;
            this.statusDetails = "Network access to local port 8597 denied by browser policy.";
        } else {
            const wasConnected = this.connected === true;
            this.status = wasConnected ? STATUS.RECOVERING : STATUS.DESKTOP_UNAVAILABLE;
            this.statusDetails = wasConnected
                ? "Connection lost. Reconnecting to desktop application..."
                : "Desktop application is not running on 127.0.0.1:8597.";
        }

        this.disconnect(this.status, this.statusDetails, true);
        return this.getStatusInfo();
    }

    clearRetryTimer() {
        if (this.retryTimer) {
            clearTimeout(this.retryTimer);
            this.retryTimer = null;
        }
    }

    scheduleNextBackoffRetry() {
        this.clearRetryTimer();
        const delay = RETRY_BACKOFF_SCHEDULE_MS[Math.min(this.retryCount, RETRY_BACKOFF_SCHEDULE_MS.length - 1)];
        this.retryCount++;
        this.scheduleRetry(delay);
    }

    scheduleRetry(delayMs) {
        this.clearRetryTimer();
        this.logger.log("Scheduling retry #" + this.retryCount + " in " + delayMs + "ms");
        this.retryTimer = setTimeout(() => {
            this.retryTimer = null;
            if (!this.polling && !this.connected) {
                this.tryConnect("auto-retry #" + this.retryCount, true);
            }
        }, delayMs);
    }

    applyMessage(json) {
        if (!json) return;

        if (json.instanceId && json.instanceId !== this.instanceId) {
            this.logger.log("XDM instance " + json.instanceId + " (was " + this.instanceId + ")");
            this.instanceId = json.instanceId;
            this.version = 0;
        }

        if (typeof json.version === "number") {
            if (json.version < this.version) {
                this.logger.log("Ignoring stale state v" + json.version + " (have v" + this.version + ")");
                return;
            }
            this.version = json.version;
        }

        this.onMessage(json);
    }

    startLongPoll() {
        if (this.polling) return;
        this.polling = true;
        this.pollOnce();
    }

    async pollOnce() {
        if (!this.polling) return;
        const startedAt = Date.now();
        try {
            const res = await fetch(APP_BASE_URL + "/poll", {
                method: "POST",
                headers: XDM_HEADERS,
                body: JSON.stringify({ clientId: this.clientId, version: this.version })
            });

            this.connected = true;
            if (res.status === 200) {
                const json = await res.json();
                if (json && json.bye === true) {
                    this.logger.log("XDM reported clean shutdown");
                    this.disconnect(STATUS.DESKTOP_UNAVAILABLE, "XDM Enhanced closed.", true);
                    return;
                }
                this.applyMessage(json);
                this.pollOnce();
                return;
            }

            // 204: nothing to report or poll replaced
            const elapsed = Date.now() - startedAt;
            if (elapsed < MIN_POLL_INTERVAL_MS) {
                setTimeout(() => this.pollOnce(), MIN_POLL_INTERVAL_MS - elapsed);
            } else {
                this.pollOnce();
            }
        } catch (err) {
            this.polling = false;
            this.status = STATUS.RECOVERING;
            this.statusDetails = "Long poll disconnected; verifying desktop connection...";
            this.verifyConnection();
        }
    }

    verifyConnection() {
        this.lastAttemptAt = Date.now();
        fetch(APP_BASE_URL + "/sync", {
            headers: XDM_HEADERS,
            signal: createTimeoutSignal(SYNC_TIMEOUT_MS)
        })
            .then(res => this.onSyncResponse(res))
            .catch(err => this.onSyncError(err));
    }

    postMessage(url, data) {
        this.lastAttemptAt = Date.now();
        return fetch(APP_BASE_URL + url, {
            method: "POST",
            headers: XDM_HEADERS,
            body: JSON.stringify(data)
        })
            .then(res => this.onSyncResponse(res))
            .catch(err => this.onSyncError(err));
    }

    launchApp() {
        this.status = STATUS.CONNECTING;
        this.statusDetails = "Launching XDM Enhanced desktop application...";
        try {
            var tabsApi = (typeof browser !== "undefined" && browser.tabs) ? browser.tabs : (typeof chrome !== "undefined" ? chrome.tabs : null);
            if (tabsApi && tabsApi.create) {
                const res = tabsApi.create({ url: "xdm-app://launch", active: false }, tab => {
                    if (chrome.runtime && chrome.runtime.lastError) {}
                    if (tab && tab.id) {
                        setTimeout(() => {
                            try { tabsApi.remove(tab.id); } catch (e) {}
                        }, 1200);
                    }
                });
                if (res && typeof res.catch === "function") {
                    res.catch(() => {});
                }
            } else if (typeof window !== "undefined" && window.open) {
                window.open("xdm-app://launch");
            }
        } catch (e) {
            this.status = STATUS.LAUNCH_FAILURE;
            this.statusDetails = "Failed to launch application via protocol handler: " + (e && e.message ? e.message : e);
            this.logger.log(this.statusDetails);
            return;
        }

        this.retryCount = 0;
        this.scheduleRetry(1000);
    }
}
