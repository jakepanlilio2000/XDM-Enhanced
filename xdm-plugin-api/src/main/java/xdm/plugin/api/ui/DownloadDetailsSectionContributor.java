package xdm.plugin.api.ui;

import xdm.plugin.api.PluginContext;
import xdm.plugin.api.models.DownloadInfo;
import java.awt.Component;

/**
 * Contributes a modular details section, tab, or metadata panel to download inspection dialogs.
 */
public interface DownloadDetailsSectionContributor {

    /**
     * Unique identifier for this details section.
     */
    String getSectionId();

    /**
     * User-visible title for the section header or tab label.
     */
    String getTitle();

    /**
     * Determines whether this section is applicable to the given download.
     *
     * @param download Snapshot of the target download
     * @return true if this section should be rendered, false otherwise
     */
    default boolean isApplicable(DownloadInfo download) {
        return true;
    }

    /**
     * Instantiates the Swing component representing this details section.
     * Guaranteed to be called on the Swing Event Dispatch Thread (EDT).
     *
     * @param download Target download metadata
     * @param context Active plugin context
     * @return Swing component to embed in the details view
     */
    Component createSection(DownloadInfo download, PluginContext context);
}
