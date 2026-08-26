package com.northstar.money.feature.finance

import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OperationFailureReporterTest {
    @Test
    fun failure_isReportedWithOperationContext() = runBlocking {
        val messages = mutableListOf<String>()

        reportOperationFailure("save the transaction", messages::add) {
            throw IllegalStateException("database unavailable")
        }

        assertEquals(listOf("Could not save the transaction. Please try again."), messages)
    }

    @Test
    fun validationFailure_keepsActionableReason() = runBlocking {
        val messages = mutableListOf<String>()

        reportOperationFailure("create the account", messages::add) {
            throw IllegalArgumentException("Name is required")
        }

        assertEquals(listOf("Could not create the account. Name is required."), messages)
    }

    @Test
    fun cancellation_isRethrownAndNeverReported() {
        val messages = mutableListOf<String>()

        assertThrows(CancellationException::class.java) {
            runBlocking {
                reportOperationFailure("save the budget", messages::add) {
                    throw CancellationException("view model cleared")
                }
            }
        }
        assertEquals(emptyList<String>(), messages)
    }
}
