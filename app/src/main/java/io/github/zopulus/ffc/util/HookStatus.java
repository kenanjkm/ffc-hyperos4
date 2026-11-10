package io.github.zopulus.ffc.util;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Hook install results collected in system_server and handed to the module app once.
 * Nothing here schedules work: {@link #publish} is called from the existing boot/unlock
 * initialisation, and the app only reads the file the provider wrote.
 */
public final class HookStatus {
    private HookStatus() {}

    public static final String METHOD = "hookStatus";
    public static final String EXTRA_JSON = "json";
    public static final String FILE = "hook-status.json";

    private static final class Entry {
        final String name; final boolean ok; final String detail;
        Entry(String name, boolean ok, String detail) { this.name = name; this.ok = ok; this.detail = detail; }
    }

    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private static final java.util.Set<String> COLOR_OS_GROUPS = java.util.Set.of(
            "AutoStartFix", "setGmsRestricted", "isGoogleRestricInfoOn", "isAppClassifyRestricted",
            "isAllowStartFromBindService", "isAllowStartFromStartService", "isSysRestrictionCpn",
            "OAppNetControlService", "CpnProxy broadcast", "isGmsRestricted", "weak-signal net whitelist",
            "validStartProcessFromBroadcast", "malicious broadcast check", "malicious service check",
            "link-start broadcast check", "night network whitelist", "Google network firewall",
            "Google restrict broadcast", "deep-sleep GMS force-stop");

    /**
     * A failed group that only exists on the other vendor's ROM: ColorOS (Oplus/Hans) groups on
     * HyperOS and HyperOS groups on ColorOS. Those fail with "class not found" by design and are
     * not errors. A group that installed is never foreign.
     */
    public static boolean isForeign(String name, boolean ok, boolean hyperOs) {
        if (ok || name == null) return false;
        boolean colorOsOnly = name.startsWith("Oplus") || name.startsWith("Hans") || COLOR_OS_GROUPS.contains(name);
        boolean hyperOsOnly = name.startsWith("HyperOS") || name.equals("HyperOsFix");
        return hyperOs ? colorOsOnly : hyperOsOnly;
    }

    /** Record one hook group. On a repeated name the newer result wins. */
    public static void record(String name, boolean ok, String detail) {
        if (name == null) return;
        ENTRIES.removeIf(entry -> entry.name.equals(name));
        ENTRIES.add(new Entry(name, ok, detail == null ? "" : detail));
    }

    public static JSONObject toJson() {
        JSONArray hooks = new JSONArray();
        for (Entry entry : ENTRIES) {
            JSONObject hook = new JSONObject();
            try {
                hook.put("name", entry.name).put("ok", entry.ok).put("detail", entry.detail);
            } catch (Exception ignored) { }
            hooks.put(hook);
        }
        JSONObject json = new JSONObject();
        try {
            json.put("time", System.currentTimeMillis());
            json.put("bootTime", System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime());
            json.put("hooks", hooks);
        } catch (Exception ignored) { }
        return json;
    }

    /** system_server → module app, one Binder call via the config provider. */
    public static boolean publish(Context context, String authority) {
        if (context == null || ENTRIES.isEmpty()) return false;
        try {
            Bundle extras = new Bundle();
            extras.putString(EXTRA_JSON, toJson().toString());
            Bundle result = context.getContentResolver().call(
                    Uri.parse("content://" + authority), METHOD, null, extras);
            return result != null && result.getBoolean("ok", false);
        } catch (Throwable error) {
            return false;
        }
    }

    /** Parsed view for the UI. */
    public static final class Snapshot {
        public final long time;
        public final long bootTime;
        public final List<String[]> hooks = new ArrayList<>(); // {name, "1"/"0", detail}

        public Snapshot(JSONObject json) {
            time = json.optLong("time", 0);
            bootTime = json.optLong("bootTime", 0);
            JSONArray array = json.optJSONArray("hooks");
            for (int i = 0; array != null && i < array.length(); i++) {
                JSONObject hook = array.optJSONObject(i);
                if (hook == null) continue;
                hooks.add(new String[]{hook.optString("name"), hook.optBoolean("ok") ? "1" : "0", hook.optString("detail")});
            }
        }

        /** Names of failed groups that belong to this ROM; these are the ones worth an alert. */
        public List<String> failedApplicable(boolean hyperOs) {
            List<String> failed = new ArrayList<>();
            for (String[] hook : hooks)
                if (!"1".equals(hook[1]) && !isForeign(hook[0], false, hyperOs)) failed.add(hook[0]);
            return failed;
        }

        public int okCount() {
            int n = 0;
            for (String[] hook : hooks) if ("1".equals(hook[1])) n++;
            return n;
        }
    }
}
