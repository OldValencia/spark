(() => {
    if (!globalThis.chrome?.runtime || chrome.runtime.id !== __SPARK_EXTENSION_ID__) return false;

    // The bootstrap runs first in background code; the document variant runs in Spark's popup host.
    const bootstrap = __SPARK_BOOTSTRAP__;
    const key = Symbol.for('spark.extensionContext');
    const CHANNEL = 'spark.extensionContext';
    const STARTUP_WAIT_MS = 150;
    const state = globalThis[key] || {windowId: null};
    globalThis[key] = state;
    if (!bootstrap) state.windowId = __SPARK_WINDOW_ID__;
    state.namespaces ||= new WeakSet();

    const hasCallback = args => typeof args[args.length - 1] === 'function';

    // A freshly started worker briefly holds window queries until an open popup answers.
    const afterContext = action => (...args) => {
        if (!state.waiting) return action(...args);
        const pending = state.ready.then(() => action(...args));
        return hasCallback(args) ? undefined : pending;
    };

    const connect = () => {
        if (state.channel || typeof BroadcastChannel !== 'function') return;
        const channel = new BroadcastChannel(CHANNEL);
        state.channel = channel;
        if (bootstrap) {
            state.waiting = true;
            state.ready = new Promise(resolve => {
                state.finishWaiting = () => {
                    state.waiting = false;
                    resolve();
                };
                setTimeout(state.finishWaiting, STARTUP_WAIT_MS);
            });
            channel.onmessage = ({data}) => {
                if (data?.type !== 'context') return;
                state.windowId = data.windowId ?? null;
                state.finishWaiting();
            };
            channel.postMessage({type: 'request'});
        } else {
            const announce = windowId => channel.postMessage({type: 'context', windowId});
            channel.onmessage = ({data}) => {
                if (data?.type === 'request') announce(state.windowId);
            };
            state.release = () => announce(null);
            addEventListener('pagehide', state.release, {once: true});
            announce(state.windowId);
        }
    };

    const invoke = (original, args, callback, transform = value => value) => {
        if (typeof callback === 'function') {
            return original(...args, value => callback(transform(value)));
        }
        return original(...args).then(transform);
    };

    const describe = window => window && window.id === state.windowId ? {...window, type: 'normal'} : window;

    const isCurrentWindowQuery = criteria => criteria.currentWindow === true || criteria.lastFocusedWindow === true
        || criteria.windowId === -2 || (criteria.active === true && criteria.windowId === undefined
            && criteria.currentWindow === undefined && criteria.lastFocusedWindow === undefined);

    const install = api => {
        if (!api?.tabs || !api?.windows || state.namespaces.has(api.tabs)) return;
        state.namespaces.add(api.tabs);
        const tabs = api.tabs;
        const windows = api.windows;
        const query = tabs.query.bind(tabs);
        const getWindow = windows.get.bind(windows);

        tabs.query = afterContext((criteria, callback) => {
            const mapped = {...criteria};
            if (state.windowId !== null && isCurrentWindowQuery(criteria)) {
                mapped.windowId = state.windowId;
                delete mapped.currentWindow;
                delete mapped.lastFocusedWindow;
                if (mapped.windowType === 'normal') delete mapped.windowType;
            }
            return typeof callback === 'function' ? query(mapped, callback) : query(mapped);
        });

        for (const method of ['getCurrent', 'getLastFocused']) {
            const original = windows[method].bind(windows);
            windows[method] = afterContext((options, callback) => {
                if (typeof options === 'function') {
                    callback = options;
                    options = {};
                }
                if (state.windowId === null) return invoke(original, [options || {}], callback);
                return invoke(getWindow, [state.windowId, options || {}], callback, describe);
            });
        }

        const getAll = windows.getAll.bind(windows);
        windows.getAll = afterContext((options, callback) => {
            if (typeof options === 'function') {
                callback = options;
                options = {};
            }
            if (state.windowId === null) return invoke(getAll, [options || {}], callback);
            const mapped = {...options};
            delete mapped.windowTypes;
            return invoke(getAll, [mapped], callback, result => result?.map(describe)
                .filter(window => !options?.windowTypes || options.windowTypes.includes(window.type)));
        });
    };

    connect();
    install(globalThis.chrome);
    install(globalThis.browser);
    return true;
})()
