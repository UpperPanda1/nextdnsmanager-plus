package org.dnsmanager.app;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A stand-in for Android's settings file in JVM tests (Android's own classes are empty stubs on the
 * computer). Keeps values in a map; enough for the code under test.
 */
public final class MapPrefs implements SharedPreferences {

    public final Map<String, Object> values = new HashMap<>();

    @Override
    public Map<String, ?> getAll() {
        return values;
    }

    @Override
    public String getString(String key, String defValue) {
        Object v = values.get(key);
        return v instanceof String ? (String) v : defValue;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        Object v = values.get(key);
        return v instanceof Set ? (Set<String>) v : defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        Object v = values.get(key);
        return v instanceof Integer ? (Integer) v : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        Object v = values.get(key);
        return v instanceof Long ? (Long) v : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object v = values.get(key);
        return v instanceof Float ? (Float) v : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Object v = values.get(key);
        return v instanceof Boolean ? (Boolean) v : defValue;
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new Editor() {
            private final Map<String, Object> pending = new HashMap<>();
            private boolean clear;

            @Override public Editor putString(String key, String value) { pending.put(key, value); return this; }
            @Override public Editor putStringSet(String key, Set<String> v) { pending.put(key, v); return this; }
            @Override public Editor putInt(String key, int value) { pending.put(key, value); return this; }
            @Override public Editor putLong(String key, long value) { pending.put(key, value); return this; }
            @Override public Editor putFloat(String key, float value) { pending.put(key, value); return this; }
            @Override public Editor putBoolean(String key, boolean value) { pending.put(key, value); return this; }
            @Override public Editor remove(String key) { pending.put(key, null); return this; }
            @Override public Editor clear() { clear = true; return this; }

            @Override
            public boolean commit() {
                apply();
                return true;
            }

            @Override
            public void apply() {
                if (clear) {
                    values.clear();
                }
                for (Map.Entry<String, Object> e : pending.entrySet()) {
                    if (e.getValue() == null) {
                        values.remove(e.getKey());
                    } else {
                        values.put(e.getKey(), e.getValue());
                    }
                }
            }
        };
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    }
}
