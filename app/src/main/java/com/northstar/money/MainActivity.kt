package com.northstar.money

import android.os.Bundle
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.northstar.money.core.designsystem.NorthstarTheme
import com.northstar.money.core.navigation.NorthstarApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                showSecurityError(getString(R.string.security_settings_unavailable))
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
            showSecurityError(getString(R.string.security_auth_unavailable))
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
                    showSecurityError(getString(R.string.security_auth_incomplete))
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.security_unlock_title))
                .setSubtitle(getString(R.string.security_unlock_subtitle))
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
                        Text(stringResource(R.string.ui_northstar_is_locked), style = MaterialTheme.typography.headlineSmall)
                        Text(message, modifier = Modifier.padding(vertical = 16.dp))
                        Button(onClick = ::recreate) { Text(stringResource(R.string.ui_retry)) }
                    }
                }
            }
        }
    }
}
