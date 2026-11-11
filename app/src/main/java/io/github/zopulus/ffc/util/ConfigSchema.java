package io.github.zopulus.ffc.util;

import java.util.List;

/** Persisted names and migration defaults shared by UI, provider and hooked processes. */
public final class ConfigSchema {
    private ConfigSchema() {}
    public static final String KEY_DISABLE_AUTO_CLEAN_NOTIFICATION = "disableAutoCleanNotification";
    public static final String KEY_INCLUDE_ICEBOX_DISABLED_APP = "includeIceBoxDisableApp";
    public static final String KEY_DEEP_SLEEP_GOOGLE_WHITELIST = "deepSleepGoogleWhitelist";
    public static final String KEY_DOZE_GOOGLE_WHITELIST = "dozeGoogleWhitelist";
    public static final String KEY_ROOT_DEEP_SLEEP_NETWORK_WHITELIST = "rootDeepSleepNetworkWhitelist";
    public static final String KEY_AUTO_ALLOW_NEW_APPS = "autoAllowNewApps";
    /** HyperOS PowerKeeper "sleep mode" (turns data/Wi-Fi/Bluetooth off at night). true = leave it on (system default). */
    public static final String KEY_HYPEROS_SLEEP_MODE = "hyperOsSleepMode";
    /** HyperOS: keep mobile data on while PowerKeeper sleep mode is active (rest of sleep mode unchanged). */
    public static final String KEY_SLEEP_MODE_KEEP_DATA = "sleepModeKeepMobileData";
    public static final String KEY_INSTALL_EVENTS = "installEvents";
    /** HyperOS: packages the user chose to keep out of the freezer and the memory killer (costs battery). */
    public static final String KEY_PROTECTED_APPS = "protectedApps";
    public static final List<String> RETIRED_KEYS = List.of("disableGoogleNetworkControl",
            "autoAllowExcluded", "autoAllowOnNotificationGrant", "notificationEnableEvents");
    public static final List<String> OPTIONS = List.of(KEY_DISABLE_AUTO_CLEAN_NOTIFICATION,
            KEY_INCLUDE_ICEBOX_DISABLED_APP, KEY_DEEP_SLEEP_GOOGLE_WHITELIST,
            KEY_DOZE_GOOGLE_WHITELIST, KEY_ROOT_DEEP_SLEEP_NETWORK_WHITELIST,
            KEY_AUTO_ALLOW_NEW_APPS, KEY_HYPEROS_SLEEP_MODE, KEY_SLEEP_MODE_KEEP_DATA);
    public static boolean defaultValue(String key) {
        return KEY_DEEP_SLEEP_GOOGLE_WHITELIST.equals(key) || KEY_DOZE_GOOGLE_WHITELIST.equals(key)
                || KEY_AUTO_ALLOW_NEW_APPS.equals(key) || KEY_HYPEROS_SLEEP_MODE.equals(key);
    }
}
