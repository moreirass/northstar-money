package com.northstar.money.data.repository

import com.google.common.util.concurrent.ListenableFuture

import com.northstar.money.core.database.copy
import com.northstar.money.core.database.DatabaseSnapshot
import com.northstar.money.core.database.GoalContributionEntity
import com.northstar.money.core.database.SuspendingFinanceDao
import com.northstar.money.domain.model.Money
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.data.backup.FullBackupJsonCodec
import com.northstar.money.data.backup.DatabaseSnapshotValidator
import com.northstar.money.data.backup.PortableBackupCodec
import com.northstar.money.data.backup.RestoreRecoveryStore
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.northstar.money.domain.repository.BackupsRepository

internal class OfflineBackupsRepository(
    private val dao: SuspendingFinanceDao,
    private val backupCodec: FullBackupJsonCodec,
    private val baseCurrencyCode: Flow<String>,
    private val portableBackupCodec: PortableBackupCodec,
    private val restoreRecoveryStore: RestoreRecoveryStore?,
    private val snapshotValidator: DatabaseSnapshotValidator,
    private val scheduleRateRefresh: (() -> Unit)?,
) : BackupsRepository {
    private val restoreMutex = Mutex()

    override fun createFullBackup(): ListenableFuture<String> = RepositoryTasks.read {
        backupCodec.encode(
            dao.exportSnapshot(), NorthstarDatabase.VERSION, System.currentTimeMillis(), baseCurrencyCode.first(),
        )
    }

    override fun writeFullBackup(output: java.io.OutputStream, password: CharArray): ListenableFuture<Void?> = RepositoryTasks.write {
        val snapshot = dao.exportSnapshot()
        val currency = baseCurrencyCode.first()
        withContext(Dispatchers.IO) {
            portableBackupCodec.encryptTo(output, password) { plaintext ->
                backupCodec.encodeTo(plaintext, snapshot, NorthstarDatabase.VERSION, currency)
            }
        }
    }

    override fun restoreFullBackup(backup: String, recoveryPassword: CharArray): ListenableFuture<Void?> = RepositoryTasks.write {
        withContext(Dispatchers.IO) {
            restoreMutex.withLock { restoreFullBackupLocked(backup, recoveryPassword) }
        }
    }

    override fun undoLastFullRestore(recoveryPassword: CharArray): ListenableFuture<Void?> = RepositoryTasks.write {
        withContext(Dispatchers.IO) {
            restoreMutex.withLock {
                val recoveryStore = requireNotNull(restoreRecoveryStore) { "Restore recovery storage is unavailable" }
                val encryptedRecovery = requireNotNull(recoveryStore.load()) { "No restore recovery is available" }
                val recoveryDocument = portableBackupCodec.decrypt(encryptedRecovery, recoveryPassword)
                restoreFullBackupLocked(recoveryDocument, recoveryPassword)
            }
        }
    }

    private suspend fun restoreFullBackupLocked(backup: String, recoveryPassword: CharArray) {
        val document = backupCodec.decode(backup)
        require(document.databaseVersion <= NorthstarDatabase.VERSION) {
            "Backup requires a newer Northstar Money database version"
        }
        val rawSnapshot = if (document.databaseVersion < 10) normalizeLegacySnapshot(document.toSnapshot()) else document.toSnapshot()
        val snapshot = normalizeLegacyGoalSavings(if (document.databaseVersion < 10) rawSnapshot.copy(
            budgetAllocations = rawSnapshot.budgetAllocations.map { it.withCurrency(document.baseCurrencyCode ?: baseCurrencyCode.first()) },
        ) else rawSnapshot)
        snapshotValidator.validate(snapshot)

        val recoveryStore = requireNotNull(restoreRecoveryStore) { "Restore recovery storage is unavailable" }
        dao.replaceWithRecovery(snapshot) { previous ->
            val currency = baseCurrencyCode.first()
            withContext(Dispatchers.IO) {
                recoveryStore.write { output -> portableBackupCodec.encryptTo(output, recoveryPassword) { plaintext ->
                    backupCodec.encodeTo(plaintext, previous, NorthstarDatabase.VERSION, currency)
                } }
            }
        }
        scheduleRateRefresh?.invoke()
    }

    private fun normalizeLegacyGoalSavings(snapshot: DatabaseSnapshot): DatabaseSnapshot {
        val goalsWithSavedAmount = snapshot.goals.filter { it.savedMinor > 0 }
        if (goalsWithSavedAmount.isEmpty()) return snapshot

        val contributionIds = snapshot.goalContributions.mapTo(mutableSetOf()) { it.id }
        val migratedContributions = goalsWithSavedAmount.map { goal ->
            val contributionId = "legacy-${goal.id}"
            require(contributionIds.add(contributionId)) {
                "Backup contains a conflicting legacy goal contribution"
            }
            GoalContributionEntity(
                contributionId,
                goal.id,
                goal.savedMinor,
                Instant.ofEpochMilli(goal.createdAt).atZone(ZoneOffset.UTC).toLocalDate().toString(),
                "Opening saved amount",
                goal.createdAt,
                goal.createdAt,
            )
        }
        return snapshot.copy(
            goals = snapshot.goals.map { it.withSavedMinor(0) },
            goalContributions = snapshot.goalContributions + migratedContributions,
        )
    }

    private fun normalizeLegacySnapshot(snapshot: DatabaseSnapshot): DatabaseSnapshot {
        require(snapshot.categories.size <= 10_000) { "Backup contains too many categories" }
        val categories = snapshot.categories.associateBy { it.id }
        val entries = snapshot.transactionEntries.associateBy { it.id }
        val transactions = snapshot.transactions.associateBy { it.id }
        return snapshot.copy(
            categories = snapshot.categories.map { category ->
                var target = category.mergedIntoCategoryId
                val visited = mutableSetOf(category.id)
                while (target != null) {
                    require(visited.size < 100) { "Backup category merge chain is too long" }
                    require(visited.add(target)) { "Backup contains a category merge cycle" }
                    val next = requireNotNull(categories[target]) { "Backup category merge target is missing" }
                    require(next.kind == category.kind) { "Backup category merge types differ" }
                    if (next.mergedIntoCategoryId == null) {
                        if (next.archivedAt != null) target = null
                        break
                    }
                    target = next.mergedIntoCategoryId
                }
                category.withMergeTarget(target)
            },
            transactionExchangeRates = snapshot.transactionExchangeRates.map { rate ->
                val entry = entries[rate.entryId]
                val transaction = entry?.let { transactions[it.transactionId] }
                if (entry != null && transaction != null && (rate.transactionId != transaction.id ||
                    rate.rateLocalDate != transaction.localDate || rate.baseCurrencyCode != entry.currencyCode)) {
                    rate.asPending(transaction.id, entry.currencyCode, transaction.localDate)
                } else rate
            },
        )
    }
}
