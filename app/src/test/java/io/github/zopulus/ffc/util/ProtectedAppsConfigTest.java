package io.github.zopulus.ffc.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProtectedAppsConfigTest {
    private static JSONObject config(String... protectedApps) throws Exception {
        JSONObject json = new JSONObject().put("revision", 1L).put("allowList", new JSONArray());
        if (protectedApps != null) json.put(ConfigSchema.KEY_PROTECTED_APPS, new JSONArray(java.util.Arrays.asList(protectedApps)));
        return json;
    }

    @Test public void missingKeyMeansNothingProtected() throws Exception {
        JSONObject json = new JSONObject().put("allowList", new JSONArray());
        assertTrue(ConfigCodec.fromJson(json).protectedApps.isEmpty());
    }

    @Test public void roundTripsThroughJson() throws Exception {
        assertEquals(Set.of("com.instagram.android"), ConfigCodec.fromJson(config("com.instagram.android")).protectedApps);
    }

    @Test public void olderStoreWithoutKeyMatchesEmptySelection() {
        Map<String, Object> values = new HashMap<>();
        values.put("init", true);
        values.put("revision", 3L);
        values.put("allowList", Set.of());
        values.put("installEvents", Set.of());
        for (String key : ConfigSchema.OPTIONS) values.put(key, ConfigSchema.defaultValue(key));
        ConfigSnapshot snapshot = new ConfigSnapshot(values);
        assertTrue(snapshot.matchesPreferences(values));
        values.put(ConfigSchema.KEY_PROTECTED_APPS, Set.of("com.instagram.android"));
        assertFalse(snapshot.matchesPreferences(values));
    }

    @Test public void userEditKeepsConcurrentChangesAndAppliesDelta() throws Exception {
        JSONObject current = config("a", "b");          // saved meanwhile: b was added elsewhere
        JSONObject previous = config("a");              // what the UI showed
        JSONObject requested = config("c");             // user removed a, added c
        JSONObject merged = ConfigEdits.merge(current, previous, requested);
        assertEquals(Set.of("b", "c"), ConfigCodec.fromJson(merged).protectedApps);
    }

    @Test public void invalidEntryIsRejected() throws Exception {
        JSONObject json = config().put(ConfigSchema.KEY_PROTECTED_APPS, new JSONArray().put(5));
        try { ConfigCodec.fromJson(json); fail("non-string accepted"); } catch (IllegalArgumentException expected) { }
    }
}
