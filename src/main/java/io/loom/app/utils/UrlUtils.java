package io.loom.app.utils;

import lombok.extern.slf4j.Slf4j;

import java.awt.Desktop;
import java.net.URI;

@Slf4j
public class UrlUtils {

    public static void openLink(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            log.error("Error opening link", e);
        }
    }
}