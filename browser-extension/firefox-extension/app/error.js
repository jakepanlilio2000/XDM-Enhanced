window.onload = function () {
    const openLinkBtn = document.getElementById("OpenLink");
    const recheckBtn = document.getElementById("recheckBtn");
    const statusElem = document.getElementById("status");
    const detailElem = document.getElementById("detail");
    const noticeElem = document.getElementById("notice");
    const hintElem = document.getElementById("versionHint");

    function applyStatus(response) {
        if (!response) return;
        if (response.connected) {
            noticeElem.className = "notice ok";
            statusElem.textContent = "Connected to XDM Enhanced";
            detailElem.textContent = "Click the XDM Enhanced icon again to see what has been detected on this page.";
            if (hintElem) hintElem.hidden = true;
            if (openLinkBtn) openLinkBtn.hidden = true;
            if (recheckBtn) recheckBtn.hidden = true;
            return;
        }

        if (response.status === "connecting") {
            noticeElem.className = "notice info";
            statusElem.textContent = "Connecting to XDM Enhanced…";
            detailElem.textContent = response.statusDetails || "Checking desktop integration service...";
        } else if (response.status === "recovering") {
            noticeElem.className = "notice warn";
            statusElem.textContent = "Reconnecting to XDM Enhanced…";
            detailElem.textContent = response.statusDetails || "Connection dropped; retrying with backoff...";
        } else if (response.status === "connection_timeout") {
            noticeElem.className = "notice warn";
            statusElem.textContent = "Connection Timed Out";
            detailElem.textContent = response.statusDetails || "Desktop service timed out responding on 127.0.0.1:8597.";
        } else if (response.status === "permission_denied") {
            noticeElem.className = "notice warn";
            statusElem.textContent = "Permission Denied";
            detailElem.textContent = response.statusDetails || "Loopback connection rejected (HTTP 403) or host permissions withheld.";
        } else if (response.status === "incompatible_version") {
            noticeElem.className = "notice warn";
            statusElem.textContent = "Incompatible Desktop Version";
            detailElem.textContent = response.statusDetails || "XDM Enhanced desktop app is older than required.";
        } else {
            noticeElem.className = "notice warn";
            statusElem.textContent = "XDM Enhanced isn't running";
            detailElem.textContent = response.statusDetails || "Start XDM Enhanced to capture downloads and videos from this page.";
        }
    }

    if (openLinkBtn) {
        openLinkBtn.addEventListener('click', function () {
            try {
                chrome.runtime.sendMessage({ type: "launch" });
            } catch (e) {}
            window.open("xdm-app://launch");
            if (statusElem) statusElem.textContent = "Launching XDM Enhanced…";
            setTimeout(() => {
                chrome.runtime.sendMessage({ type: "recheck" }, function (res) {
                    if (!chrome.runtime.lastError && res) {
                        applyStatus(res);
                    }
                });
            }, 1200);
        });
    }

    if (recheckBtn) {
        recheckBtn.addEventListener('click', function () {
            recheckBtn.disabled = true;
            recheckBtn.textContent = "Checking…";
            chrome.runtime.sendMessage({ type: "recheck" }, function (res) {
                recheckBtn.disabled = false;
                recheckBtn.textContent = "Recheck Connection";
                if (!chrome.runtime.lastError && res) {
                    applyStatus(res);
                }
            });
        });
    }

    chrome.runtime.sendMessage({ type: "recheck" }, function (response) {
        if (chrome.runtime.lastError || !response) return;
        applyStatus(response);
    });
};
