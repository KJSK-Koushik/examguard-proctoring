package com.examguard.proctoring

import android.app.Application
import android.util.Log

/**
 * Application entry point. Installs a process-wide uncaught-exception hook so
 * crashes are written to logcat with a stable tag (a lightweight stand-in for a
 * crash-reporting SDK, which is intentionally omitted to keep the app offline).
 */
class ExamGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("ExamGuardCrash", "Uncaught exception on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
        Log.i("ExamGuard", "Application started (offline proctoring build ${BuildConfig.VERSION_NAME})")
    }
}
