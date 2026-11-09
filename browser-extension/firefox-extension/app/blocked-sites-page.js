"use strict";

class BlockedSitesManager {
    constructor() {
        this.sites = [];
        this.desktopSites = [];
        this.searchQuery = "";
        this.editingRule = null;
    }

    async init() {
        this.bindEvents();
        await this.loadData();
    }

    bindEvents() {
        const addBtn = document.getElementById("addSiteBtn");
        const newSiteInput = document.getElementById("newSiteInput");
        const searchInput = document.getElementById("searchRulesInput");
        const clearAllBtn = document.getElementById("clearAllBtn");

        addBtn.addEventListener("click", () => this.handleAddSite());
        newSiteInput.addEventListener("keydown", (e) => {
            if (e.key === "Enter") {
                this.handleAddSite();
            }
        });

        searchInput.addEventListener("input", (e) => {
            this.searchQuery = (e.target.value || "").trim().toLowerCase();
            this.renderList();
        });

        clearAllBtn.addEventListener("click", async () => {
            if (this.sites.length === 0) return;
            if (confirm("Remove all website exclusions? XDM will resume monitoring on all sites.")) {
                await saveUserBlockedSites([]);
                this.sites = [];
                this.renderList();
            }
        });
    }

    async loadData() {
        try {
            this.sites = await getUserBlockedSites();
        } catch (e) {
            this.sites = [];
        }

        // Ask background script for desktop application blocked hosts
        try {
            chrome.runtime.sendMessage({ type: "get-blocked-sites" }, (response) => {
                if (response) {
                    if (Array.isArray(response.userBlockedSites)) {
                        this.sites = response.userBlockedSites;
                    }
                    if (Array.isArray(response.desktopBlockedHosts) && response.desktopBlockedHosts.length > 0) {
                        this.desktopSites = response.desktopBlockedHosts;
                        this.renderDesktopSites();
                    }
                }
                this.renderList();
            });
        } catch (e) {
            this.renderList();
        }
    }

    renderDesktopSites() {
        const container = document.getElementById("desktopRulesList");
        const card = document.getElementById("desktopCard");
        if (!container || !card) return;

        if (this.desktopSites.length === 0) {
            card.hidden = true;
            return;
        }

        card.hidden = false;
        container.replaceChildren();
        for (const host of this.desktopSites) {
            const badge = document.createElement("span");
            badge.className = "site-badge";
            badge.textContent = host;
            container.appendChild(badge);
        }
    }

    showFeedback(message) {
        const fb = document.getElementById("inputFeedback");
        if (!fb) return;
        if (!message) {
            fb.hidden = true;
            fb.textContent = "";
        } else {
            fb.hidden = false;
            fb.textContent = message;
        }
    }

    async handleAddSite() {
        const input = document.getElementById("newSiteInput");
        const raw = (input.value || "").trim();
        if (!raw) {
            this.showFeedback("Please enter a website domain or URL.");
            input.focus();
            return;
        }

        const normalized = normalizeHost(raw);
        if (!normalized) {
            this.showFeedback("Invalid website name. Please enter a valid domain (e.g. example.com).");
            input.focus();
            return;
        }

        const result = await addUserBlockedSite(normalized);
        if (!result.ok) {
            this.showFeedback(result.error || "Could not add site.");
            return;
        }

        this.showFeedback("");
        input.value = "";
        this.sites = result.sites;
        this.renderList();
    }

    async handleRemoveSite(site) {
        const result = await removeUserBlockedSite(site);
        if (result.ok) {
            this.sites = result.sites;
            this.renderList();
        }
    }

    async handleEditSite(oldSite, newSite) {
        const normalized = normalizeHost(newSite);
        if (!normalized) {
            alert("Invalid domain name.");
            return;
        }
        const result = await editUserBlockedSite(oldSite, normalized);
        if (!result.ok) {
            alert(result.error || "Could not update rule.");
            return;
        }
        this.editingRule = null;
        this.sites = result.sites;
        this.renderList();
    }

    renderList() {
        const list = document.getElementById("rulesList");
        const empty = document.getElementById("emptyRules");
        const count = document.getElementById("ruleCount");

        if (count) {
            count.textContent = String(this.sites.length);
        }

        let displayed = this.sites;
        if (this.searchQuery) {
            displayed = displayed.filter(site => site.includes(this.searchQuery));
        }

        list.replaceChildren();

        if (displayed.length === 0) {
            empty.hidden = false;
            empty.textContent = this.sites.length === 0
                ? "No websites are currently excluded. XDM monitors downloads on all allowed sites."
                : "No exclusions match your search.";
            return;
        }

        empty.hidden = true;

        for (const site of displayed) {
            const li = document.createElement("li");
            li.className = "rule-item";

            if (this.editingRule === site) {
                // Edit mode
                const editWrap = document.createElement("div");
                editWrap.style.display = "flex";
                editWrap.style.gap = "8px";
                editWrap.style.width = "100%";
                editWrap.style.alignItems = "center";

                const editInput = document.createElement("input");
                editInput.type = "text";
                editInput.className = "text-input";
                editInput.style.height = "28px";
                editInput.style.fontSize = "13px";
                editInput.value = site;

                const saveBtn = document.createElement("button");
                saveBtn.className = "primary-button btn-small";
                saveBtn.textContent = "Save";
                saveBtn.onclick = () => this.handleEditSite(site, editInput.value);

                const cancelBtn = document.createElement("button");
                cancelBtn.className = "secondary-button btn-small";
                cancelBtn.textContent = "Cancel";
                cancelBtn.onclick = () => {
                    this.editingRule = null;
                    this.renderList();
                };

                editInput.addEventListener("keydown", (e) => {
                    if (e.key === "Enter") {
                        this.handleEditSite(site, editInput.value);
                    } else if (e.key === "Escape") {
                        this.editingRule = null;
                        this.renderList();
                    }
                });

                editWrap.appendChild(editInput);
                editWrap.appendChild(saveBtn);
                editWrap.appendChild(cancelBtn);
                li.appendChild(editWrap);
                setTimeout(() => editInput.focus(), 0);
            } else {
                // View mode
                const hostSpan = document.createElement("span");
                hostSpan.className = "rule-host";
                hostSpan.textContent = site;

                const actions = document.createElement("div");
                actions.className = "rule-actions";

                const editBtn = document.createElement("button");
                editBtn.className = "secondary-button btn-small";
                editBtn.textContent = "Edit";
                editBtn.onclick = () => {
                    this.editingRule = site;
                    this.renderList();
                };

                const removeBtn = document.createElement("button");
                removeBtn.className = "secondary-button btn-small";
                removeBtn.textContent = "Remove";
                removeBtn.onclick = () => this.handleRemoveSite(site);

                actions.appendChild(editBtn);
                actions.appendChild(removeBtn);

                li.appendChild(hostSpan);
                li.appendChild(actions);
            }

            list.appendChild(li);
        }
    }
}

document.addEventListener("DOMContentLoaded", () => {
    const manager = new BlockedSitesManager();
    manager.init();
});

