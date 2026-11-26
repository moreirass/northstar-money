package com.northstar.money.data.repository

import androidx.lifecycle.LiveData
import com.google.common.util.concurrent.ListenableFuture

import com.northstar.money.core.database.SuspendingFinanceDao
import com.northstar.money.domain.model.Account
import com.northstar.money.domain.model.AccountType
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.EditableAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.northstar.money.domain.repository.AccountsRepository

import androidx.concurrent.futures.await

internal class OfflineAccountsRepository(
    private val dao: SuspendingFinanceDao,
    private val baseCurrencyCode: Flow<String>,
    private val scheduleRateRefresh: (() -> Unit)?,
) : AccountsRepository {
    private val accounts = AccountOperations(dao.executor)

    override fun observeAccounts(): LiveData<List<Account>> = RepositoryTasks.observe {
        dao.observeAccounts().map { rows ->
            rows.map {
                Account(
                    it.id, it.name, AccountType.valueOf(it.type), it.currencyCode,
                    Money(it.balanceMinor, it.currencyCode),
                    Money(it.clearedBalanceMinor, it.currencyCode),
                )
            }
        }
    }

    override fun observeArchivedAccounts(): LiveData<List<Account>> = RepositoryTasks.observe {
        dao.observeArchivedAccounts().map { rows ->
            rows.map {
                Account(
                    it.id, it.name, AccountType.valueOf(it.type), it.currencyCode,
                    Money(it.balanceMinor, it.currencyCode),
                    Money(it.clearedBalanceMinor, it.currencyCode),
                )
            }
        }
    }

    override fun seedIfEmpty(): ListenableFuture<Void?> = RepositoryTasks.write {
        accounts.seed(baseCurrencyCode.first()).await()
    }

    override fun createAccount(name: String, type: AccountType, openingBalance: Money): ListenableFuture<Void?> = RepositoryTasks.write {
        require(name.isNotBlank())
        accounts.create(name.trim(), type, openingBalance).await()
        scheduleRateRefresh?.invoke()
    }

    override fun configureInitialAccount(name: String, openingBalance: Money): ListenableFuture<Void?> = RepositoryTasks.write {
        require(name.isNotBlank()) { "Account name is required" }
        if (accounts.configureInitial(name.trim(), openingBalance).await()) scheduleRateRefresh?.invoke()
    }

    override fun getAccountForEdit(id: String): ListenableFuture<EditableAccount> = RepositoryTasks.read {
        accounts.forEdit(id).await()
    }

    override fun updateAccount(account: EditableAccount): ListenableFuture<Void?> = RepositoryTasks.write {
        require(account.name.isNotBlank()) { "Account name is required" }
        accounts.update(account, account.name.trim()).await()
        scheduleRateRefresh?.invoke()
    }

    override fun archiveAccount(id: String): ListenableFuture<Void?> = RepositoryTasks.write { accounts.archive(id).await()
    }

    override fun restoreAccount(id: String): ListenableFuture<Void?> = RepositoryTasks.write { accounts.restore(id).await()
    }

}
