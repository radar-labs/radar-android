package org.thoughtcrime.securesms.payments.monero

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process

object MoneroProcess {
  @JvmStatic
  fun isSupportedDevice(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
      Process.is64Bit() &&
      Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"

  @JvmStatic
  fun isIsolated(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 28) return Process.isIsolated()
    return try {
      (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
      false
    } catch (error: SecurityException) {
      error.message?.contains("isolated", ignoreCase = true) == true
    }
  }
}
