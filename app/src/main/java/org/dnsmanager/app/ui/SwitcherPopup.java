package org.dnsmanager.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import org.dnsmanager.app.R;
import org.dnsmanager.app.account.Account;
import org.dnsmanager.app.theme.Scheme;

import java.util.List;

/**
 * The account menu that drops down from the top bar's chip (PROJECT.md §5.1). From top to bottom:
 * <ol>
 *   <li>one row per account: numbered badge, name, and a ✕ on every account whose page is loaded
 *       (tap = unload it to free memory; the menu stays open);</li>
 *   <li>the icon row: Desktop site (this account), Reload page, Hide status bar, Clear cache (all
 *       accounts; logins stay);</li>
 *   <li>"Manage accounts": opens Settings at the account list.</li>
 * </ol>
 * Built from plain Android views in code (no layout file, no UI library), coloured from the scheme.
 * It only draws and reports taps: what a tap does is decided by the {@link Host} (MainActivity).
 */
public final class SwitcherPopup {

    /** What the menu needs from the screen. */
    public interface Host {
        void pick(Account account);

        boolean isLoaded(String accountId);

        void unload(String accountId);

        boolean isDesktopOn();

        void toggleDesktop();

        void reload();

        boolean isStatusBarHidden();

        void toggleStatusBar();

        void clearCache();

        void manageAccounts();
    }

    private SwitcherPopup() {
        // Only static methods.
    }

    /**
     * @param anchor   the chip; the menu opens under it
     * @param accounts every account, in list order (row n gets the number n)
     * @param current  the account on screen (its row is highlighted)
     * @return the open menu (MainActivity closes it when the app locks)
     */
    public static PopupWindow show(View anchor, List<Account> accounts, Account current, Scheme scheme, Host host) {
        Context context = anchor.getContext();
        int pad = AccountBadge.dp(context, 8);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable();
        background.setColor(scheme.get(Scheme.SURFACE_CONTAINER));
        background.setCornerRadius(AccountBadge.dp(context, 20));
        list.setBackground(background);

        PopupWindow popup = new PopupWindow(list, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(AccountBadge.dp(context, 6)); // soft shadow
        popup.setOutsideTouchable(true);

        // The account rows scroll inside at most half the screen, so the icon row is always reachable.
        int maxListHeight = context.getResources().getDisplayMetrics().heightPixels / 2;
        ScrollView scroller = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int limit = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED ? maxListHeight
                        : Math.min(maxListHeight, MeasureSpec.getSize(heightMeasureSpec));
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
            }
        };
        LinearLayout accountsBox = new LinearLayout(context);
        accountsBox.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(accountsBox);
        list.addView(scroller);

        // Filled now and again after a ✕ (a Runnable in a one-element array so it can call itself).
        Runnable[] fillAccounts = new Runnable[1];
        fillAccounts[0] = () -> {
            accountsBox.removeAllViews();
            int number = 1;
            for (Account account : accounts) {
                accountsBox.addView(createAccountRow(context, scheme, account, number++,
                        account.id.equals(current.id), host.isLoaded(account.id), v -> {
                            popup.dismiss();
                            host.pick(account);
                        }, () -> {
                            host.unload(account.id);
                            fillAccounts[0].run();
                        }));
            }
        };
        fillAccounts[0].run();

        list.addView(divider(context, scheme, pad));

        LinearLayout icons = new LinearLayout(context);
        icons.setOrientation(LinearLayout.HORIZONTAL);
        ImageButton desktop = iconButton(context, scheme, R.drawable.ic_desktop, R.string.desktop_site,
                popup, host::toggleDesktop);
        NativeTheme.styleDesktopButton(desktop, host.isDesktopOn(), scheme);
        addEvenly(icons, desktop);
        addEvenly(icons, iconButton(context, scheme, R.drawable.ic_refresh, R.string.reload_page,
                popup, host::reload));
        ImageButton statusBar = iconButton(context, scheme, R.drawable.ic_fullscreen, R.string.hide_status_bar,
                popup, host::toggleStatusBar);
        NativeTheme.styleToggleButton(statusBar, host.isStatusBarHidden(), scheme,
                R.drawable.ic_fullscreen, R.drawable.ic_fullscreen, R.string.hide_status_bar);
        addEvenly(icons, statusBar);
        addEvenly(icons, iconButton(context, scheme, R.drawable.ic_delete_sweep, R.string.clear_cache,
                popup, host::clearCache));
        list.addView(icons);

        list.addView(divider(context, scheme, pad));

        LinearLayout manage = baseRow(context, v -> {
            popup.dismiss();
            host.manageAccounts();
        });
        ImageView manageIcon = new ImageView(context);
        manageIcon.setImageResource(R.drawable.ic_manage_accounts);
        manageIcon.setImageTintList(ColorStateList.valueOf(scheme.get(Scheme.ON_SURFACE_VARIANT)));
        manageIcon.setScaleType(ImageView.ScaleType.CENTER);
        manageIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int iconSize = AccountBadge.dp(context, 32);
        manage.addView(manageIcon, new LinearLayout.LayoutParams(iconSize, iconSize));
        TextView manageText = text(context, context.getString(R.string.manage_accounts), 16,
                scheme.get(Scheme.ON_SURFACE));
        LinearLayout.LayoutParams manageParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        manageParams.setMarginStart(AccountBadge.dp(context, 14));
        manage.addView(manageText, manageParams);
        list.addView(manage);

        // 4dp gap: the chip is 40dp tall in the 48dp bar, so the menu's edge lines up with the bar's.
        popup.showAsDropDown(anchor, 0, AccountBadge.dp(context, 4), Gravity.START);
        return popup;
    }

    private static View createAccountRow(Context context, Scheme scheme, Account account, int number,
                                         boolean isCurrent, boolean loaded, View.OnClickListener onClick,
                                         Runnable onUnload) {
        LinearLayout row = baseRow(context, onClick);
        if (isCurrent) {
            GradientDrawable selected = new GradientDrawable();
            selected.setColor(scheme.get(Scheme.SECONDARY_CONTAINER));
            selected.setCornerRadius(AccountBadge.dp(context, 14));
            row.setBackground(selected);
            row.setSelected(true);
        }

        TextView badge = new TextView(context);
        int badgeSize = AccountBadge.dp(context, 32);
        row.addView(badge, new LinearLayout.LayoutParams(badgeSize, badgeSize));
        AccountBadge.apply(badge, number, scheme, badgeSize);
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        TextView name = text(context, account.name, 16,
                scheme.get(isCurrent ? Scheme.ON_SECONDARY_CONTAINER : Scheme.ON_SURFACE));
        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(AccountBadge.dp(context, 14));
        row.addView(name, textParams);

        // What a screen reader says for the row: "Account 2, Work, loaded, current".
        String description = context.getString(R.string.account_number, number) + ", " + account.name;
        if (loaded) {
            description = description + ", " + context.getString(R.string.account_loaded);
            ImageButton close = new ImageButton(context);
            close.setImageResource(R.drawable.ic_close);
            close.setImageTintList(ColorStateList.valueOf(scheme.get(isCurrent
                    ? Scheme.ON_SECONDARY_CONTAINER : Scheme.ON_SURFACE_VARIANT)));
            close.setScaleType(ImageView.ScaleType.CENTER);
            close.setBackgroundResource(rippleResource(context, android.R.attr.selectableItemBackgroundBorderless));
            String label = context.getString(R.string.unload_account, account.name);
            close.setContentDescription(label);
            close.setTooltipText(label);
            close.setOnClickListener(v -> onUnload.run());
            int size = AccountBadge.dp(context, 48);
            LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(size, size);
            closeParams.setMarginStart(AccountBadge.dp(context, 4));
            closeParams.setMarginEnd(-AccountBadge.dp(context, 12)); // sits in the row's end padding
            row.addView(close, closeParams);
        }
        row.setContentDescription(isCurrent
                ? description + ", " + context.getString(R.string.current_account) : description);
        return row;
    }

    /** A 48dp round icon button of the icon row; a tap closes the menu, then runs the action. */
    private static ImageButton iconButton(Context context, Scheme scheme, int iconRes, int labelRes,
                                          PopupWindow popup, Runnable action) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(iconRes);
        button.setImageTintList(ColorStateList.valueOf(scheme.get(Scheme.ON_SURFACE_VARIANT)));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setBackgroundResource(rippleResource(context, android.R.attr.selectableItemBackgroundBorderless));
        String label = context.getString(labelRes);
        button.setContentDescription(label);
        button.setTooltipText(label); // long-press shows the name
        button.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        return button;
    }

    /** Adds a button in its own equal-width cell, so the icons are spread evenly across the menu. */
    private static void addEvenly(LinearLayout row, View button) {
        FrameLayout cell = new FrameLayout(row.getContext());
        int size = AccountBadge.dp(row.getContext(), 48);
        cell.addView(button, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        row.addView(cell, new LinearLayout.LayoutParams(0, size, 1f));
    }

    private static View divider(Context context, Scheme scheme, int margin) {
        View divider = new View(context);
        divider.setBackgroundColor(scheme.get(Scheme.OUTLINE_VARIANT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, AccountBadge.dp(context, 1)));
        params.setMargins(margin, margin / 2, margin, margin / 2);
        divider.setLayoutParams(params);
        return divider;
    }

    /** A 56dp-tall tappable row with touch feedback. */
    private static LinearLayout baseRow(Context context, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(AccountBadge.dp(context, 56));
        row.setMinimumWidth(AccountBadge.dp(context, 240));
        row.setPadding(AccountBadge.dp(context, 12), 0, AccountBadge.dp(context, 16), 0);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(onClick);
        row.setForeground(context.getDrawable(rippleResource(context, android.R.attr.selectableItemBackground)));
        return row;
    }

    /** @return the resource id of the theme's standard touch-feedback drawable. */
    private static int rippleResource(Context context, int attr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.resourceId;
    }

    private static TextView text(Context context, String value, int sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }
}
