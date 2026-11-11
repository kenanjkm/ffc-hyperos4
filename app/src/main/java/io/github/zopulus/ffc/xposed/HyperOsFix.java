package io.github.zopulus.ffc.xposed;

import android.content.Intent;

import java.lang.reflect.Method;

import io.github.zopulus.ffc.libxposed.XC_MethodHook;
import io.github.zopulus.ffc.libxposed.XposedBridge;
import io.github.zopulus.ffc.libxposed.XposedHelpers;
import io.github.zopulus.ffc.util.ConfigSchema;
import io.github.zopulus.ffc.util.FcmTrust;

/**
 * HyperOS (Xiaomi) system_server hooks. Signatures were checked against HyperOS 4 / Android 17
 * services.jar + miui-services.jar. Every group installs independently; on ColorOS the classes
 * are absent and each group just logs that once.
 *
 * Trust model is the same as the ColorOS hooks: an FCM bypass needs a RECEIVE broadcast whose
 * BroadcastRecord.callingUid is GMS (validated by {@link #trustedDelivery}); follow-up work of the
 * target is only allowed inside the existing 20 s {@code FcmDeliveryWindow} for that UID, and only
 * when GMS or the target itself is the caller.
 *
 * Deep sleep: no wakelocks, alarms or scheduled work. Thawing only happens as a reaction to an FCM
 * broadcast that GMS is already delivering; the window simply expires.
 */
public class HyperOsFix extends XposedModule {
    private static final String BROADCAST_STUB = "com.android.server.am.BroadcastQueueModernStubImpl";
    private static final String AUTO_START = "com.android.server.am.AutoStartManagerServiceStubImpl";
    private static final String GREEZE = "com.miui.server.greeze.GreezeManagerService";
    private static final String IMMOBULUS = "com.miui.server.greeze.AurogonImmobulusMode";
    private static final String NOTIFICATION_IMPL = "com.android.server.notification.NotificationManagerServiceImpl";
    private static final String GMS = "com.google.android.gms";

    private static volatile Class<?> greezeClass;
    private static volatile int gmsAppId = -1;

    public HyperOsFix(ClassLoader classLoader) {
        super(classLoader);
        if (XposedHelpers.findClassIfExists(GREEZE, classLoader) == null
                && XposedHelpers.findClassIfExists(BROADCAST_STUB, classLoader) == null) {
            printLog("HyperOS classes not present, HyperOsFix skipped");
            return;
        }
        greezeClass = XposedHelpers.findClassIfExists(GREEZE, classLoader);
        HookInstaller.install("HyperOS auto-start", this::hookBroadcastAutoStart);
        HookInstaller.install("HyperOS service auto-start", this::hookServiceAutoStart);
        HookInstaller.install("HyperOS Greeze receiver", this::hookGreezeReceiver);
        HookInstaller.install("HyperOS Greeze background action", this::hookGreezeBackgroundAction);
        HookInstaller.install("HyperOS Greeze freeze", this::hookGreezeFreeze);
        HookInstaller.install("HyperOS Greeze GMS limit", this::hookGreezeGmsLimit);
        HookInstaller.install("HyperOS local notification", this::hookLocalNotification);
        HookInstaller.install("HyperOS memory killer", this::hookMemoryKiller);
    }

    // ---------------------------------------------------------------------------------------------
    // MIUI auto-start / wake path for receivers
    // boolean checkApplicationAutoStart(BroadcastQueue, BroadcastRecord, ResolveInfo)       cold start
    // boolean checkReceiverAppDealBroadcast(BroadcastQueue, BroadcastRecord, ProcessRecord, boolean)
    // boolean checkReceiverIfRestricted(BroadcastQueue, BroadcastRecord, ProcessRecord, boolean)
    // ---------------------------------------------------------------------------------------------
    private void hookBroadcastAutoStart() {
        Class<?> stub = XposedHelpers.findClass(BROADCAST_STUB, classLoader);
        int hooks = 0;
        for (Method method : stub.getDeclaredMethods()) {
            String name = method.getName();
            boolean allowWhenTrusted = "checkApplicationAutoStart".equals(name)
                    || "checkReceiverAppDealBroadcast".equals(name);
            boolean restricted = "checkReceiverIfRestricted".equals(name);
            if ((!allowWhenTrusted && !restricted) || method.getReturnType() != boolean.class) continue;
            int recordIndex = indexOfType(method, "com.android.server.am.BroadcastRecord");
            if (recordIndex < 0) continue;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object record = param.args[recordIndex];
                    if (record == null) return;
                    Intent intent = (Intent) XposedHelpers.getObjectField(record, "intent");
                    String target = explicitTarget(intent);
                    if (!trustedDelivery(intent, target, param)) return;
                    int uid = OplusProxyFix.beginFcmDeliveryWindow(target);
                    thaw(uid);
                    printLog("HyperOS " + name + " bypass for FCM: " + target, true);
                    param.setResult(allowWhenTrusted);
                }
            });
            hooks++;
        }
        if (hooks == 0) throw new NoSuchMethodError(BROADCAST_STUB + "#checkApplicationAutoStart");
        printLog("HyperOS auto-start hooks: " + hooks);
    }

    // boolean isAllowStartService(Context, Intent service, int userId[, int uid])
    // The service Intent carries no FCM action, so it is only allowed inside the delivery window.
    private void hookServiceAutoStart() {
        Class<?> clazz = XposedHelpers.findClass(AUTO_START, classLoader);
        int hooks = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            if (!"isAllowStartService".equals(method.getName()) || method.getReturnType() != boolean.class) continue;
            int intentIndex = indexOfType(method, Intent.class.getName());
            if (intentIndex < 0) continue;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String target = explicitTarget((Intent) param.args[intentIndex]);
                    if (target == null) return;
                    int uid = packageUid(target);
                    if (inWindow(uid, target)) {
                        printLog("HyperOS service start allowed in FCM window: " + target, true);
                        param.setResult(true);
                    }
                }
            });
            hooks++;
        }
        if (hooks == 0) throw new NoSuchMethodError(AUTO_START + "#isAllowStartService");
    }

    // ---------------------------------------------------------------------------------------------
    // Greeze (MIUI app freezer)
    // ---------------------------------------------------------------------------------------------

    // boolean isRestrictReceiver(Intent, int callerUid, String callerPkg, int calleeUid, String calleePkg)
    private void hookGreezeReceiver() {
        Method method = XposedHelpers.findMethodExact(requireGreeze(), "isRestrictReceiver",
                Intent.class, int.class, String.class, int.class, String.class);
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!isBootComplete) return;
                Intent intent = (Intent) param.args[0];
                int callerUid = (Integer) param.args[1];
                int calleeUid = (Integer) param.args[3];
                String calleePkg = (String) param.args[4];
                if (intent == null || calleePkg == null) return;
                boolean gmsCaller = isGmsUid(callerUid);
                boolean fcm = gmsCaller && FcmTrust.RECEIVE.equals(intent.getAction())
                        && calleePkg.equals(explicitTarget(intent)) && isDeliveryTargetAllowed(calleePkg);
                if (fcm) {
                    OplusProxyFix.beginFcmDeliveryWindow(calleePkg);
                } else if (!(inWindow(calleeUid, calleePkg) && (gmsCaller || callerUid == calleeUid))) {
                    return;
                }
                thaw(calleeUid);
                printLog("HyperOS Greeze receiver allowed: " + calleePkg, true);
                param.setResult(false);
            }
        });
    }

    // boolean isRestrictBackgroundAction(String what, int callerUid, String callerPkg, int calleeUid, String calleePkg)
    // Despite the name it returns true when the action is ALLOWED (Greeze then thaws the callee).
    private void hookGreezeBackgroundAction() {
        Method method = XposedHelpers.findMethodExact(requireGreeze(), "isRestrictBackgroundAction",
                String.class, int.class, String.class, int.class, String.class);
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                int callerUid = (Integer) param.args[1];
                int calleeUid = (Integer) param.args[3];
                String calleePkg = (String) param.args[4];
                if (!inWindow(calleeUid, calleePkg)) return;
                if (!isGmsUid(callerUid) && callerUid != calleeUid) return;
                thaw(calleeUid);
                printLog("HyperOS Greeze " + param.args[0] + " allowed in FCM window: " + calleePkg, true);
                param.setResult(true);
            }
        });
    }

    // boolean freezeAction(int uid, int fromWho, String reason, boolean, boolean)
    // List<Integer> freezeUids(int[] uids, long timeout, int fromWho, String reason, boolean checkAudioGps)
    private void hookGreezeFreeze() {
        Class<?> greeze = requireGreeze();
        Method freezeAction = XposedHelpers.findMethodExact(greeze, "freezeAction",
                int.class, int.class, String.class, boolean.class, boolean.class);
        XposedBridge.hookMethod(freezeAction, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (keepUnfrozen((Integer) param.args[0])) param.setResult(false);
            }
        });
        Method freezeUids = XposedHelpers.findMethodExact(greeze, "freezeUids",
                int[].class, long.class, int.class, String.class, boolean.class);
        XposedBridge.hookMethod(freezeUids, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                int[] uids = (int[]) param.args[0];
                if (uids == null) return;
                param.args[0] = io.github.zopulus.ffc.util.IdFilter.without(uids, uid -> keepUnfrozen(uid));
            }
        });
        // PowerKeeper freezes an app's non-key processes by PID (ActionExecute.freezeNonKeyProcesses ->
        // freezePids), and PerfService uses freezeProcess. Neither goes through freezeAction/freezeUids,
        // so a multi-process app could still lose a helper process inside the delivery window.
        // List<Integer> freezePids(int[] pids, long timeout, int fromWho, String reason)
        Method freezePids = XposedHelpers.findMethodExact(greeze, "freezePids",
                int[].class, long.class, int.class, String.class);
        XposedBridge.hookMethod(freezePids, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                int[] pids = (int[]) param.args[0];
                if (pids == null) return;
                param.args[0] = io.github.zopulus.ffc.util.IdFilter.without(pids, pid -> keepUnfrozen(uidForPid(pid)));
            }
        });
        // boolean freezeProcess(RunningProcess proc, long timeout, int fromWho, String reason)
        for (Method method : greeze.getDeclaredMethods()) {
            if (!"freezeProcess".equals(method.getName()) || method.getParameterCount() != 4) continue;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object proc = param.args[0];
                    if (proc == null) return;
                    Object uid = XposedHelpers.getObjectField(proc, "uid");
                    if (uid instanceof Integer && keepUnfrozen((Integer) uid)) param.setResult(false);
                }
            });
        }
    }

    // CN ROM: PowerKeeper's GmsObserver calls updateGmsNetStatus(true) when Google is unreachable,
    // and Greeze then quick-freezes GMS while the screen is off (AurogonImmobulusMode).
    private void hookGreezeGmsLimit() {
        Method update = XposedHelpers.findMethodExact(requireGreeze(), "updateGmsNetStatus", boolean.class);
        XposedBridge.hookMethod(update, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(param.args[0]) && protectGms()) {
                    logOnce("HyperOS Greeze GMS network limit ignored");
                    param.args[0] = false;
                }
            }
        });
        Class<?> immobulus = XposedHelpers.findClass(IMMOBULUS, classLoader);
        Method quickFreeze = XposedHelpers.findMethodExact(immobulus, "triggerQuickFreezeAction", int.class);
        XposedBridge.hookMethod(quickFreeze, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (isGmsAppUid((Integer) param.args[0]) && protectGms()) param.setResult(null);
            }
        });
    }

    // boolean isDeniedLocalNotification(AppOpsManager, Notification, int callingUid, String callingPkg)
    private void hookLocalNotification() {
        Class<?> clazz = XposedHelpers.findClass(NOTIFICATION_IMPL, classLoader);
        int hooks = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            String name = method.getName();
            if (!"isDeniedLocalNotification".equals(name) && !"isAllowLocalNotification".equals(name)) continue;
            if (method.getReturnType() != boolean.class) continue;
            int pkgIndex = lastIndexOfType(method, String.class.getName());
            if (pkgIndex < 0) continue;
            boolean result = "isAllowLocalNotification".equals(name);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String pkg = (String) param.args[pkgIndex];
                    // The module's own hook alert must not be dropped by MIUI either.
                    if (SELF_PACKAGE_NAME.equals(pkg) || isDeliveryTargetAllowed(pkg)) param.setResult(result);
                }
            });
            hooks++;
        }
        if (hooks == 0) throw new NoSuchMethodError(NOTIFICATION_IMPL + "#isDeniedLocalNotification");
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** Thaws through Greeze's own async API, which is safe while ActivityManagerService locks are held. */
    static void thaw(int uid) {
        Class<?> greeze = greezeClass;
        if (uid < 10000 || greeze == null) return;
        try {
            Object service = XposedHelpers.callStaticMethod(greeze, "getService");
            // Use a reason Greeze knows ("broadcast"); unknown reasons log a warning on every thaw.
            if (service != null) XposedHelpers.callMethod(service, "thawUidAsync", uid, 1000, "broadcast");
        } catch (Throwable error) {
            logOnce("HyperOS Greeze thaw failed: " + error);
        }
    }

    private Class<?> requireGreeze() {
        Class<?> greeze = greezeClass;
        if (greeze == null) throw new XposedHelpers.ClassNotFoundError(GREEZE, null);
        return greeze;
    }

    private boolean protectGms() {
        return getBooleanConfig(ConfigSchema.KEY_DOZE_GOOGLE_WHITELIST, true);
    }

    private boolean keepUnfrozen(int uid) {
        if (uid < 10000) return false;
        if (isProtectedUid(uid)) return true;
        String windowPackage = OplusProxyFix.getFcmDeliveryPackage(uid);
        if (windowPackage != null && isDeliveryTargetAllowed(windowPackage)) return true;
        return isGmsAppUid(uid) && protectGms();
    }

    private static boolean inWindow(int uid, String packageName) {
        if (uid < 10000 || packageName == null) return false;
        String windowPackage = OplusProxyFix.getFcmDeliveryPackage(uid);
        return packageName.equals(windowPackage) && isDeliveryTargetAllowed(windowPackage);
    }

    // ---------------------------------------------------------------------------------------------
    // HyperOS sleep mode switch
    // PowerKeeper's PhoneSleepModeController turns mobile data, Wi-Fi and Bluetooth off when it
    // decides the user is asleep (seen on device: data off 00:42 -> 06:45, no FCM at all meanwhile).
    // Its user switch is the PowerKeeper setting "sleep_mode_user" ("on"/"off"), behind a provider
    // permission only system_server holds, so the module writes it here on the user's behalf.
    // ---------------------------------------------------------------------------------------------

    private static final android.net.Uri POWERKEEPER_MISC =
            android.net.Uri.parse("content://com.miui.powerkeeper.configure/SimpleSettings/misc");

    @Override
    protected void onConfigLoaded(io.github.zopulus.ffc.util.ConfigSnapshot previous,
                                  io.github.zopulus.ffc.util.ConfigSnapshot current) {
        if (greezeClass == null || current == null) return;
        boolean wanted = current.options.getOrDefault(ConfigSchema.KEY_HYPEROS_SLEEP_MODE, true);
        Boolean before = previous == null ? null : previous.options.get(ConfigSchema.KEY_HYPEROS_SLEEP_MODE);
        // Only act on the user's choice: "off" whenever it is off (idempotent, also after boot);
        // "on" only when it was switched back on. Untouched, the system's own setting is left alone.
        if (!wanted) writeSleepMode("off");
        else if (Boolean.FALSE.equals(before)) writeSleepMode("on");
    }

    private static void writeSleepMode(String value) {
        if (context == null) return;
        long identity = android.os.Binder.clearCallingIdentity();
        try {
            android.os.Bundle extras = new android.os.Bundle();
            extras.putString("value", value);
            extras.putInt("_user", 0);
            context.getContentResolver().call(POWERKEEPER_MISC, "PUT_misc", "sleep_mode_user", extras);
            printLog("HyperOS sleep mode set to " + value);
        } catch (Throwable error) {
            printLog("HyperOS sleep mode switch failed: " + error);
        } finally {
            android.os.Binder.restoreCallingIdentity(identity);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Critical app protection (opt-in per app, costs battery)
    // ---------------------------------------------------------------------------------------------

    // UMMS (ProcessKillerForUMMS) asks this before killing a background process; a non-null reason
    // means "skip". Instagram's own push process (:fbns) was killed here with umms_launch/umms_pressure.
    // String getSystemCleanWhiteListSkipReason(ProcessRecord app, int minAdj, int adj)
    private void hookMemoryKiller() {
        Class<?> killer = XposedHelpers.findClass("com.android.server.am.ProcessKillerForUMMS", classLoader);
        Method method = null;
        for (Method m : killer.getDeclaredMethods()) {
            if ("getSystemCleanWhiteListSkipReason".equals(m.getName()) && m.getParameterCount() == 3
                    && m.getReturnType() == String.class) method = m;
        }
        if (method == null) throw new NoSuchMethodError("ProcessKillerForUMMS#getSystemCleanWhiteListSkipReason");
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object app = param.args[0];
                // minAdj <= 200 is critical memory pressure: leave the system its last resort.
                if (app == null || !(param.args[1] instanceof Integer) || (Integer) param.args[1] <= 200) return;
                Object uid = XposedHelpers.getObjectField(app, "uid");
                if (uid instanceof Integer && isProtectedUid((Integer) uid)) {
                    logOnce("HyperOS memory killer skips protected uid " + uid);
                    param.setResult("fcmfix-protected");
                }
            }
        });
    }

    private static volatile java.util.Set<String> protectedCacheKey = java.util.Collections.emptySet();
    private static volatile java.util.Set<Integer> protectedAppIds = java.util.Collections.emptySet();

    /** App-id match for the user's protected packages; resolved again only when the selection changes. */
    private static boolean isProtectedUid(int uid) {
        if (uid < 10000) return false;
        java.util.Set<String> packages = protectedPackages();
        if (packages.isEmpty()) return false;
        if (packages != protectedCacheKey) {
            java.util.Set<Integer> ids = new java.util.HashSet<>();
            for (String pkg : packages) {
                int pkgUid = packageUid(pkg);
                if (pkgUid >= 10000) ids.add(pkgUid % 100000);
            }
            protectedAppIds = ids;
            protectedCacheKey = packages;
        }
        return protectedAppIds.contains(uid % 100000);
    }

    /** GMS in any user: compares app ids, cached after the first lookup. */
    private static boolean isGmsAppUid(int uid) {
        if (uid < 10000) return false;
        if (gmsAppId < 0) {
            int gmsUid = packageUid(GMS);
            if (gmsUid >= 10000) gmsAppId = gmsUid % 100000;
        }
        return gmsAppId > 0 && uid % 100000 == gmsAppId;
    }

    /** Owner UID of a PID from /proc (no Binder, no locks); -1 when the process is gone. */
    private static int uidForPid(int pid) {
        try {
            return android.system.Os.stat("/proc/" + pid).st_uid;
        } catch (Throwable error) {
            return -1;
        }
    }

    private static int packageUid(String packageName) {
        if (packageName == null || context == null) return -1;
        try {
            return context.getPackageManager().getPackageUid(packageName, 0);
        } catch (Throwable error) {
            return -1;
        }
    }

    private static int indexOfType(Method method, String className) {
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) if (className.equals(types[i].getName())) return i;
        return -1;
    }

    private static int lastIndexOfType(Method method, String className) {
        Class<?>[] types = method.getParameterTypes();
        for (int i = types.length - 1; i >= 0; i--) if (className.equals(types[i].getName())) return i;
        return -1;
    }
}
