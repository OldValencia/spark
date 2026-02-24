package io.loom.app.ui.topbar.utils;

import io.loom.app.config.AiConfiguration;
import io.loom.app.config.AppPreferences;
import io.loom.app.ui.topbar.components.DockItemNode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AiDockOrderUtils {

    public static List<AiConfiguration.AiConfig> applyCustomOrder(List<AiConfiguration.AiConfig> configs, AppPreferences appPreferences) {
        var savedOrder = appPreferences.getAiOrder();
        if (savedOrder.isEmpty()) {
            return configs;
        }

        var ordered = new ArrayList<AiConfiguration.AiConfig>();
        for (var url : savedOrder) {
            if (url.startsWith("http")) {
                configs.stream()
                        .filter(c -> c.url().equals(url))
                        .findFirst()
                        .ifPresent(ordered::add);
            }
        }

        for (var config : configs) {
            if (!ordered.contains(config)) {
                ordered.add(config);
            }
        }

        return ordered;
    }

    public static void saveCurrentOrder(List<DockItemNode> dockItems, AppPreferences appPreferences) {
        var urls = dockItems.stream()
                .map(item -> item.getConfig().url())
                .collect(Collectors.toList());
        appPreferences.setAiOrder(urls);
    }
}
