package to.sparkapp.app.browser;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BrowserPageScripts {
    static final String INIT_SCRIPT = """
            (function() {
                document.addEventListener('wheel', function(e) {
                    if (!e.ctrlKey) return;
                    e.preventDefault();
                    window.sparkCall('zoom', e.deltaY > 0 ? 'down' : 'up');
                }, {passive: false});

                document.addEventListener('keydown', function(e) {
                    if (!e.ctrlKey) return;

                    // Zoom with support for non-English layouts using e.code
                    if (e.code === 'Equal' || e.code === 'NumpadAdd' || e.key === '=' || e.key === '+') {
                        e.preventDefault(); window.sparkCall('zoom', 'up');
                    } else if (e.code === 'Minus' || e.code === 'NumpadSubtract' || e.key === '-') {
                        e.preventDefault(); window.sparkCall('zoom', 'down');
                    } else if (e.code === 'Digit0' || e.code === 'Numpad0' || e.key === '0') {
                        e.preventDefault(); window.sparkCall('zoom', 'reset');
                    }
                });

                document.addEventListener('auxclick', function(e) {
                    if (e.button === 1) { // Middle mouse button
                        var a = e.target.closest('a');
                        if (a && a.href && (a.href.startsWith('http://') || a.href.startsWith('https://'))) {
                            e.preventDefault();
                            window.sparkCall('middleClicked', a.href);
                        }
                    }
                });

                document.addEventListener('DOMContentLoaded', function() {
                    window.sparkCall('documentReady', window.location.href);
                }, {once: true});
                window.sparkCall('urlChanged', window.location.href);
                var _push = history.pushState;
                history.pushState = function() { _push.apply(this, arguments); window.sparkCall('urlChanged', window.location.href); };
                var _replace = history.replaceState;
                history.replaceState = function() { _replace.apply(this, arguments); window.sparkCall('urlChanged', window.location.href); };
                window.addEventListener('popstate', function() { window.sparkCall('urlChanged', window.location.href); });
                window.addEventListener('hashchange', function() { window.sparkCall('urlChanged', window.location.href); });
            })();
            """;

}
