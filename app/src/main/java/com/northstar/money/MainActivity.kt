package com.northstar.money

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.northstar.money.core.designsystem.NorthstarTheme
import com.northstar.money.core.navigation.NorthstarApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        lifecycleScope.launch {
            val application = application as NorthstarApplication
            try {
                if (application.userPreferences.settings.first().appLockEnabled) {
                    authenticate { showApp() }
                } else {
                    showApp()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                showSecurityError("Northstar could not verify the app-lock setting. Your financial data remains locked.")
            }
        }
    }

    private fun showApp() {
        setContent {
            NorthstarTheme {
                NorthstarApp()
            }
        }
    }

    private fun authenticate(onSuccess: () -> Unit) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            showSecurityError("Device authentication is unavailable. Set up a secure screen lock, then retry.")
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    showSecurityError("Authentication was not completed. Your financial data remains locked.")
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Northstar")
                .setSubtitle("Use biometrics or your device credential")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    private fun showSecurityError(message: String) {
        setContent {
            NorthstarTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Northstar is locked", style = MaterialTheme.typography.headlineSmall)
                        Text(message, modifier = Modifier.padding(vertical = 16.dp))
                        Button(onClick = ::recreate) { Text("Retry") }
                    }
                }
            }
        }
    }
}
