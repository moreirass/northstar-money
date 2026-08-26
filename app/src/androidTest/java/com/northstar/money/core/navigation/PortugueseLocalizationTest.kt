package com.northstar.money.core.navigation

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.northstar.money.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PortugueseLocalizationTest {
    @Test
    fun portugueseLocaleLoadsTranslatedAndFormattedResources() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("pt-PT"))
        }
        val portugueseContext = context.createConfigurationContext(configuration)

        assertEquals("Início", portugueseContext.getString(R.string.nav_home))
        assertEquals(
            "Previsão: 125,00 €",
            portugueseContext.getString(R.string.forecast_projected, "125,00 €"),
        )
        assertEquals(
            "Não foi possível concluir a operação. Tente novamente.",
            portugueseContext.getString(R.string.operation_failed_generic),
        )
    }
}
