package org.dnsmanager.app.lock;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

import org.dnsmanager.app.Prefs;

/**
 * App lock (Settings → Security): opening the app needs the phone's own unlock, fingerprint, face,
 * PIN, pattern or password. The rules for <i>when</i> are in {@link LockRules}; the cover shown
 * meanwhile is ui/LockScreen; MainActivity ties them together.
 *
 * <p><b>How:</b> Android's system unlock sheet ({@link BiometricPrompt}). The app never sees the
 * fingerprint or PIN; Android only reports "confirmed" or "not". "Device credential" (PIN, pattern,
 * password) is always allowed as well, so the lock also works on phones without a fingerprint
 * sensor and nobody is locked out by a wet finger. No library is used: the framework class exists
 * on every supported Android version (10+).
 * Docs: https://developer.android.com/identity/sign-in/biometric-auth</p>
 */
public final class AppLock {

    /** Preference: is the app lock on? */
    public static final String PREF = "app_lock";

    /** Reports how an unlock attempt ended. Called on the main thread. */
    public interface Callback {
        void onUnlocked();

        /** The user closed the sheet or it failed; the lock stays. */
        void onCancelled();
    }

    private AppLock() {
        // Only static methods.
    }

    public static boolean isEnabled(Context context) {
        return Prefs.get(context).getBoolean(PREF, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        Prefs.get(context).edit().putBoolean(PREF, enabled).apply();
    }

    /**
     * @return true if the phone has a screen lock to ask for (a PIN, pattern, password or an enrolled
     *         fingerprint/face). Without one the app lock can't be switched on.
     */
    public static boolean isAvailable(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager manager = context.getSystemService(BiometricManager.class);
            return manager != null && manager.canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS;
        }
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        return keyguard != null && keyguard.isDeviceSecure();
    }

    /**
     * Shows Android's unlock sheet.
     *
     * @return a handle to close the sheet from code (e.g. when the screen is destroyed)
     */
    @SuppressWarnings("deprecation") // setDeviceCredentialAllowed: the only way on Android 10
    public static CancellationSignal prompt(Activity activity, String title, String subtitle, Callback callback) {
        BiometricPrompt.Builder builder = new BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(authenticators());
        } else {
            builder.setDeviceCredentialAllowed(true);
        }
        CancellationSignal cancel = new CancellationSignal();
        builder.build().authenticate(cancel, activity.getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        callback.onUnlocked();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence message) {
                        // Cancelled, too many attempts, no hardware… the sheet is gone either way.
                        callback.onCancelled();
                    }
                    // onAuthenticationFailed (a finger that didn't match) keeps the sheet open: nothing to do.
                });
        return cancel;
    }

    /** Fingerprint or face ("weak" includes "strong") or the screen lock's PIN/pattern/password. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private static int authenticators() {
        return BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    }
}
