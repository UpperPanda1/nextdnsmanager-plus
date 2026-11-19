package org.dnsmanager.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.dnsmanager.app.R;
import org.dnsmanager.app.theme.Scheme;

/**
 * The cover shown over the whole screen while the app is locked (lock/AppLock): a plain surface with
 * a padlock, "Locked" and an Unlock button that brings Android's unlock sheet back after the user
 * closed it. It hides the page underneath and swallows every tap.
 *
 * <p>Built from plain views in code, like the account menu. MainActivity adds it as the last child of
 * {@code R.id.screen}, so it lies on top of the top bar and the pages.</p>
 */
public final class LockScreen {

    private final FrameLayout root;
    private final ImageView icon;
    private final TextView title;
    private final Button button;
    // The screen content under the cover (top bar + pages); null until covers() is called.
    private ViewGroup covered;
    private int coveredFocusability;

    public LockScreen(Context context, Runnable onUnlockTapped) {
        root = new FrameLayout(context);
        root.setVisibility(View.GONE);
        root.setClickable(true);  // swallows taps, so nothing underneath can be touched
        root.setFocusable(true);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);

        icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_lock);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int size = AccountBadge.dp(context, 56);
        column.addView(icon, new LinearLayout.LayoutParams(size, size));

        title = new TextView(context);
        title.setText(R.string.lock_locked);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = AccountBadge.dp(context, 16);
        column.addView(title, titleParams);

        button = new Button(context);
        button.setText(R.string.lock_button);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setMinHeight(AccountBadge.dp(context, 48));
        button.setMinimumHeight(AccountBadge.dp(context, 48));
        button.setPadding(AccountBadge.dp(context, 24), 0, AccountBadge.dp(context, 24), 0);
        button.setStateListAnimator(null); // flat, no shadow
        button.setOnClickListener(v -> onUnlockTapped.run());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = AccountBadge.dp(context, 24);
        column.addView(button, buttonParams);

        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    /** @return the view to add to the screen. */
    public View view() {
        return root;
    }

    /**
     * Tells the cover which views it hides (MainActivity: R.id.mainRoot = top bar + pages).
     *
     * <p><b>Why this is needed:</b> drawing the cover on top only hides the content from eyes and fingers. Android
     * does not hide covered views from <i>accessibility services</i> (TalkBack swipes through every view on screen,
     * covered or not, and can press them) nor from <i>keyboard focus</i> (Tab/arrow keys move to any focusable view).
     * Without this, the top bar's ⋮ → Settings (sign out, remove accounts) and the page itself were reachable
     * behind the lock. While locked, the content is removed from both (see {@link #setVisible}).</p>
     */
    public void covers(ViewGroup content) {
        covered = content;
        coveredFocusability = content.getDescendantFocusability();
    }

    public void restyle(Scheme scheme) {
        root.setBackgroundColor(scheme.get(Scheme.SURFACE));
        icon.setImageTintList(ColorStateList.valueOf(scheme.get(Scheme.ON_SURFACE_VARIANT)));
        title.setTextColor(scheme.get(Scheme.ON_SURFACE));
        button.setBackground(NativeTheme.pill(scheme.get(Scheme.PRIMARY), AccountBadge.dp(root.getContext(), 24)));
        button.setTextColor(scheme.get(Scheme.ON_PRIMARY));
    }

    public void setVisible(boolean visible) {
        if (covered != null) {
            // Hidden from accessibility services, together with everything inside it.
            covered.setImportantForAccessibility(visible ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            // No keyboard focus can move in. Blocking doesn't take focus away from a view that already has it
            // (AOSP ViewGroup.setDescendantFocusability only sets a flag), so that focus is cleared too: else a
            // page that had the keyboard would still get typed keys behind the cover.
            covered.setDescendantFocusability(visible ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : coveredFocusability);
            View focused = visible ? covered.findFocus() : null;
            if (focused != null) {
                focused.clearFocus();
            }
        }
        root.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            button.requestFocus(); // for keyboard users (does nothing in touch mode, which is fine)
        }
    }

    public boolean isVisible() {
        return root.getVisibility() == View.VISIBLE;
    }
}
