(() => {
    if (window !== window.top || location.protocol !== 'chrome-extension:') return;

    const MESSAGE_PREFIX = 'spark-popup-size:';
    const SETTLE_DELAY_MS = 60;
    const FIRST_SETTLE_DELAY_MS = 50;
    let timer = 0;
    let started = false;
    let lastPosted = '';

    // Probes intrinsic width without leaving styles or observers in the extension's own state.
    const measure = () => {
        const body = document.body;
        if (!body) return null;
        const original = body.style.cssText;
        try {
            const declaredWidth = body.computedStyleMap ? String(body.computedStyleMap().get('width')) : '';
            if (declaredWidth === 'auto') {
                body.style.setProperty('width', 'max-content', 'important');
                body.style.setProperty('max-width', '800px', 'important');
            }
            const box = body.getBoundingClientRect();
            const style = getComputedStyle(body);
            const marginX = (parseFloat(style.marginLeft) || 0) + (parseFloat(style.marginRight) || 0);
            const marginY = (parseFloat(style.marginTop) || 0) + (parseFloat(style.marginBottom) || 0);
            const quirksMode = document.compatMode === 'BackCompat';
            const width = Math.max(box.width, quirksMode ? 0 : body.scrollWidth) + marginX;
            const height = Math.max(box.height, quirksMode ? 0 : body.scrollHeight) + marginY;
            const root = document.documentElement;
            const rootStyle = getComputedStyle(root);
            return {
                width: Math.ceil(width), height: Math.ceil(height), ratio: devicePixelRatio || 1,
                viewport: {width: innerWidth, height: innerHeight},
                body: {width: box.width, height: box.height, scrollWidth: body.scrollWidth,
                    scrollHeight: body.scrollHeight, cssHeight: style.height, minHeight: style.minHeight,
                    overflow: style.overflow},
                root: {width: root.clientWidth, height: root.clientHeight, scrollHeight: root.scrollHeight,
                    cssHeight: rootStyle.height, minHeight: rootStyle.minHeight, overflow: rootStyle.overflow}
            };
        } finally {
            if (body.style.cssText !== original) body.style.cssText = original;
        }
    };

    const post = () => {
        timer = 0;
        const value = measure();
        const json = value && JSON.stringify(value);
        if (!json || json === lastPosted || !window.chrome?.webview) return;
        lastPosted = json;
        chrome.webview.postMessage(MESSAGE_PREFIX + json);
    };

    const schedule = (delay = SETTLE_DELAY_MS) => {
        if (!started) return;
        clearTimeout(timer);
        timer = setTimeout(post, delay);
    };

    // Internal scroll panels keep their own constraints, so only the top-level layout is observed.
    const resizes = new ResizeObserver(() => schedule());
    const observeLayout = () => {
        resizes.disconnect();
        resizes.observe(document.documentElement);
        resizes.observe(document.body);
        for (const child of document.body.children) resizes.observe(child);
    };

    const start = () => {
        if (started || !document.body) return;
        started = true;
        observeLayout();
        new MutationObserver(() => {
            observeLayout();
            schedule();
        }).observe(document.body, {childList: true});
        addEventListener('resize', () => schedule());
        schedule(FIRST_SETTLE_DELAY_MS);
    };

    const whenLoaded = () => (document.fonts ? document.fonts.ready : Promise.resolve()).then(start);
    if (document.readyState === 'complete') {
        whenLoaded();
    } else {
        addEventListener('load', whenLoaded, {once: true});
    }

    // Java asks again after its own resize animation, and polls only if messaging is unavailable.
    globalThis[Symbol.for('spark.popupMeasure')] = () => {
        lastPosted = '';
        schedule(0);
    };
    globalThis[Symbol.for('spark.popupSize')] = () => started ? measure() : null;
})();
