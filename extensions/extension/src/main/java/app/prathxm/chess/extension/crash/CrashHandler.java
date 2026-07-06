/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.crash;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashHandler";
    private static Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    // Re-entry guard: prevents the crash handler from triggering recursively
    // if a crash occurs inside the :crash process itself (e.g. Firebase init failure).
    private static volatile boolean sHandlingCrash = false;

    private CrashHandler(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void install(Context ctx) {
        if (isCrashProcess()) {
            Log.i(TAG, "Skipping CrashHandler install — running in crash process");
            return;
        }
        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof CrashHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(ctx));
            Log.i(TAG, "Global CrashHandler installed successfully!");
        }
    }

    /**
     * Reads /proc/self/cmdline to get the current process name.
     * This is always reliable regardless of API level or process lifecycle stage,
     * unlike Application.getProcessName() which can return stale values when
     * called during Application.onCreate() in a secondary process.
     */
    private static String getCurrentProcessName() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/cmdline"))) {
            String line = reader.readLine();
            if (line != null) {
                // cmdline is null-delimited; trim trailing null bytes
                return line.trim().replace('\0', ' ').trim();
            }
        } catch (IOException ignored) {}

        // Fallback to API 28+ method
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            return android.app.Application.getProcessName();
        }

        return "";
    }

    private static boolean isCrashProcess() {
        String processName = getCurrentProcessName();
        return processName.endsWith(":crash");
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        // Re-entry guard: if we're already handling a crash (e.g. something in
        // CrashActivity itself threw), just delegate to the default handler.
        if (sHandlingCrash) {
            Log.e(TAG, "Re-entrant crash detected, delegating to default handler", throwable);
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
            return;
        }
        sHandlingCrash = true;

        try {
            Log.e(TAG, "Uncaught exception intercepted", throwable);

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            String stackTrace = sw.toString();

            Intent intent = new Intent(context, CrashActivity.class);
            intent.putExtra("error_log", stackTrace);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);

            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        } catch (Exception e) {
            Log.e(TAG, "Failed in uncaughtException crash reporting", e);
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        }
    }
}
