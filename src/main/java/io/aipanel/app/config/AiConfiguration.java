package io.aipanel.app.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "ai")
public class AiConfiguration {

    private List<AiConfig> configurations = new ArrayList<>();

    @Getter
    @Setter
    public static class AiConfig {
        private String name;
        private String url;
        private String icon;
    }
}
