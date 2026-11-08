package to.sparkapp.app.browser;

import static to.sparkapp.app.browser.ComCalls.call;
import static to.sparkapp.app.browser.ComCalls.check;
import static to.sparkapp.app.browser.ComCalls.release;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/** Extension operations must be submitted on the owning browser thread. */
final class BrowserExtensions {
    private static final int PROFILE_ADD_EXTENSION = 22;
    private static final int PROFILE_GET_EXTENSIONS = 23;
    private static final int LIST_COUNT = 3;
    private static final int LIST_ITEM = 4;
    private static final int EXTENSION_ID = 3;
    private static final int EXTENSION_NAME = 4;
    private static final int EXTENSION_REMOVE = 5;
    private static final int EXTENSION_IS_ENABLED = 6;
    private static final int EXTENSION_ENABLE = 7;
    private static final String ADD_COMPLETED = "df1aab27-82b9-4ab6-aae8-017a49398c14";
    private static final String LIST_COMPLETED = "fce16a1c-f107-4601-8b75-fc4940ae25d0";
    private static final String REMOVE_COMPLETED = "8e41909a-9b18-4bb1-8cdf-930f467a50be";
    private static final String ENABLE_COMPLETED = "30c186ce-7fad-421f-a3bc-a8eaf071ddb8";

    private final Pointer core;

    BrowserExtensions(Pointer core) {
        this.core = core;
    }

    CompletableFuture<BrowserExtension> install(Path directory) {
        var result = new CompletableFuture<BrowserExtension>();
        BrowserExtensionProfile.use(core, profile -> {
            try (var callback = new BrowserAsyncCallback(ADD_COMPLETED, (status, extension) -> {
                try {
                    check(status);
                    result.complete(describe(extension));
                } catch (Exception error) {
                    result.completeExceptionally(error);
                }
            })) {
                var path = new WString(directory.toAbsolutePath().toString());
                check(call(profile, PROFILE_ADD_EXTENSION, path, callback.pointer()));
            }
        }, result);

        return result;
    }

    CompletableFuture<List<BrowserExtension>> list() {
        var result = new CompletableFuture<List<BrowserExtension>>();
        visit((items, count) -> {
            var extensions = new ArrayList<BrowserExtension>();
            for (var index = 0; index < count; index++) {
                var extension = item(items, index);
                try {
                    extensions.add(describe(extension));
                } finally {
                    release(extension);
                }
            }
            result.complete(List.copyOf(extensions));
        }, result);

        return result;
    }

    CompletableFuture<Void> setEnabled(String id, boolean enabled) {
        return withExtension(id, ENABLE_COMPLETED,
                (extension, callback) -> check(call(extension, EXTENSION_ENABLE, enabled ? 1 : 0, callback)));
    }

    CompletableFuture<Void> remove(String id) {
        return withExtension(id, REMOVE_COMPLETED,
                (extension, callback) -> check(call(extension, EXTENSION_REMOVE, callback)));
    }

    private CompletableFuture<Void> withExtension(String id, String callbackIid, ExtensionCommand command) {
        var result = new CompletableFuture<Void>();
        visit((items, count) -> {
            for (var index = 0; index < count; index++) {
                var extension = item(items, index);
                try {
                    if (text(extension, EXTENSION_ID).equals(id)) {
                        submit(extension, callbackIid, command, result);
                        return;
                    }
                } finally {
                    release(extension);
                }
            }
            result.completeExceptionally(new IllegalStateException("The extension is no longer installed"));
        }, result);

        return result;
    }

    private void submit(Pointer extension, String callbackIid, ExtensionCommand command, CompletableFuture<Void> result) {
        Consumer<Boolean> completion = success -> {
            if (success) {
                result.complete(null);
            } else {
                result.completeExceptionally(new IllegalStateException("Extension operation failed"));
            }
        };
        try (var callback = new BrowserStatusCallback(callbackIid, completion)) {
            command.run(extension, callback.pointer());
        }
    }

    private void visit(ListVisitor visitor, CompletableFuture<?> result) {
        BrowserExtensionProfile.use(core, profile -> {
            try (var callback = new BrowserAsyncCallback(LIST_COMPLETED, (status, items) -> {
                try {
                    check(status);
                    var count = new IntByReference();
                    check(call(items, LIST_COUNT, count));
                    visitor.accept(items, count.getValue());
                } catch (Exception error) {
                    result.completeExceptionally(error);
                }
            })) {
                check(call(profile, PROFILE_GET_EXTENSIONS, callback.pointer()));
            }
        }, result);
    }

    private Pointer item(Pointer items, int index) {
        var out = new PointerByReference();
        check(call(items, LIST_ITEM, index, out));

        return out.getValue();
    }

    private BrowserExtension describe(Pointer extension) {
        var enabled = new IntByReference();
        check(call(extension, EXTENSION_IS_ENABLED, enabled));

        return new BrowserExtension(text(extension, EXTENSION_ID), text(extension, EXTENSION_NAME), enabled.getValue() != 0);
    }

    private String text(Pointer object, int slot) {
        var value = new PointerByReference();
        check(call(object, slot, value));
        try {
            return value.getValue().getWideString(0);
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(value.getValue());
        }
    }

    private interface ListVisitor {
        void accept(Pointer items, int count);
    }

    private interface ExtensionCommand {
        void run(Pointer extension, Pointer callback);
    }
}
