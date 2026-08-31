package com.northstar.money.core.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.northstar.money.R

private val OnboardingBackground = Color(0xFF08080A)
private val OnboardingSurface = Color(0xFF18181C)
private val OnboardingPrimary = Color.White
private val OnboardingSecondary = Color(0xFF8E8E9F)
private val OnboardingTertiary = Color(0xFF4F4F5F)
private val OnboardingAccent = Color(0xFF10B981)

private data class LegacyOnboardingPage(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val icon: ImageVector,
)

private val legacyOnboardingPages = listOf(
    LegacyOnboardingPage(R.string.onboarding_welcome_title, R.string.onboarding_welcome_body, Icons.Default.AccountBalanceWallet),
    LegacyOnboardingPage(R.string.onboarding_privacy_title, R.string.onboarding_privacy_body, Icons.Default.Lock),
    LegacyOnboardingPage(R.string.onboarding_ready_title, R.string.onboarding_ready_body, Icons.Default.CheckCircle),
)

@Composable
internal fun OnboardingScreen(onComplete: () -> Unit) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    if (pageIndex == 0) {
        WelcomeOnboardingPage(onStart = { pageIndex = 1 })
    } else {
        LegacyOnboardingPageScreen(
            pageIndex = pageIndex - 1,
            onNext = {
                if (pageIndex == legacyOnboardingPages.size) onComplete() else pageIndex++
            },
            onBack = { pageIndex-- },
        )
    }
}

@Composable
internal fun WelcomeOnboardingPage(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        Box(
            modifier = Modifier.size(80.dp).background(OnboardingSurface, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.StarBorder,
                contentDescription = null,
                tint = OnboardingAccent,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.onboarding_brand),
            color = OnboardingPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.height(64.dp))
        Text(
            stringResource(R.string.onboarding_new_welcome_title),
            color = OnboardingPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_new_welcome_body),
            color = OnboardingSecondary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        OnboardingStepIndicator(currentStep = 0)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = OnboardingAccent, contentColor = OnboardingBackground),
        ) {
            Text(stringResource(R.string.onboarding_start), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
internal fun OnboardingStepIndicator(currentStep: Int, totalSteps: Int = 4) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(totalSteps) { index ->
            Box(
                Modifier
                    .size(width = if (index == currentStep) 24.dp else 8.dp, height = 8.dp)
                    .background(if (index == currentStep) OnboardingAccent else OnboardingTertiary, CircleShape),
            )
        }
    }
}

@Composable
private fun LegacyOnboardingPageScreen(pageIndex: Int, onNext: () -> Unit, onBack: () -> Unit) {
    val page = legacyOnboardingPages[pageIndex]
    val isLastPage = pageIndex == legacyOnboardingPages.lastIndex
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.onboarding_progress, pageIndex + 2, 4), style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { (pageIndex + 2) / 4f }, modifier = Modifier.fillMaxWidth().height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Card(shape = CircleShape) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                            modifier = Modifier.padding(24.dp).size(56.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Text(
                stringResource(page.titleRes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(page.bodyRes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(if (isLastPage) R.string.onboarding_finish else R.string.onboarding_next))
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.onboarding_back))
            }
        }
    }
}
