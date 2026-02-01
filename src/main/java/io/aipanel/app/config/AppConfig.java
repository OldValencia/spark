package io.aipanel.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    @Bean
    public AiConfiguration aiConfiguration() {
        return new AiConfiguration();
    }

    @Bean
    public AppPreferences appPreferences() {
        return new AppPreferences();
    }
}
