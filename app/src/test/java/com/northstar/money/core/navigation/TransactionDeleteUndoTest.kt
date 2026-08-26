package com.northstar.money.core.navigation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionDeleteUndoTest {
    @Test
    fun undoIsOfferedOnlyAfterDeletionAndRestoresTheSameTransaction() = runBlocking {
        val events = mutableListOf<String>()

        val outcome = performTransactionDeleteWithUndo(
            delete = { events += "deleted:transaction" },
            offerUndo = {
                events += "undo offered"
                true
            },
            restore = { events += "restored:transaction" },
        )

        assertEquals(DeleteUndoOutcome.RESTORED, outcome)
        assertEquals(
            listOf("deleted:transaction", "undo offered", "restored:transaction"),
            events,
        )
    }

    @Test
    fun dismissedUndoLeavesRecoverableDeletionInPlace() = runBlocking {
        var restored = false

        val outcome = performTransactionDeleteWithUndo(
            delete = {},
            offerUndo = { false },
            restore = { restored = true },
        )

        assertEquals(DeleteUndoOutcome.DELETED, outcome)
        assertEquals(false, restored)
    }

    @Test
    fun failedRestoreIsReportedWithoutPretendingDeletionFailed() = runBlocking {
        val outcome = performTransactionDeleteWithUndo(
            delete = {},
            offerUndo = { true },
            restore = { error("restore failed") },
        )

        assertEquals(DeleteUndoOutcome.RESTORE_FAILED, outcome)
    }
}
