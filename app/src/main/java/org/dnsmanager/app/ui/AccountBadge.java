package org.dnsmanager.app.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import org.dnsmanager.app.theme.Scheme;

/**
 * The round badge that tells accounts apart: a filled circle with the account's number (its place
 * in the account list: 1, 2, 3…). All accounts belong to the same service, so a logo would look the
 * same for each; the number doesn't. Used in the top bar's chip and in the account menu; the
 * Settings page draws the same badge in HTML ({@code .badge} in settings.html).
 */
public final class AccountBadge {

    private AccountBadge() {
        // Only static methods.
    }

    /**
     * Turns a TextView into the badge.
     *
     * @param number the account's number (account/AccountStore.numberOf)
     * @param sizePx the badge's width and height in pixels
     */
    public static void apply(TextView view, int number, Scheme scheme, int sizePx) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(scheme.get(Scheme.PRIMARY));
        view.setBackground(circle);
        view.setText(label(number));
        view.setTextColor(scheme.get(Scheme.ON_PRIMARY));
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        // Two digits need a slightly smaller text to fit the circle.
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * (number > 9 ? 0.42f : 0.5f));
    }

    /** @return the text on the badge: the number, or "?" for an account that isn't in the list. */
    public static String label(int number) {
        return number > 0 ? String.valueOf(number) : "?";
    }

    /** @return {@code dp} density-independent pixels in real pixels on this screen. */
    public static int dp(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
