package org.dnsmanager.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.dnsmanager.app.R;
import org.dnsmanager.app.theme.Scheme;

/**
 * Colours the native (non-HTML) parts of the screen with the current scheme: top bar, loading bar,
 * toggle buttons. The XML layouts only carry start-up colours; this is what the user sees.
 */
public final class NativeTheme {

    private NativeTheme() {
        // Only static methods.
    }

    /** Top bar: background = the page's background, chip slightly raised, icons in the quiet grey. */
    public static void styleTopBar(View topBar, Scheme scheme) {
        if (topBar == null) {
            return;
        }
        topBar.setBackgroundColor(scheme.get(Scheme.SURFACE));
        View chip = topBar.findViewById(R.id.accountChip);
        if (chip != null) {
            chip.setBackground(pill(scheme.get(Scheme.SURFACE_CONTAINER_HIGH), AccountBadge.dp(chip.getContext(), 20)));
        }
        TextView name = topBar.findViewById(R.id.accountName);
        if (name != null) {
            name.setTextColor(scheme.get(Scheme.ON_SURFACE));
        }
        TextView arrow = topBar.findViewById(R.id.accountArrow);
        if (arrow != null) {
            arrow.setTextColor(scheme.get(Scheme.ON_SURFACE_VARIANT));
        }
        // The status button is not listed: status/ConnectionStatus tints it with the status colour.
        for (int id : new int[]{R.id.homeButton, R.id.menuButton}) {
            ImageView button = topBar.findViewById(id);
            if (button != null) {
                button.setImageTintList(ColorStateList.valueOf(scheme.get(Scheme.ON_SURFACE_VARIANT)));
            }
        }
    }

    /** The account menu's Desktop site button: outlined monitor = off, filled monitor in a circle = on. */
    public static void styleDesktopButton(ImageView button, boolean on, Scheme scheme) {
        styleToggleButton(button, on, scheme, R.drawable.ic_desktop, R.drawable.ic_desktop_on, R.string.desktop_site);
    }

    /**
     * A round icon button that is on or off: on = filled circle behind the icon. Screen readers hear
     * "label, On/Off".
     */
    public static void styleToggleButton(ImageView button, boolean on, Scheme scheme, int offIcon, int onIcon,
                                         int labelRes) {
        Context context = button.getContext();
        button.setSelected(on);
        button.setImageResource(on ? onIcon : offIcon);
        button.setImageTintList(ColorStateList.valueOf(
                scheme.get(on ? Scheme.ON_SECONDARY_CONTAINER : Scheme.ON_SURFACE_VARIANT)));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(scheme.get(Scheme.SECONDARY_CONTAINER));
        int inset = AccountBadge.dp(context, 4);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(0xFFFFFFFF);
        int ripple = (scheme.get(Scheme.ON_SURFACE) & 0x00FFFFFF) | 0x1F000000; // onSurface at 12%
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple),
                on ? new InsetDrawable(circle, inset) : null, new InsetDrawable(mask, inset)));
        String label = context.getString(labelRes);
        String state = context.getString(on ? R.string.state_on : R.string.state_off);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            button.setContentDescription(label);
            button.setStateDescription(state);
        } else {
            button.setContentDescription(label + ", " + state);
        }
    }

    public static void styleProgressBar(ProgressBar bar, Scheme scheme) {
        if (bar != null) {
            ColorStateList accent = ColorStateList.valueOf(scheme.get(Scheme.PRIMARY));
            bar.setIndeterminateTintList(accent);
            bar.setProgressTintList(accent);
        }
    }

    /** @return a filled shape with rounded corners. */
    public static GradientDrawable pill(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }
}
