(() => {
    if (!globalThis.chrome?.runtime || chrome.runtime.id !== __SPARK_EXTENSION_ID__) return false;
    const key = Symbol.for('spark.extensionContext');
    const windowId = __SPARK_WINDOW_ID__;
    const bootstrap = __SPARK_BOOTSTRAP__;
    const state = globalThis[key] || {windowId};
    if (!bootstrap) state.windowId = windowId;
    state.namespaces ||= new WeakSet();
    const install = api => {
        if (!api?.tabs || !api?.windows || state.namespaces.has(api.tabs)) return;
        state.namespaces.add(api.tabs);
        const tabs = api.tabs;
        const windows = api.windows;
        const query = tabs.query.bind(tabs);
        const getWindow = windows.get.bind(windows);
        const invoke = (original, args, callback, transform = value => value) => {
            if (typeof callback === 'function') {
                return original(...args, value => callback(transform(value)));
            }
            return original(...args).then(transform);
        };
        const describe = window => window && window.id === state.windowId ? {...window, type: 'normal'} : window;
        tabs.query = (criteria, callback) => {
            const mapped = {...criteria};
            const current = mapped.currentWindow || mapped.lastFocusedWindow || mapped.windowId === -2
                || (mapped.active && mapped.windowId === undefined);
            if (state.windowId !== null && current) {
                mapped.windowId = state.windowId;
                delete mapped.currentWindow;
                delete mapped.lastFocusedWindow;
                if (mapped.windowType === 'normal') delete mapped.windowType;
            }
            return typeof callback === 'function' ? query(mapped, callback) : query(mapped);
        };
        for (const method of ['getCurrent', 'getLastFocused']) {
            const original = windows[method].bind(windows);
            windows[method] = (options, callback) => {
                if (typeof options === 'function') {
                    callback = options;
                    options = {};
                }
                if (state.windowId === null) return invoke(original, [options || {}], callback);
                return invoke(getWindow, [state.windowId, options || {}], callback, describe);
            };
        }
        const getAll = windows.getAll.bind(windows);
        windows.getAll = (options, callback) => {
            if (typeof options === 'function') {
                callback = options;
                options = {};
            }
            if (state.windowId === null) return invoke(getAll, [options || {}], callback);
            const mapped = {...options};
            delete mapped.windowTypes;
            return invoke(getAll, [mapped], callback, result => result?.map(describe)
                .filter(window => !options?.windowTypes || options.windowTypes.includes(window.type)));
        };
    };
    install(globalThis.chrome);
    install(globalThis.browser);
    globalThis[key] = state;
    return true;
})()
