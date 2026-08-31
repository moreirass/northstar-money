package com.northstar.money.core.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.northstar.money.R

private val MoreBackground = Color(0xFF08080A)
private val MoreSurface = Color(0xFF121215)
private val MoreBorder = Color(0xFF24242B)
private val MorePrimary = Color.White
private val MoreSecondary = Color(0xFF8E8E9F)
private val MoreAccent = Color(0xFF10B981)

internal enum class MoreHubTarget(@StringRes val titleRes: Int) {
    SETTINGS(R.string.more_settings),
    CALENDAR(R.string.more_calendar),
    ACTIVITY_LOG(R.string.more_activity_log),
    SCHEDULED(R.string.more_scheduled),
    SUBSCRIPTIONS(R.string.more_subscriptions),
    LOANS(R.string.more_loans),
    CATEGORIES(R.string.more_categories),
    EXPORTS(R.string.more_exports),
}

private data class MoreHubItem(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val target: MoreHubTarget? = null,
    val specialAction: MoreSpecialAction? = null,
)

private enum class MoreSpecialAction { ABOUT, HELP }

@Composable
internal fun MoreHubScreen(
    padding: PaddingValues,
    onOpen: (MoreHubTarget) -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MoreBackground).padding(padding),
        contentPadding = PaddingValues(top = 18.dp, start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.more_title), color = MorePrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(stringResource(R.string.more_subtitle), color = MoreSecondary, fontSize = 13.sp)
            }
        }
        item {
            MoreHubGroup(
                title = stringResource(R.string.more_general),
                items = listOf(
                    MoreHubItem(R.string.more_settings, Icons.Default.Settings, MoreHubTarget.SETTINGS),
                    MoreHubItem(R.string.more_about, Icons.Default.Info, specialAction = MoreSpecialAction.ABOUT),
                    MoreHubItem(R.string.more_help, Icons.Default.HelpOutline, specialAction = MoreSpecialAction.HELP),
                ),
                onOpen = onOpen,
                onAbout = onAbout,
                onHelp = onHelp,
            )
        }
        item {
            MoreHubGroup(
                title = stringResource(R.string.more_financial),
                items = listOf(
                    MoreHubItem(R.string.more_calendar, Icons.Default.CalendarMonth, MoreHubTarget.CALENDAR),
                    MoreHubItem(R.string.more_activity_log, Icons.Default.ListAlt, MoreHubTarget.ACTIVITY_LOG),
                    MoreHubItem(R.string.more_scheduled, Icons.Default.Schedule, MoreHubTarget.SCHEDULED),
                    MoreHubItem(R.string.more_subscriptions, Icons.Default.CreditCard, MoreHubTarget.SUBSCRIPTIONS),
                    MoreHubItem(R.string.more_loans, Icons.Default.AttachMoney, MoreHubTarget.LOANS),
                ),
                onOpen = onOpen,
                onAbout = onAbout,
                onHelp = onHelp,
            )
        }
        item {
            MoreHubGroup(
                title = stringResource(R.string.more_data),
                items = listOf(
                    MoreHubItem(R.string.more_categories, Icons.Default.Category, MoreHubTarget.CATEGORIES),
                    MoreHubItem(R.string.more_exports, Icons.Default.Share, MoreHubTarget.EXPORTS),
                ),
                onOpen = onOpen,
                onAbout = onAbout,
                onHelp = onHelp,
            )
        }
    }
}

@Composable
private fun MoreHubGroup(
    title: String,
    items: List<MoreHubItem>,
    onOpen: (MoreHubTarget) -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = MoreSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { item ->
                MoreHubRow(
                    label = stringResource(item.labelRes),
                    icon = item.icon,
                    onClick = {
                        when (item.specialAction) {
                            MoreSpecialAction.ABOUT -> onAbout()
                            MoreSpecialAction.HELP -> onHelp()
                            null -> item.target?.let(onOpen)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MoreHubRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(1.dp, MoreBorder, RoundedCornerShape(12.dp))
            .background(MoreSurface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MoreAccent, modifier = Modifier.size(20.dp))
            Text(label, color = MorePrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MoreSecondary, modifier = Modifier.size(14.dp))
    }
}

@Composable
internal fun MoreManagementHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(36.dp).border(1.dp, MoreBorder, CircleShape).background(MoreSurface, CircleShape),
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.more_back), tint = MoreAccent)
        }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}
