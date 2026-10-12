package com.northstar.money.domain.model


/** Return null rather than interpreting a quote in the wrong currency or date. */
fun TransactionItem.convertedTo(currency: String, rates: List<HistoricalExchangeRate>): Money? {
    if (amount.currencyCode == currency) return amount
    val rate = rates.firstOrNull {
        it.transactionId == id && it.baseCurrencyCode == amount.currencyCode &&
            it.quoteCurrencyCode == currency && it.status == "AVAILABLE" && it.rateLocalDate == localDate
    } ?: return null
    return rate.rateMicros?.let { Money(convertCurrencyMinor(amount.minor, amount.currencyCode, currency, it), currency) }
}
