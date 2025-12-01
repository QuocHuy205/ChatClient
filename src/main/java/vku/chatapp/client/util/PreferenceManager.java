package vku.chatapp.client.util;

import java.util.prefs.Preferences;

public class PreferenceManager {
    private static PreferenceManager instance;
    private final Preferences prefs;

    private PreferenceManager() {
        prefs = Preferences.userNodeForPackage(PreferenceManager.class);
    }

    public static PreferenceManager getInstance() {
        if (instance == null) {
            synchronized (PreferenceManager.class) {
                if (instance == null) {
                    instance = new PreferenceManager();
                }
            }
        }
        return instance;
    }

    public void savePreference(String key, String value) {
        prefs.put(key, value);
    }

    public String getPreference(String key, String defaultValue) {
        return prefs.get(key, defaultValue);
    }

    public void savePreference(String key, boolean value) {
        prefs.putBoolean(key, value);
    }

    public boolean getPreference(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public void removePreference(String key) {
        prefs.remove(key);
    }

    public void clearAll() {
        try {
            prefs.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}