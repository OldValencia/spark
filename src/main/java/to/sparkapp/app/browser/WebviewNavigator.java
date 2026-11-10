package to.sparkapp.app.browser;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import lombok.Setter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import to.sparkapp.app.config.AiConfiguration.AiConfig;
import to.sparkapp.app.utils.UrlUtils;

@Slf4j
public class WebviewNavigator {

    private final Consumer<Runnable> dispatch;
    private final Consumer<String> load;
    private final Runnable applyZoom;

    private static final AtomicLong NAV_ID_SEQUENCE = new AtomicLong();

    private volatile long currentNavId = 0L;
    private volatile String currentUrl;
    private volatile String configBaseUrl;
    @Getter
    private volatile boolean externalPage;
    /** The address of the page on screen, which may belong to another site, or null while it is unknown. */
    @Getter
    private volatile String pageUrl;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "spark-nav-scheduler");
        t.setDaemon(true);

        return t;
    });

    @Setter
    private Consumer<String> onUrlChanged;

    WebviewNavigator(Consumer<Runnable> dispatch, Consumer<String> load, Runnable applyZoom) {
        this.dispatch = dispatch;
        this.load = load;
        this.applyZoom = applyZoom;
    }

    void handleUrlChange(String url) {
        if (!UrlUtils.isWebUrl(url)) {
            return;
        }

        externalPage = isExternalPage(url);
        pageUrl = url;

        if (UrlUtils.isRestorablePage(url, configBaseUrl)) {
            currentUrl = url;
        }

        if (onUrlChanged != null) {
            onUrlChanged.accept(url);
        }
    }

    /**
     * Switches to a provider, optionally opening a deeper page of it (for example the conversation
     * the user had open when the app was last closed).
     */
    void setCurrentConfig(AiConfig config, String targetUrl) {
        var url = targetUrl != null && UrlUtils.isSameHost(targetUrl, config.url()) ? targetUrl : config.url();

        log.info("WebviewNavigator: Changing config to: {}", UrlUtils.forLog(url));
        this.configBaseUrl = config.url();
        this.currentUrl = url;
        externalPage = isExternalPage(url);
        pageUrl = url;
        navigate(url);
    }

    /** A page the site opened has no address until it reports one. */
    void forgetPageUrl() {
        pageUrl = null;
    }

    /** Opens the last page on the site, after the site navigated to another one. */
    void returnToSitePage() {
        navigate(getCurrentUrl());
    }

    void navigate(String url) {
        if (!UrlUtils.isWebUrl(url)) {
            return;
        }

        if (UrlUtils.isRestorablePage(url, configBaseUrl)) {
            this.currentUrl = url;
        }

        final long navId = NAV_ID_SEQUENCE.incrementAndGet();
        this.currentNavId = navId;

        dispatch.accept(() -> {
            if (currentNavId == navId) {
                log.info("WebviewNavigator: Loading {} [Nav-{}]", UrlUtils.forLog(url), navId);
                load.accept(url);
            }
        });

        schedule(() -> {
            if (navId == currentNavId) {
                dispatch.accept(applyZoom);
            }
        });
    }

    String getCurrentUrl() {
        return currentUrl != null ? currentUrl : "about:blank";
    }

    boolean isExternalPage(String url) {
        return UrlUtils.isWebUrl(url) && configBaseUrl != null && !UrlUtils.isSameHost(url, configBaseUrl);
    }

    void shutdown() {
        scheduler.shutdownNow();
    }

    private void schedule(Runnable task) {
        if (scheduler.isShutdown()) {
            return;
        }

        try {
            scheduler.schedule(task, 500, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopped) {
            if (!scheduler.isShutdown()) {
                throw stopped;
            }
        }
    }
}
