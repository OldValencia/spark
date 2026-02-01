package io.aipanel.app.config;

import lombok.Getter;
import lombok.Setter;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AiConfiguration {

    private List<AiConfig> configurations;

    public AiConfiguration() {
        var yaml = new Yaml();
        var inputStream = this.getClass()
                .getClassLoader()
                .getResourceAsStream("ai-configurations.yml");

        Map<String, List<Map<String, String>>> obj = yaml.load(inputStream);

        this.configurations = obj.get("configurations").stream()
                .map(m -> new AiConfig(m.get("name"), m.get("url"), m.get("icon"), m.get("color")))
                .toList();
    }

    public record AiConfig(String name, String url, String icon, String color) {
    }
}