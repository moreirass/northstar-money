package com.northstar.money.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.northstar.money.MainActivity
import com.northstar.money.NorthstarApplication
import com.northstar.money.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FinanceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { update(context, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FinanceWidgetProvider::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val application = context.applicationContext as NorthstarApplication
            val repository = application.financeRepository
            val summary = repository.observeSummary().first()
            val transactions = repository.observeTransactions().first().take(3)
            val hidden = application.userPreferences.settings.first().moneyValuesHidden
            val amountText = if (hidden) context.getString(R.string.money_hidden_value) else summary.balance.formatted()
            val recentText = if (transactions.isEmpty()) context.getString(R.string.widget_no_transactions) else
                transactions.joinToString("\n") { item ->
                    val amount = if (hidden) context.getString(R.string.money_hidden_value) else item.amount.formatted()
                    "${item.payee} · $amount"
                }
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.finance_widget).apply {
                    setTextViewText(R.id.widget_balance, amountText)
                    setTextViewText(R.id.widget_transactions, recentText)
                    setOnClickPendingIntent(R.id.widget_root, openApp)
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}
