package org.dnsmanager.app.ui;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;

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

    /** Call after {@code show()}: stretches the dialog over the screen and removes its own frame. */
    public static void fill(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
