package com.example.wellnesstracker.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object PackageUtils {
    private const val TAG = "PackageUtils"

    private val COMMON_PACKAGES = listOf(
        "com.facebook.katana",
        "com.zhiliaoapp.musically",
        "com.facebook.orca",
        "com.facebook.lite",
        "com.zhiliaoapp.musically.go",
        "com.whatsapp.w4b",
        "com.lemon.lvoverseas",
        "lk.bhasha.helakuru",
        "com.snapchat.android",
        "com.instagram.android",
        "com.imo.android.imoim",
        "us.zoom.videomeetings",
        "com.einnovation.temu",
        "com.pinterest",
        "com.instagram.barcelona",
        "com.adobe.lrmobile",
        "com.mobitel.selfcare",
        "lk.ikman",
        "com.calculator.hideu",
        "com.intsig.camscanner",
        "com.camerasideas.instashot",
        "com.DCGames.DrivingSimulatorSrilanka",
        "com.outfit7.talkingangelafree",
        "com.outfit7.talkingtom2free",
        "air.com.winkypinky.kittykateunicorndailycaring",
        "air.net.chicworld.brideweddingdresses",
        "angela.tom.video_call",
        "com.dobroapps.anti.stress",
        "com.psiphon3.subscription",
        "com.soundryt.music"
    )

    /**
     * Safely checks a curated list of commonly-known packages and logs presence/absence.
     * This method catches security and name-not-found exceptions so it won't spam error logs
     * on devices where package visibility is restricted.
     */
    fun logInstalledPackages(ctx: Context) {
        val pm = ctx.packageManager
        for (pkg in COMMON_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                Log.i(TAG, "Package present: $pkg")
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed; debug-level log to reduce noise
                Log.d(TAG, "Package not installed: $pkg")
            } catch (e: SecurityException) {
                // Visibility restricted (Android 11+), warn once per package
                Log.w(TAG, "No permission to query package: $pkg", e)
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error while checking package: $pkg", e)
            }
        }
    }
}
