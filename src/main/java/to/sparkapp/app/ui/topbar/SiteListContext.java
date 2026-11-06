package to.sparkapp.app.ui.topbar;

import java.util.function.Consumer;

import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.topbar.components.AiDock;

record SiteListContext(AiDock dock, ListView<AiConfig> list, TextField search, AppPreferences preferences,
                       Consumer<AiConfig> choose, Consumer<AiConfig> edit) {
}
