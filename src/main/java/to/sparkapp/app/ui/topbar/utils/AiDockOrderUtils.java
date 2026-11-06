package to.sparkapp.app.ui.topbar.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.ui.topbar.components.DockItemNode;

public class AiDockOrderUtils {

    public static List<AiConfig> applyCustomOrder(List<AiConfig> configs, AppPreferences appPreferences) {
        var savedOrder = appPreferences.getAiOrder();

        if (savedOrder.isEmpty()) {
            return configs;
        }

        var ordered = new ArrayList<AiConfig>();

        for (var key : savedOrder) {
            configs.stream().filter(c -> c.id().equals(key) || c.url().equals(key)).filter(c -> !ordered.contains(c))
                    .findFirst().ifPresent(ordered::add);
        }

        for (var config : configs) {
            if (!ordered.contains(config)) {
                ordered.add(config);
            }
        }

        return ordered;
    }

    public static void saveCurrentOrder(List<DockItemNode> dockItems, AppPreferences appPreferences) {
        var urls = dockItems.stream().map(item -> item.getConfig().id()).collect(Collectors.toList());
        appPreferences.setAiOrder(urls);
    }
}
