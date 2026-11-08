package to.sparkapp.app.extensions;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Asks an extension's update service (the Chrome Web Store protocol) for a newer package and downloads it. */
final class ExtensionUpdateService {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String USER_AGENT = "Spark extension updater";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    record Update(String version, URI codebase) {
    }

    Optional<Update> check(ExtensionSource source, String currentVersion, String browserVersion)
            throws IOException, InterruptedException {
        var query = "id=" + source.storeId() + "&v=" + currentVersion + "&uc";
        var separator = source.updateUrl().contains("?") ? "&" : "?";
        var uri = URI.create(source.updateUrl() + separator + "response=updatecheck&acceptformat=crx3&prodversion="
                + browserVersion + "&x=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
        var response = http.send(request(uri), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("The update service answered with HTTP " + response.statusCode());
        }

        return parse(response.body()).filter(update -> ExtensionVersion.isNewer(update.version(), currentVersion));
    }

    /** Downloads the package next to the managed folders; the caller deletes it after importing. */
    Path download(Update update, Path root) throws IOException, InterruptedException {
        if (!"https".equals(update.codebase().getScheme())) {
            throw new IOException("The update service returned an insecure download address");
        }

        var target = root.resolve(".spark-download-" + UUID.randomUUID() + ".crx");
        var response = http.send(request(update.codebase()), HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(target);
            throw new IOException("The extension download failed with HTTP " + response.statusCode());
        }

        return target;
    }

    private static HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("User-Agent", USER_AGENT).GET().build();
    }

    /** Reads `updatecheck status="ok" version codebase` from the service's XML answer. */
    private static Optional<Update> parse(String xml) throws IOException {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            var checks = document.getElementsByTagName("updatecheck");
            for (var index = 0; index < checks.getLength(); index++) {
                var check = (Element) checks.item(index);
                if ("ok".equals(check.getAttribute("status")) && !check.getAttribute("codebase").isBlank()) {
                    return Optional.of(new Update(check.getAttribute("version"), URI.create(check.getAttribute("codebase"))));
                }
            }

            return Optional.empty();
        } catch (ParserConfigurationException | SAXException | IllegalArgumentException error) {
            throw new IOException("The update service answer could not be read", error);
        }
    }
}
