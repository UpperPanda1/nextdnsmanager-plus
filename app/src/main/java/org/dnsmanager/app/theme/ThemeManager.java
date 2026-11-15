package org.dnsmanager.app.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import org.dnsmanager.app.Prefs;

import java.util.Arrays;
import java.util.List;

/**
 * Light or dark: which one is active, and applying it to the window (PROJECT.md §5.6).
 *
 * <p><b>The setting</b> (Settings → Appearance → Theme, preference {@value #PREF_MODE}): "system"
 * follows the phone, "light" and "dark" force one.</p>
 *
 * <p><b>How the website learns about it.</b> The NextDNS dashboard has its own light and dark design
 * and picks it with the web standard {@code prefers-color-scheme}. WebView answers that question from
 * the <i>app's</i> theme (dark theme → "dark"; Android 13+ behaviour for apps targeting it). So
 * forcing light or dark is done the way Android's own AppCompat library does it: the activity's
 * configuration is overridden with "night yes/no" ({@link #nightOverride}, used in
 * {@code MainActivity.attachBaseContext}), and the manifest theme {@code Theme.DeviceDefault.DayNight}
 * follows that. Nothing is injected into the page. (If the user picked a theme inside the dashboard's
 * own account settings, the website follows that choice instead; that is the site's decision.)</p>
 */
public final class ThemeManager {

    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final List<String> MODES = Arrays.asList(MODE_SYSTEM, MODE_LIGHT, MODE_DARK);

    static final String PREF_MODE = "theme_mode";
    static final String PREF_HIDE_STATUS_BAR = "hide_status_bar";

    private ThemeManager() {
        // Only static methods.
    }

    /** @return the saved theme setting; anything unknown counts as "system". */
    public static String mode(Context context) {
        String mode = Prefs.get(context).getString(PREF_MODE, MODE_SYSTEM);
        return MODES.contains(mode) ? mode : MODE_SYSTEM;
    }

    public static void setMode(Context context, String mode) {
        if (MODES.contains(mode)) {
            Prefs.get(context).edit().putString(PREF_MODE, mode).apply();
        }
    }

    /** @return true if the app should be dark right now. */
    public static boolean isNight(Context context) {
        switch (mode(context)) {
            case MODE_LIGHT:
                return false;
            case MODE_DARK:
                return true;
            default:
                // The PHONE's setting (Resources.getSystem), not this activity's, which may be overridden.
                int night = Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                return night == Configuration.UI_MODE_NIGHT_YES;
        }
    }

    /** @return the colours to use right now (theme/Themes). */
    public static Scheme scheme(Context context) {
        return isNight(context) ? Themes.DARK : Themes.LIGHT;
    }

    /**
     * @return the configuration change that forces light or dark for one activity, or null for
     *         "follow the phone". Only the night bits are set (and {@code fontScale = 0} = "not set"),
     *         so everything else stays the phone's, as AppCompat does it.
     */
    public static Configuration nightOverride(Context base) {
        if (MODE_SYSTEM.equals(mode(base))) {
            return null;
        }
        Configuration config = new Configuration();
        config.fontScale = 0;
        config.uiMode = isNight(base) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        return config;
    }

    // ------------------------------------------------------------ status bar

    /** Account menu → "Hide status bar": more room for the page; swipe down to peek at the bar. */
    public static boolean isStatusBarHidden(Context context) {
        return Prefs.get(context).getBoolean(PREF_HIDE_STATUS_BAR, false);
    }

    public static void setStatusBarHidden(Context context, boolean hidden) {
        Prefs.get(context).edit().putBoolean(PREF_HIDE_STATUS_BAR, hidden).apply();
    }

    /** Shows or hides the status bar as the setting says. */
    @SuppressWarnings("deprecation") // FLAG_FULLSCREEN: still the way on Android 10
    public static void applyStatusBar(Activity activity) {
        boolean hide = isStatusBarHidden(activity);
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                // Android 11–14: let the page use the camera cut-out strip while the bar is hidden
                // (Android 15+ always does).
                WindowManager.LayoutParams attrs = window.getAttributes();
                int mode = hide ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
                if (attrs.layoutInDisplayCutoutMode != mode) {
                    attrs.layoutInDisplayCutoutMode = mode;
                    window.setAttributes(attrs);
                }
            }
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                if (hide) {
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    controller.hide(WindowInsets.Type.statusBars());
                } else {
                    controller.show(WindowInsets.Type.statusBars());
                }
            }
        } else if (hide) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    /**
     * Android 11+: pads the screen's root view by hand so it stays clear of the system bars, the
     * keyboard and the camera cut-out (the rule is in {@link ContentInsets}). This replaces
     * {@code fitsSystemWindows}, which can't give the top strip to the page while the status bar is
     * hidden. On Android 10 the layout's {@code fitsSystemWindows="true"} does the job.
     */
    public static void fitContent(View root) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            android.graphics.Insets cutout = insets.getInsets(WindowInsets.Type.displayCutout());
            int[] padding = ContentInsets.padding(insets.isVisible(WindowInsets.Type.statusBars()),
                    new int[]{bars.left, bars.top, bars.right, bars.bottom},
                    new int[]{cutout.left, cutout.top, cutout.right, cutout.bottom});
            view.setPadding(padding[0], padding[1], padding[2], padding[3]);
            return WindowInsets.CONSUMED;
        });
        root.requestApplyInsets();
    }

    /**
     * Colours the window: background, status bar and navigation bar get the scheme's {@code surface}
     * (white, or the website's dark #212529), with dark icons on a light bar and light icons on a
     * dark one. So Android's status bar always matches the top bar and the page.
     *
     * <p>Android 15+ draws apps edge to edge: the bars are see-through and show the window
     * background, which is why the background is set too.</p>
     */
    @SuppressWarnings("deprecation") // status/nav bar colours: still needed below Android 15
    public static void styleWindow(Activity activity, Scheme scheme) {
        Window window = activity.getWindow();
        int background = scheme.get(Scheme.SURFACE);
        window.setBackgroundDrawable(new ColorDrawable(background));
        boolean lightBars = !scheme.dark;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(background);
            window.setNavigationBarColor(background);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightBars ? mask : 0, mask);
            }
        } else {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(lightBars ? (flags | lightFlags) : (flags & ~lightFlags));
        }
    }
}
