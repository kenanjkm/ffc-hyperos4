package io.github.zopulus.ffc.xposed;

import android.content.SharedPreferences;
import android.telephony.TelephonyManager;

import java.lang.reflect.Method;

import io.github.zopulus.ffc.libxposed.XC_MethodHook;
import io.github.zopulus.ffc.libxposed.XposedBridge;
import io.github.zopulus.ffc.libxposed.XposedHelpers;
import io.github.zopulus.ffc.util.ConfigSchema;

/**
 * HyperOS PowerKeeper (com.miui.powerkeeper): keep mobile data on while "sleep mode" is active.
 *
 * PhoneSleepModeController enters sleep mode when it decides the user is asleep and calls
 * CommonAdapter.setDataEnabled(telephonyManager, false); on device this cut data from 00:42 to
 * 06:45 and no FCM arrived. Only that one call is skipped, and only when it comes from the sleep
 * mode controller and the user enabled the option. Wi-Fi/Bluetooth/alarm restrictions of sleep
 * mode stay as they are; nothing here keeps the device awake (Doze is untouched).
 */
public class PowerKeeperSleepFix extends XposedModule {
    private static final String COMMON_ADAPTER = "com.miui.powerkeeper.utils.CommonAdapter";

    public PowerKeeperSleepFix(ClassLoader classLoader) {
        super(classLoader);
        Class<?> adapter = XposedHelpers.findClass(COMMON_ADAPTER, classLoader);
        Method method = XposedHelpers.findMethodExact(adapter, "setDataEnabled", TelephonyManager.class, boolean.class);
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!Boolean.FALSE.equals(param.args[1]) || !keepDataEnabled() || !calledBySleepMode()) return;
                printLog("PowerKeeper sleep mode: mobile data kept on");
                param.setResult(null);
            }
        });
        printLog("PowerKeeper sleep-mode data hook active");
    }

    /** Read live from the module's remote preferences, so the switch applies without a reboot. */
    private static boolean keepDataEnabled() {
        try {
            SharedPreferences prefs = XposedBridge.getRemotePreferences("config");
            return prefs != null && prefs.getBoolean(ConfigSchema.KEY_SLEEP_MODE_KEEP_DATA,
                    ConfigSchema.defaultValue(ConfigSchema.KEY_SLEEP_MODE_KEEP_DATA));
        } catch (Throwable error) {
            return false;
        }
    }

    /** Only the phone/pad sleep-mode controllers; any other PowerKeeper caller is left alone. */
    private static boolean calledBySleepMode() {
        for (StackTraceElement frame : new Throwable().getStackTrace()) {
            String name = frame.getClassName();
            if (name.startsWith("com.miui.powerkeeper.statemachine.PhoneSleepModeController")
                    || name.startsWith("com.miui.powerkeeper.statemachine.PadSleepModeController")) return true;
        }
        return false;
    }
}
