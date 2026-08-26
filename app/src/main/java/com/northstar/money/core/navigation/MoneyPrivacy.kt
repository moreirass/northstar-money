package com.northstar.money.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.northstar.money.R
import com.northstar.money.domain.model.Money

internal val LocalMoneyValuesHidden = staticCompositionLocalOf { false }

internal fun moneyDisplayValue(hidden: Boolean, formattedValue: String, hiddenValue: String): String =
    if (hidden) hiddenValue else formattedValue

@Composable
internal fun Money.displayValue(): String = moneyDisplayValue(
    hidden = LocalMoneyValuesHidden.current,
    formattedValue = formatted(),
    hiddenValue = stringResource(R.string.money_hidden_value),
)
