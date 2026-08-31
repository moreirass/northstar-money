package com.northstar.money.core.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
private val OnboardingCurrencySurface = Color(0xFF141417)
private val OnboardingCurrencySymbolSurface = Color(0xFF24242B)

private data class CurrencyOption(val code: String, val symbol: String, val name: String)

private val currencyOptions = listOf(
    CurrencyOption("EUR", "€", "Euro"),
    CurrencyOption("USD", "$", "Dólar Americano"),
    CurrencyOption("GBP", "£", "Libra Esterlina"),
    CurrencyOption("BRL", "R$", "Real Brasileiro"),
    CurrencyOption("CHF", "CHF", "Franco Suíço"),
    CurrencyOption("JPY", "¥", "Iene Japonês"),
)

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
internal fun OnboardingScreen(
    initialCurrencyCode: String = "EUR",
    onCurrencySelected: (String) -> Unit = {},
    onInitialAccountSubmitted: (String, String) -> Unit = { _, _ -> },
    onComplete: () -> Unit,
) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedCurrencyCode by rememberSaveable { mutableStateOf(initialCurrencyCode) }
    when (pageIndex) {
        0 -> WelcomeOnboardingPage(onStart = { pageIndex = 1 })
        1 -> CurrencyOnboardingPage(
            selectedCurrencyCode = selectedCurrencyCode,
            onCurrencySelected = { selectedCurrencyCode = it },
            onContinue = {
                onCurrencySelected(selectedCurrencyCode)
                pageIndex = 2
            },
        )
        2 -> BalanceOnboardingPage(
            currencyCode = selectedCurrencyCode,
            onContinue = { accountName, balance ->
                onInitialAccountSubmitted(accountName, balance)
                pageIndex = 3
            },
        )
        else -> LegacyOnboardingPageScreen(
            pageIndex = pageIndex - 1,
            onNext = {
                if (pageIndex == legacyOnboardingPages.size) onComplete() else pageIndex++
            },
            onBack = { pageIndex-- },
        )
    }
}

@Composable
internal fun BalanceOnboardingPage(
    currencyCode: String,
    onContinue: (accountName: String, balance: String) -> Unit,
) {
    var balanceText by rememberSaveable { mutableStateOf("0,00") }
    var accountName by rememberSaveable { mutableStateOf("Conta Principal") }
    val balanceIsValid = balanceText.trim().replace(',', '.').toBigDecimalOrNull()?.signum()?.let { it >= 0 } == true
    val currencySymbol = currencyOptions.firstOrNull { it.code == currencyCode }?.symbol ?: currencyCode
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text(
                stringResource(R.string.onboarding_balance_title),
                color = OnboardingPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_balance_body),
                color = OnboardingSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth().border(0.dp, Color.Transparent).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(currencySymbol, color = OnboardingAccent, fontWeight = FontWeight.Bold, fontSize = 32.sp)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = balanceText,
                    onValueChange = { value ->
                        if (value.length <= 15 && value.all { it.isDigit() || it == ',' || it == '.' }) balanceText = value
                    },
                    modifier = Modifier.width(210.dp),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = OnboardingPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 48.sp,
                        textAlign = TextAlign.Start,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    cursorBrush = SolidColor(OnboardingAccent),
                )
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(OnboardingCurrencySymbolSurface))
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.onboarding_account_name),
                color = OnboardingSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(OnboardingCurrencySurface, RoundedCornerShape(12.dp))
                    .border(1.dp, OnboardingCurrencySymbolSurface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = accountName,
                    onValueChange = { accountName = it.take(60) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(color = OnboardingPrimary, fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    cursorBrush = SolidColor(OnboardingAccent),
                )
                Icon(Icons.Default.Edit, contentDescription = null, tint = OnboardingTertiary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingStepIndicator(currentStep = 1, totalSteps = 3)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onContinue(accountName.trim(), balanceText) },
                enabled = accountName.isNotBlank() && balanceIsValid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = OnboardingAccent, contentColor = OnboardingBackground),
            ) {
                Text(stringResource(R.string.onboarding_continue), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
internal fun CurrencyOnboardingPage(
    selectedCurrencyCode: String,
    onCurrencySelected: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.StarBorder, contentDescription = null, tint = OnboardingAccent, modifier = Modifier.size(22.dp))
            Text(
                stringResource(R.string.onboarding_brand),
                color = OnboardingPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 0.8.sp,
            )
        }
        Spacer(Modifier.height(38.dp))
        Text(
            stringResource(R.string.onboarding_currency_title),
            color = OnboardingPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboarding_currency_body),
            color = OnboardingSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            currencyOptions.forEach { currency ->
                val selected = currency.code == selectedCurrencyCode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(OnboardingCurrencySurface, RoundedCornerShape(12.dp))
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) OnboardingAccent else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { onCurrencySelected(currency.code) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(36.dp).background(OnboardingCurrencySymbolSurface, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(currency.symbol, color = OnboardingPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(currency.code, color = OnboardingPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(currency.name, color = OnboardingSecondary, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(1.5.dp, if (selected) OnboardingAccent else OnboardingTertiary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Box(Modifier.size(10.dp).background(OnboardingAccent, CircleShape))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        OnboardingStepIndicator(currentStep = 0, totalSteps = 3)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = OnboardingAccent, contentColor = OnboardingBackground),
        ) {
            Text(stringResource(R.string.onboarding_continue), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
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
