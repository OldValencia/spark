package to.sparkapp.app.ui.topbar;

import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.ui.dialogs.ProviderEditDialog;

record SiteEditRequest(AiConfig original, String name, String url, String color, boolean useIconColor,
                       boolean manualColor, boolean deleted) {
    static SiteEditRequest from(AiConfig original, ProviderEditDialog dialog) {
        return new SiteEditRequest(original, dialog.getProviderName(), dialog.getProviderUrl(),
                dialog.getProviderColor(), dialog.isUseIconColor(), dialog.isManualColor(), dialog.isDeleted());
    }
}
