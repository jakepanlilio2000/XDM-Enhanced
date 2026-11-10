package xdm.plugin.api.models;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PluginMetadata {
    private final String id;
    private final String name;
    private final String version;
    private final String author;
    private final String description;
    private final String minAppVersion;
    private final String minApiVersion;
    private final String entryPoint;
    private final Set<String> capabilities;
    private final List<String> dependencies;
    private final String license;
    private final String homepage;
    private final String checksumSha256;
    private final boolean builtIn;

    public PluginMetadata(String id, String name, String version, String author, String description,
                          String minAppVersion, String minApiVersion, String entryPoint,
                          Set<String> capabilities, List<String> dependencies,
                          String license, String homepage, String checksumSha256, boolean builtIn) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.minAppVersion = minAppVersion;
        this.minApiVersion = minApiVersion;
        this.entryPoint = entryPoint;
        this.capabilities = capabilities != null ? Collections.unmodifiableSet(capabilities) : Collections.emptySet();
        this.dependencies = dependencies != null ? Collections.unmodifiableList(dependencies) : Collections.emptyList();
        this.license = license;
        this.homepage = homepage;
        this.checksumSha256 = checksumSha256;
        this.builtIn = builtIn;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getAuthor() { return author; }
    public String getDescription() { return description; }
    public String getMinAppVersion() { return minAppVersion; }
    public String getMinApiVersion() { return minApiVersion; }
    public String getEntryPoint() { return entryPoint; }
    public Set<String> getCapabilities() { return capabilities; }
    public List<String> getDependencies() { return dependencies; }
    public String getLicense() { return license; }
    public String getHomepage() { return homepage; }
    public String getChecksumSha256() { return checksumSha256; }
    public boolean isBuiltIn() { return builtIn; }
}

