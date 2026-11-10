package io.github.zopulus.ffc.util;

import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class HookStatusTest {
    private static JSONObject hook(String name, boolean ok, String detail) throws Exception {
        return new JSONObject().put("name", name).put("ok", ok).put("detail", detail);
    }

    private static HookStatus.Snapshot snapshot(JSONObject... hooks) throws Exception {
        return new HookStatus.Snapshot(new JSONObject().put("time", 5L).put("bootTime", 2L)
                .put("hooks", new JSONArray(Arrays.asList(hooks))));
    }

    @Test public void parsesEntriesAndCountsSuccesses() throws Exception {
        HookStatus.Snapshot s = snapshot(hook("BroadcastFix", true, ""), hook("HyperOS auto-start", false, "x"));
        assertEquals(5L, s.time);
        assertEquals(2L, s.bootTime);
        assertEquals(2, s.hooks.size());
        assertEquals(1, s.okCount());
        assertArrayEquals(new String[]{"HyperOS auto-start", "0", "x"}, s.hooks.get(1));
    }

    @Test public void missingHooksArrayIsEmpty() {
        HookStatus.Snapshot s = new HookStatus.Snapshot(new JSONObject());
        assertTrue(s.hooks.isEmpty());
        assertEquals(0, s.okCount());
        assertTrue(s.failedApplicable(true).isEmpty());
    }

    @Test public void failedColorOsGroupsAreForeignOnHyperOs() {
        assertTrue(HookStatus.isForeign("OplusProxyWakeLock", false, true));
        assertTrue(HookStatus.isForeign("HansCGroup FCM window", false, true));
        assertTrue(HookStatus.isForeign("isGmsRestricted", false, true));
        assertTrue(HookStatus.isForeign("deep-sleep GMS force-stop", false, true));
        // The same failures are real errors on ColorOS.
        assertFalse(HookStatus.isForeign("OplusProxyWakeLock", false, false));
        assertFalse(HookStatus.isForeign("isGmsRestricted", false, false));
    }

    @Test public void failedHyperOsGroupsAreForeignOnlyOnColorOs() {
        assertTrue(HookStatus.isForeign("HyperOS Greeze freeze", false, false));
        assertTrue(HookStatus.isForeign("HyperOsFix", false, false));
        assertFalse(HookStatus.isForeign("HyperOS Greeze freeze", false, true));
        assertFalse(HookStatus.isForeign("HyperOsFix", false, true));
    }

    @Test public void installedAndSharedGroupsAreNeverForeign() {
        assertFalse(HookStatus.isForeign("OplusProxyFix", true, true));
        assertFalse(HookStatus.isForeign("HyperOsFix", true, false));
        for (boolean hyperOs : new boolean[]{true, false}) {
            assertFalse(HookStatus.isForeign("BroadcastFix", false, hyperOs));
            assertFalse(HookStatus.isForeign("KeepNotification", false, hyperOs));
            assertFalse(HookStatus.isForeign("NewAppInstallReceiver", false, hyperOs));
        }
    }

    @Test public void alertListsOnlyFailuresThatBelongToThisRom() throws Exception {
        HookStatus.Snapshot s = snapshot(
                hook("BroadcastFix", true, ""),
                hook("OplusProxyWakeLock", false, "NoClassDefFoundError"),
                hook("HyperOS Greeze receiver", false, "NoSuchMethodError"),
                hook("KeepNotification", false, "NoSuchMethodError"),
                hook("HyperOS Greeze freeze", true, ""));
        assertEquals(Arrays.asList("HyperOS Greeze receiver", "KeepNotification"), s.failedApplicable(true));
        assertEquals(Arrays.asList("OplusProxyWakeLock", "KeepNotification"), s.failedApplicable(false));
    }

    @Test public void fullyWorkingHyperOsReportRaisesNoAlert() throws Exception {
        HookStatus.Snapshot s = snapshot(
                hook("BroadcastFix", true, ""), hook("KeepNotification", true, ""),
                hook("HyperOS auto-start", true, ""), hook("HyperOS Greeze GMS limit", true, ""),
                hook("HyperOsFix", true, ""), hook("Google network firewall", false, "NoClassDefFoundError"));
        assertTrue(s.failedApplicable(true).isEmpty());
    }
}
