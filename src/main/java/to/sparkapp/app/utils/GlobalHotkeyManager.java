package to.sparkapp.app.utils;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.util.Duration;


import lombok.Getter;
import to.sparkapp.app.config.AppPreferences;
import to.sparkapp.app.windows.MainWindow;

public class GlobalHotkeyManager implements NativeKeyListener, NativeMouseInputListener {

    private static final int MOUSE_OFFSET = 10_000;

    private final MainWindow mainWindow;
    private final AppPreferences appPreferences;

    private final Set<Integer> pressedKeys = Collections.synchronizedSet(new HashSet<>());

    private volatile boolean recording = false;
    private Runnable onRecordComplete;

    private boolean hotkeyTriggered = false;
    private static final long STALE_KEYS_MILLIS = 1500;
    private long lastEventTime = System.currentTimeMillis();

    /** Lets a recording ignore the click or keys that started it. */
    private final FxTimer recordingDelay = FxTimer.once(Duration.millis(200), () -> {
        pressedKeys.clear();
        recording = true;
    });

    @Getter
    private boolean initialized = false;

    public GlobalHotkeyManager(MainWindow mainWindow, AppPreferences appPreferences) {
        this.mainWindow = mainWindow;
        this.appPreferences = appPreferences;

        var logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
    }

    public void start() {
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            GlobalScreen.addNativeMouseListener(this);
            GlobalScreen.addNativeMouseMotionListener(this);
            initialized = true;
        } catch (NativeHookException e) {
            initialized = false;
        }
    }

    public void stop() {
        if (!initialized) {
            return;
        }

        try {
            recordingDelay.stop();
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.removeNativeMouseListener(this);
            GlobalScreen.removeNativeMouseMotionListener(this);
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException ignored) {
        }
    }

    /**
     * Forgets keys whose release was missed, for example while another app held the keyboard, once
     * nothing has happened for a moment. Checked on the next event instead of with a timer that
     * would wake Spark every second.
     */
    private void noteKeyEvent() {
        var now = System.currentTimeMillis();

        if (now - lastEventTime > STALE_KEYS_MILLIS) {
            pressedKeys.clear();
            hotkeyTriggered = false;
        }

        lastEventTime = now;
    }

    public void startRecording(Runnable onRecordComplete) {
        if (!initialized) {
            return;
        }

        this.onRecordComplete = onRecordComplete;
        recording = false;
        pressedKeys.clear();

        recordingDelay.restart();
    }

    public void clearHotkey() {
        appPreferences.setHotkeyToStartApplication(null);
    }

    private void tryTriggerHotkey() {
        if (recording || hotkeyTriggered) {
            return;
        }

        var saved = appPreferences.getHotkeyToStartApplication();

        if (saved == null || saved.isEmpty()) {
            return;
        }

        synchronized (pressedKeys) {
            if (pressedKeys.containsAll(saved)) {
                hotkeyTriggered = true;

                long requestedAt = System.nanoTime();
                Platform.runLater(() -> toggleWindow(requestedAt));
            }
        }
    }

    private void toggleWindow(long requestedAt) {
        if (mainWindow.isShowing() && !mainWindow.isHiding()) {
            mainWindow.hideMainWindow();
        } else {
            mainWindow.showMainWindow(requestedAt);
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        noteKeyEvent();

        var code = e.getKeyCode();

        if (recording) {
            if (code == NativeKeyEvent.VC_ESCAPE) {
                finishRecording(false, false);

                return;
            }

            if (code == NativeKeyEvent.VC_DELETE || code == NativeKeyEvent.VC_BACKSPACE) {
                finishRecording(true, true);

                return;
            }

            pressedKeys.add(code);

            if (pressedKeys.size() >= 4) {
                finishRecording(true, false);
            }
        } else {
            pressedKeys.add(code);
            tryTriggerHotkey();
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        noteKeyEvent();

        if (recording) {
            if (!pressedKeys.isEmpty()) {
                finishRecording(true, false);
            }
        } else {
            pressedKeys.remove(e.getKeyCode());
            hotkeyTriggered = false;
        }
    }

    @Override
    public void nativeMousePressed(NativeMouseEvent e) {
        noteKeyEvent();
        pressedKeys.add(MOUSE_OFFSET + e.getButton());

        if (!recording) {
            tryTriggerHotkey();
            mainWindow.onGlobalMousePressed();
        }
    }

    @Override
    public void nativeMouseReleased(NativeMouseEvent e) {
        noteKeyEvent();

        var code = MOUSE_OFFSET + e.getButton();

        if (recording) {
            var simpleClick = false;
            synchronized (pressedKeys) {
                simpleClick = pressedKeys.size() == 1 &&
                        (pressedKeys.contains(MOUSE_OFFSET + 1) || pressedKeys.contains(MOUSE_OFFSET + 2));
            }

            if (!pressedKeys.isEmpty() && !simpleClick) {
                finishRecording(true, false);
            } else {
                pressedKeys.remove(code);
            }
        } else {
            pressedKeys.remove(code);
            hotkeyTriggered = false;
        }
    }

    private void finishRecording(boolean save, boolean forceClear) {
        recording = false;

        if (save) {
            synchronized (pressedKeys) {
                if (forceClear || pressedKeys.isEmpty()) {
                    appPreferences.setHotkeyToStartApplication(null);
                } else {
                    appPreferences.setHotkeyToStartApplication(new ArrayList<>(pressedKeys));
                }
            }
        }

        pressedKeys.clear();

        if (onRecordComplete != null) {
            Platform.runLater(onRecordComplete);
        }
    }

    @Override
    public void nativeMouseMoved(NativeMouseEvent e) {
        if (!recording) {
            mainWindow.onGlobalMouseMoved(e.getX(), e.getY(), System.nanoTime());
        }
    }

    @Override
    public void nativeMouseDragged(NativeMouseEvent e) {
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
    }

    @Override
    public void nativeMouseClicked(NativeMouseEvent e) {
    }

    public static String getHotkeyText(List<Integer> codes) {
        if (codes == null || codes.isEmpty()) {
            return "None";
        }

        return codes.stream().map(code -> {
            if (code >= MOUSE_OFFSET) {
                return "Mouse " + (code - MOUSE_OFFSET);
            }

            return NativeKeyEvent.getKeyText(code);
        }).collect(Collectors.joining(" + "));
    }
}
