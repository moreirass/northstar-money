package com.northstar.money.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.northstar.money.R

internal enum class FinanceContentState { LOADING, ERROR, CONTENT }

internal fun financeContentState(isLoading: Boolean, loadFailed: Boolean): FinanceContentState = when {
    isLoading -> FinanceContentState.LOADING
    loadFailed -> FinanceContentState.ERROR
    else -> FinanceContentState.CONTENT
}

@Composable
internal fun FinanceScreenState(
    isLoading: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    when (financeContentState(isLoading, loadFailed)) {
        FinanceContentState.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.state_loading))
            }
        }
        FinanceContentState.ERROR -> Box(
            Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(R.string.state_error_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.state_error_body))
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.state_retry))
                    }
                }
            }
        }
        FinanceContentState.CONTENT -> content()
    }
}

@Composable
internal fun EmptyStateCard(message: String, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
