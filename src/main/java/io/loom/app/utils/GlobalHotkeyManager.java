package io.loom.app.utils;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;
import io.loom.app.config.AppPreferences;
import io.loom.app.windows.MainWindow;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GlobalHotkeyManager implements NativeKeyListener, NativeMouseInputListener {

    private final MainWindow mainWindow;
    private final AppPreferences appPreferences;

    private final Set<Integer> pressedKeys = new HashSet<>();

    private boolean isRecording = false;
    private Runnable onRecordComplete;

    public GlobalHotkeyManager(MainWindow mainWindow, AppPreferences appPreferences) {
        this.mainWindow = mainWindow;
        this.appPreferences = appPreferences;

        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
    }

    public void start() {
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            GlobalScreen.addNativeMouseListener(this);
        } catch (NativeHookException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        try {
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.removeNativeMouseListener(this);
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException e) {
            e.printStackTrace();
        }
    }

    public void startRecording(Runnable onRecordComplete) {
        this.onRecordComplete = onRecordComplete;
        this.pressedKeys.clear();
        new Timer(200, e -> {
            ((Timer) e.getSource()).stop();
            pressedKeys.clear();
            isRecording = true;
        }).start();
    }

    public void clearHotkey() {
        appPreferences.setHotkeyToStartApplication(null);
    }

    private void checkHotkey() {
        if (isRecording) return;

        var saved = appPreferences.getHotkeyToStartApplication();
        if (saved == null || saved.isEmpty()) return;

        if (pressedKeys.size() == saved.size() && pressedKeys.containsAll(saved)) {
            pressedKeys.clear();
            SwingUtilities.invokeLater(this::toggleWindow);
        }
    }

    private void toggleWindow() {
        var frame = mainWindow.getFrame();
        if (frame.isVisible() && frame.isActive()) {
            frame.setVisible(false);
        } else {
            frame.setVisible(true);
            frame.setExtendedState(JFrame.NORMAL);
            frame.toFront();
            frame.requestFocus();
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        int code = e.getKeyCode();

        if (isRecording) {
            if (code == NativeKeyEvent.VC_ESCAPE) {
                finishRecording(false);
                return;
            }
            if (code == NativeKeyEvent.VC_DELETE || code == NativeKeyEvent.VC_BACKSPACE) {
                finishRecording(true, true);
                return;
            }

            pressedKeys.add(code);
            if (pressedKeys.size() >= 4) {
                finishRecording(true);
            }
        } else {
            pressedKeys.add(code);
            checkHotkey();
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        if (isRecording) {
            if (!pressedKeys.isEmpty()) {
                finishRecording(true);
            }
        } else {
            pressedKeys.remove(e.getKeyCode());
        }
    }

    @Override
    public void nativeMousePressed(NativeMouseEvent e) {
        var mouseCode = 10000 + e.getButton();
        pressedKeys.add(mouseCode);

        if (!isRecording) {
            checkHotkey();
        }
    }

    @Override
    public void nativeMouseReleased(NativeMouseEvent e) {
        int mouseCode = 10000 + e.getButton();

        if (isRecording) {
            boolean isSimpleClick = pressedKeys.size() == 1 && (pressedKeys.contains(10001) || pressedKeys.contains(10002));

            if (!pressedKeys.isEmpty() && !isSimpleClick) {
                finishRecording(true);
            } else {
                pressedKeys.remove(mouseCode);
            }
        } else {
            pressedKeys.remove(mouseCode);
        }
    }

    private void finishRecording(boolean save) {
        finishRecording(save, false);
    }

    private void finishRecording(boolean save, boolean forceClear) {
        isRecording = false;

        if (save) {
            if (forceClear || pressedKeys.isEmpty()) {
                appPreferences.setHotkeyToStartApplication(null);
            } else {
                appPreferences.setHotkeyToStartApplication(new ArrayList<>(pressedKeys));
            }
        } else {
            pressedKeys.clear();
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
                    if (code > 10000) return "Mouse " + (code - 10000);
                    return NativeKeyEvent.getKeyText(code);
                }).collect(Collectors.joining(" + "));
    }
}