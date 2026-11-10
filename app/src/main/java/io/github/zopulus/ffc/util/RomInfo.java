package io.github.zopulus.ffc.util;

import android.os.Build;

/** Which vendor ROM the module app runs on (app process; system_server detects by class presence). */
public final class RomInfo {
    private RomInfo() {}

    public static boolean isHyperOs() {
        String miOs = "";
        try {
            Object value = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, "ro.mi.os.version.name");
            if (value instanceof String) miOs = (String) value;
        } catch (Throwable ignored) { }
        return !miOs.trim().isEmpty() || "Xiaomi".equalsIgnoreCase(Build.MANUFACTURER);
    }
}
