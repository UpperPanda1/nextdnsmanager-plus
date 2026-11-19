package org.dnsmanager.app.ui;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;

import org.dnsmanager.app.lock.AppLock;

/**
 * The app's extra screens (Settings, About, Ping test) are {@link Dialog}s that fill the whole
 * screen: lighter than extra activities, and they sit on top of the page, which keeps running.
 * This is the one place that makes a dialog full-screen, so all of them behave the same.
 */
public final class Windows {

    private Windows() {
        // Only static methods.
    }

    /** Call before {@code setContentView}: removes the dialog's title strip. */
    public static void prepare(Dialog dialog) {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    }

    /**
     * The app lock's "secure" mark for ONE window: Android leaves a secure window out of screenshots,
     * screen recordings and the recent-apps card. Every <b>dialog</b> needs it separately: Settings, About,
     * the Ping test and the status dialog are windows of their own, so the main screen's flag doesn't cover
     * them. Pop-up menus (account menu, ⋮ menu) don't need it: they are child windows of the main screen and
     * inherit its mark (SurfaceFlinger: a layer is secure if it or its parent is).
     *
     * <p><b>Calling it right after {@code dialog.show()} is early enough</b> (checked in AOSP, 2026-11-19):
     * {@code show()} only schedules the window's first layout and drawing for the next screen frame, and its
     * surface is created then, with the flags set by now. Call it in the same piece of main-thread work as
     * {@code show()} (not posted later).</p>
     */
    public static void setSecure(Dialog dialog, boolean secure) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    /** Call after {@code show()}: stretches the dialog over the screen and removes its own frame. */
    public static void fill(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        setSecure(dialog, AppLock.isEnabled(dialog.getContext())); // same rule as the main screen
    }
}
