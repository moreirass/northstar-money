package com.northstar.money

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.northstar.money.core.designsystem.NorthstarTheme
import com.northstar.money.core.navigation.NorthstarApp
import com.northstar.money.core.navigation.LocalAppUnlocked
import com.northstar.money.core.security.AppAuthentication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var unlocked by mutableStateOf(false)
    private var appStarted by mutableStateOf(false)
    private var lockEnabled = true
    private var foreground = false
    private var authenticatedWhileAway = false
    private var authenticating by mutableStateOf(false)
    private var message by mutableStateOf("")
    private var prompt: BiometricPrompt? = null
    private val credentialLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        authenticating = false
        if (result.resultCode == RESULT_OK) {
            if (foreground) unlock() else authenticatedWhileAway = true
        } else message = getString(R.string.security_auth_incomplete)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            NorthstarTheme {
                Box(Modifier.fillMaxSize()) {
                    // Keep activity-result and form state while external pickers are open.
                    // Hide pixels and accessibility nodes until authentication succeeds.
                    if (appStarted) Box(if (unlocked) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}) {
                        CompositionLocalProvider(LocalAppUnlocked provides unlocked) { NorthstarApp() }
                    }
                    if (!unlocked) Surface(Modifier.fillMaxSize().clickable { }) {
                        Column(Modifier.fillMaxSize().padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(getString(R.string.ui_northstar_is_locked), style = MaterialTheme.typography.headlineSmall)
                            Text(message, Modifier.padding(vertical = 16.dp))
                            Button(onClick = { authenticate() }, enabled = !authenticating) { Text(getString(R.string.ui_retry)) }
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    (application as NorthstarApplication).userPreferences.settings.collect { settings ->
                        lockEnabled = settings.appLockEnabled
                        if (lockEnabled || settings.moneyValuesHidden) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        if (!lockEnabled) unlock() else if (!unlocked && !authenticating) authenticate()
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) {
                    unlocked = false
                    message = getString(R.string.security_settings_unavailable)
                }
            }
        }
    }

    override fun onStart() {
        foreground = true
        super.onStart()
        if (authenticatedWhileAway) { authenticatedWhileAway = false; unlock() }
    }

    override fun onStop() {
        foreground = false
        // Re-read settings on every return; a just-enabled lock must not race this callback.
        unlocked = false
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Device credentials may open a system Activity on API 26–29; keep its prompt alive.
        super.onStop()
    }

    private fun unlock() {
        unlocked = true
        appStarted = true
        message = ""
    }

    private fun authenticate() {
        if (!foreground || authenticating) return
        if (!AppAuthentication.available(this)) {
            message = getString(R.string.security_auth_unavailable)
            return
        }
        authenticating = true
        if (android.os.Build.VERSION.SDK_INT < 30 && androidx.biometric.BiometricManager.from(this)
                .canAuthenticate(AppAuthentication.authenticators()) != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            authenticateLegacyCredential()
            return
        }
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticating = false
                    if (foreground) unlock() else authenticatedWhileAway = true
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authenticating = false
                    if (android.os.Build.VERSION.SDK_INT < 30 && errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        authenticateLegacyCredential()
                        return
                    }
                    message = getString(R.string.security_auth_incomplete)
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.security_unlock_title))
            .setSubtitle(getString(R.string.security_unlock_subtitle))
            .setAllowedAuthenticators(AppAuthentication.authenticators())
        if (android.os.Build.VERSION.SDK_INT < 30) info.setNegativeButtonText(getString(R.string.security_use_device_lock))
        prompt?.authenticate(info.build())
    }

    @Suppress("DEPRECATION")
    private fun authenticateLegacyCredential() {
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        val intent = keyguard.createConfirmDeviceCredentialIntent(getString(R.string.security_unlock_title), getString(R.string.security_unlock_subtitle))
        if (intent == null) { authenticating = false; message = getString(R.string.security_auth_unavailable) }
        else { authenticating = true; credentialLauncher.launch(intent) }
    }
}
