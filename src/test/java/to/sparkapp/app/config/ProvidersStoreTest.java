package to.sparkapp.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import to.sparkapp.app.config.AiConfiguration.AiConfig;

class ProvidersStoreTest {
    @TempDir
    Path directory;

    @Test
    void providerRoundTripPreservesIdentityIconAndColorSource() {
        var store = store();
        var site = new AiConfig("custom-site", "Example", "https://example.com", "#AABBCC", "site.png", "manual");
        store.saveProviders(List.of(site));
        assertEquals(List.of(site), store.loadProviders());
    }

    @Test
    void olderProviderFilesKeepTheirValuesWithoutColorSource() throws Exception {
        Files.writeString(directory.resolve("providers.json"), """
                [{"id":"legacy","name":"Legacy","url":"https://example.com",
                  "color":"#123456","icon":"legacy.svg"}]
                """);

        var provider = store().loadProviders().getFirst();
        assertEquals("legacy", provider.id());
        assertEquals("#123456", provider.color());
        assertEquals("legacy.svg", provider.icon());
        assertNull(provider.colorSource());
    }

    private ProvidersStore store() {
        return new ProvidersStore(directory.resolve("providers.json").toFile(),
                new ProviderIcons(directory.resolve("icons").toFile()));
    }
}
