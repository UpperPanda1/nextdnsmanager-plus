package org.dnsmanager.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.dnsmanager.app.R;
import org.dnsmanager.app.site.Site;
import org.dnsmanager.app.status.StatusRules;

/**
 * The status dialog's explanation of the top bar icon: one row per state, with the <b>same icon in the same colour</b>
 * as in the top bar, then a short text. Replaces the old sentence "Green = …, Orange = …", which named colours
 * instead of showing them.
 *
 * <p>Built in code like the rows of {@link SwitcherPopup} (icon, 14 dp gap, text). The dialog itself is Android's
 * {@code AlertDialog}, so the text copies the colour and size of the dialog's own message
 * ({@link #matchMessage}) and the sides use the dialog's own padding ({@code ?android:dialogPreferredPadding}).
 * The icons are decoration for TalkBack: the text says everything.</p>
 */
public final class StatusLegend {

    private StatusLegend() {
        // Only static methods.
    }

    /**
     * @param context the dialog's context ({@code AlertDialog.Builder.getContext()}), so the dialog's theme applies
     * @param dark    the app's theme: the icon colours are the top bar's ({@link StatusRules#color})
     * @return the rows (put them into the dialog with {@link #scrollable})
     */
    public static LinearLayout create(Context context, boolean dark) {
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int side = dialogPadding(context);
        list.setPadding(side, AccountBadge.dp(context, 4), side, AccountBadge.dp(context, 8));
        for (StatusRules.State state : StatusRules.LEGEND) {
            list.addView(row(context, state, dark));
        }
        return list;
    }

    /**
     * @return the rows inside a scroll view, for {@code AlertDialog.Builder.setView}. Android's dialog scrolls only
     *         its own message, so without this the last rows could be cut off on a small screen with a big font.
     */
    public static View scrollable(LinearLayout legend) {
        ScrollView scroll = new ScrollView(legend.getContext());
        scroll.addView(legend);
        return scroll;
    }

    /** Gives the rows' text the colour and size of the dialog's message. Call after {@code show()}. */
    public static void matchMessage(LinearLayout legend, TextView message) {
        if (legend == null || message == null) {
            return; // the rows keep the theme's default text style
        }
        for (int i = 0; i < legend.getChildCount(); i++) {
            View text = ((ViewGroup) legend.getChildAt(i)).getChildAt(1);
            if (text instanceof TextView) {
                ((TextView) text).setTextColor(message.getTextColors());
                ((TextView) text).setTextSize(TypedValue.COMPLEX_UNIT_PX, message.getTextSize());
            }
        }
    }

    private static View row(Context context, StatusRules.State state, boolean dark) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int gap = AccountBadge.dp(context, 6);
        row.setPadding(0, gap, 0, gap);

        ImageView icon = new ImageView(context);
        icon.setImageResource(StatusRules.usesTickIcon(state) ? R.drawable.ic_status_ok : R.drawable.ic_status_warn);
        icon.setImageTintList(ColorStateList.valueOf(StatusRules.color(state, dark)));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int size = AccountBadge.dp(context, 24);
        row.addView(icon, new LinearLayout.LayoutParams(size, size));

        TextView text = new TextView(context);
        text.setText(label(context, state));
        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(AccountBadge.dp(context, 14));
        row.addView(text, textParams);
        return row;
    }

    private static String label(Context context, StatusRules.State state) {
        switch (state) {
            case SECURE:
                return context.getString(R.string.status_legend_secure, Site.NAME);
            case INSECURE:
                return context.getString(R.string.status_legend_insecure, Site.NAME);
            case OTHER_DNS:
                return context.getString(R.string.status_legend_other_dns);
            default:
                return context.getString(R.string.status_legend_not_protected);
        }
    }

    /** The side padding Android's dialogs give their message (24 dp in Material; 24 dp if the theme has none). */
    private static int dialogPadding(Context context) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.dialogPreferredPadding, value, true)) {
            return TypedValue.complexToDimensionPixelSize(value.data, context.getResources().getDisplayMetrics());
        }
        return AccountBadge.dp(context, 24);
    }
}
