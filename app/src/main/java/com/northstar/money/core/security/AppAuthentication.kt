package com.northstar.money.core.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager

object AppAuthentication {
    fun authenticators(): Int = if (Build.VERSION.SDK_INT >= 30)
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        else BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun available(context: Context): Boolean = BiometricManager.from(context)
        .canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS ||
        (Build.VERSION.SDK_INT < 30 && context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure)
}
