package io.loom.app.utils;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;
import io.loom.app.config.AppPreferences;
import io.loom.app.windows.MainWindow;
import lombok.Getter;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GlobalHotkeyManager implements NativeKeyListener, NativeMouseInputListener {

    private static final int MOUSE_OFFSET = 10_000;

    private final MainWindow mainWindow;
    private final AppPreferences appPreferences;

    private final Set<Integer> pressedKeys = Collections.synchronizedSet(new HashSet<>());

    private volatile boolean recording = false;
    private Runnable onRecordComplete;

    private boolean hotkeyTriggered = false;
    private long lastEventTime = System.currentTimeMillis();

    private final Timer watchdogTimer;

    @Getter
    private boolean initialized = false;

    public GlobalHotkeyManager(MainWindow mainWindow, AppPreferences appPreferences) {
        this.mainWindow = mainWindow;
        this.appPreferences = appPreferences;

        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);

        watchdogTimer = new Timer(1000, e -> {
            if (System.currentTimeMillis() - lastEventTime > 1500) {
                pressedKeys.clear();
                hotkeyTriggered = false;
            }
        });
    }

    public void start() {
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            GlobalScreen.addNativeMouseListener(this);
            watchdogTimer.start();
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
            watchdogTimer.stop();
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.removeNativeMouseListener(this);
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException ignored) {
        }
    }

    public void startRecording(Runnable onRecordComplete) {
        if (!initialized) {
            return;
        }

        this.onRecordComplete = onRecordComplete;
        recording = false;
        pressedKeys.clear();

        new Timer(200, e -> {
            ((Timer) e.getSource()).stop();
            pressedKeys.clear();
            recording = true;
        }).start();
    }

    public void clearHotkey() {
        appPreferences.setHotkeyToStartApplication(null);
    }

    private void tryTriggerHotkey() {
        if (recording || hotkeyTriggered) return;

        var saved = appPreferences.getHotkeyToStartApplication();
        if (saved == null || saved.isEmpty()) return;

        synchronized (pressedKeys) {
            if (pressedKeys.containsAll(saved)) {
                hotkeyTriggered = true;
                SwingUtilities.invokeLater(this::toggleWindow);
            }
        }
    }

    private void toggleWindow() {
        if (mainWindow.isVisible() && mainWindow.isActive()) {
            mainWindow.setVisible(false);
        } else {
            mainWindow.setVisible(true);
            mainWindow.setExtendedState(JFrame.NORMAL);
            mainWindow.toFront();
            mainWindow.requestFocus();
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        lastEventTime = System.currentTimeMillis();
        int code = e.getKeyCode();

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
        lastEventTime = System.currentTimeMillis();

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
        lastEventTime = System.currentTimeMillis();
        pressedKeys.add(MOUSE_OFFSET + e.getButton());

        if (!recording) {
            tryTriggerHotkey();
        }
    }

    @Override
    public void nativeMouseReleased(NativeMouseEvent e) {
        lastEventTime = System.currentTimeMillis();
        int code = MOUSE_OFFSET + e.getButton();

        if (recording) {
            boolean simpleClick;
            synchronized (pressedKeys) {
                simpleClick = pressedKeys.size() == 1 && (pressedKeys.contains(MOUSE_OFFSET + 1) || pressedKeys.contains(MOUSE_OFFSET + 2));
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
            SwingUtilities.invokeLater(onRecordComplete);
        }
    }

    @Override
    public void nativeMouseMoved(NativeMouseEvent e) {
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
        if (codes == null || codes.isEmpty()) return "None";

        return codes.stream()
                .map(code -> {
                    if (code >= MOUSE_OFFSET) {
                        return "Mouse " + (code - MOUSE_OFFSET);
                    }
                    return NativeKeyEvent.getKeyText(code);
                })
                .collect(Collectors.joining(" + "));
    }
}
