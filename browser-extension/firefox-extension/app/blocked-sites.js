"use strict";

/**
 * Sites the extension never takes anything from, whatever XDM's own settings say.
 * Store policy does not allow downloading from YouTube.
 */
var BLOCKED_DOMAINS = [
    "youtube.com",
    "youtu.be",
    "youtube-nocookie.com",
    "youtubekids.com",
    "ytimg.com"          // thumbnails and player assets
];

var USER_BLOCKED_SITES_KEY = "userBlockedSites";

/**
 * Safely normalizes an input string into a lowercase host name or domain rule.
 * Strips protocols (http://, https://), ports (:8080), paths (/...), query strings (?q=1),
 * hashes (#anchor), and leading wildcards/dots (*.example.com -> example.com).
 * Prevents substring confusion and malformed inputs.
 */
function normalizeHost(input) {
    if (!input || typeof input !== "string") {
        return "";
    }
    var str = input.trim().toLowerCase();
    if (!str) {
        return "";
    }

    // If a protocol scheme is present, use URL parser when possible
    if (/^[a-z][a-z0-9+.-]*:\/\//i.test(str)) {
        try {
            str = new URL(str).hostname;
        } catch (e) {
            str = str.replace(/^[a-z]+:\/\//i, "").split("/")[0];
        }
    } else {
        // Strip path, query, hash
        str = str.split("/")[0].split("?")[0].split("#")[0];
    }

    // Strip port if present (e.g. localhost:8080 or example.com:443)
    if (str.includes(":") && !str.startsWith("[")) {
        str = str.split(":")[0];
    }

    // Strip leading wildcard or dot prefix (e.g. *.example.com or .example.com -> example.com)
    str = str.replace(/^(\*\.)+/, "").replace(/^\.+/, "").replace(/\.+$/, "");

    // Validate that the host contains only valid domain characters (RFC 1123 / IDN)
    if (!/^[a-z0-9]([a-z0-9-_.]*[a-z0-9])?$/i.test(str)) {
        return "";
    }

    return str;
}

/** True for one of the domains above or any subdomain of it ("m.youtube.com", "i.ytimg.com"). */
function isBlockedHost(hostname) {
    if (!hostname) {
        return false;
    }
    var host = normalizeHost(hostname);
    if (!host) {
        return false;
    }
    return BLOCKED_DOMAINS.some(function (d) { return host === d || host.endsWith("." + d); });
}

/** isBlockedHost for a URL or an origin; false for anything that does not parse. */
function isBlockedUrl(url) {
    if (!url) {
        return false;
    }
    try {
        return isBlockedHost(new URL(url).hostname);
    } catch (e) {
        return false;
    }
}

/**
 * Safely tests whether a hostname matches a host rule.
 * An exact match or a proper subdomain matches (e.g. 'sub.example.com' matches 'example.com').
 * Substring prefixes or suffixes without a dot boundary do NOT match
 * (e.g. 'notexample.com' does NOT match 'example.com', 'bad.company' does NOT match 'bad.com').
 */
function matchesHostRule(hostname, rule) {
    if (!hostname || !rule) {
        return false;
    }
    var host = normalizeHost(hostname);
    var target = normalizeHost(rule);
    if (!host || !target) {
        return false;
    }
    return host === target || host.endsWith("." + target);
}

/**
 * Checks whether a given hostname or URL matches any exclusion rule in the given lists,
 * or is store-blocked (YouTube).
 */
function isHostExcluded(hostnameOrUrl, userBlockedSites, desktopBlockedHosts) {
    if (!hostnameOrUrl) {
        return false;
    }
    var host = "";
    if (typeof hostnameOrUrl === "string" && (hostnameOrUrl.includes("://") || hostnameOrUrl.startsWith("//"))) {
        try {
            host = new URL(hostnameOrUrl).hostname;
        } catch (e) {
            host = normalizeHost(hostnameOrUrl);
        }
    } else {
        host = normalizeHost(hostnameOrUrl);
    }
    if (!host) {
        return false;
    }

    // Check store-blocked domains
    if (isBlockedHost(host)) {
        return true;
    }

    // Check user-configured website exclusions
    if (Array.isArray(userBlockedSites) && userBlockedSites.some(function (rule) { return matchesHostRule(host, rule); })) {
        return true;
    }

    // Check desktop XDM blocked hosts
    if (Array.isArray(desktopBlockedHosts) && desktopBlockedHosts.some(function (rule) { return matchesHostRule(host, rule); })) {
        return true;
    }

    return false;
}

/**
 * Loads user blocked sites from storage.local.
 * Returns a Promise of string[].
 */
function getUserBlockedSites() {
    return new Promise(function (resolve) {
        try {
            var api = typeof browser !== "undefined" ? browser : chrome;
            api.storage.local.get(USER_BLOCKED_SITES_KEY, function (stored) {
                var list = stored && stored[USER_BLOCKED_SITES_KEY];
                if (Array.isArray(list)) {
                    resolve(list.map(normalizeHost).filter(Boolean));
                } else {
                    resolve([]);
                }
            });
        } catch (e) {
            resolve([]);
        }
    });
}

/**
 * Persists user blocked sites to storage.local.
 */
function saveUserBlockedSites(sites) {
    return new Promise(function (resolve) {
        var normalized = Array.from(new Set((sites || []).map(normalizeHost).filter(Boolean)));
        var api = typeof browser !== "undefined" ? browser : chrome;
        var data = {};
        data[USER_BLOCKED_SITES_KEY] = normalized;
        api.storage.local.set(data, function () {
            resolve(normalized);
        });
    });
}

/**
 * Adds a site to the user blocked list and persists.
 */
function addUserBlockedSite(input) {
    var host = normalizeHost(input);
    if (!host) {
        return Promise.resolve({ ok: false, error: "Invalid hostname or URL" });
    }
    return getUserBlockedSites().then(function (current) {
        if (current.includes(host)) {
            return { ok: false, error: "Site is already in the exclusion list", sites: current };
        }
        current.push(host);
        return saveUserBlockedSites(current).then(function (updated) {
            return { ok: true, sites: updated, added: host };
        });
    });
}

/**
 * Removes a site from the user blocked list and persists.
 */
function removeUserBlockedSite(input) {
    var host = normalizeHost(input);
    if (!host) {
        return Promise.resolve({ ok: false, error: "Invalid hostname" });
    }
    return getUserBlockedSites().then(function (current) {
        var updated = current.filter(function (item) { return item !== host; });
        return saveUserBlockedSites(updated).then(function (saved) {
            return { ok: true, sites: saved, removed: host };
        });
    });
}

/**
 * Edits an existing site rule in the user blocked list and persists.
 */
function editUserBlockedSite(oldInput, newInput) {
    var oldHost = normalizeHost(oldInput);
    var newHost = normalizeHost(newInput);
    if (!newHost) {
        return Promise.resolve({ ok: false, error: "Invalid new hostname or URL" });
    }
    return getUserBlockedSites().then(function (current) {
        var index = current.indexOf(oldHost);
        if (index === -1) {
            return { ok: false, error: "Existing rule not found" };
        }
        if (oldHost !== newHost && current.includes(newHost)) {
            return { ok: false, error: "New rule already exists in exclusion list" };
        }
        current[index] = newHost;
        return saveUserBlockedSites(current).then(function (updated) {
            return { ok: true, sites: updated, edited: newHost };
        });
    });
}

if (typeof window !== "undefined") {
    window.BlockedSites = {
        BLOCKED_DOMAINS: BLOCKED_DOMAINS,
        USER_BLOCKED_SITES_KEY: USER_BLOCKED_SITES_KEY,
        normalizeHost: normalizeHost,
        matchesHostRule: matchesHostRule,
        isBlockedHost: isBlockedHost,
        isBlockedUrl: isBlockedUrl,
        isHostExcluded: isHostExcluded,
        getUserBlockedSites: getUserBlockedSites,
        saveUserBlockedSites: saveUserBlockedSites,
        addUserBlockedSite: addUserBlockedSite,
        removeUserBlockedSite: removeUserBlockedSite,
        editUserBlockedSite: editUserBlockedSite
    };
}

