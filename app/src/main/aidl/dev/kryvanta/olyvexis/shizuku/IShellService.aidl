package dev.kryvanta.olyvexis.shizuku;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import dev.kryvanta.olyvexis.shizuku.ShellResult;

// Runs inside Shizuku's privileged shell process (uid 2000).
interface IShellService {
    // Transaction code reserved by Shizuku for service teardown.
    void destroy() = 16777114;

    void exit() = 1;

    ShellResult exec(String command, long timeoutMs) = 2;

    int createVirtualDisplay(String name, int width, int height, int densityDpi,
            in Surface surface, boolean systemDecorations) = 3;

    void resizeVirtualDisplay(int displayId, int width, int height, int densityDpi) = 4;

    void setVirtualDisplaySurface(int displayId, in Surface surface) = 5;

    void releaseVirtualDisplay(int displayId) = 6;

    ShellResult startActivityOnDisplay(String component, int displayId, boolean freeform) = 7;

    // Move any existing task from phone display to this window display before launching.
    ShellResult launchAppInWindow(String component, int displayId) = 10;

    boolean injectMotionEvent(int displayId, in MotionEvent event) = 8;

    boolean injectKeyEvent(int displayId, in KeyEvent event) = 9;

    boolean injectMouse(int displayId, int action, float x, float y, int buttons, int actionButton,
            long downTime, float vscroll, float hscroll) = 11;

    boolean injectKey(int displayId, int action, int keyCode, int metaState, long downTime, int repeat) = 12;

    boolean injectText(int displayId, String text) = 13;
}
