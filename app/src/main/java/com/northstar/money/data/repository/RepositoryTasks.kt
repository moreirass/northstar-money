package com.northstar.money.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.Flow
import androidx.concurrent.futures.CallbackToFutureAdapter
import kotlinx.coroutines.future.future

/** As operações partilham executores da aplicação; cada futuro pode ser cancelado separadamente. */
internal object RepositoryTasks {
    private val dispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()

    fun <T> read(operation: suspend () -> T): ListenableFuture<T> =
        CallbackToFutureAdapter.getFuture { completer ->
            val task = CoroutineScope(dispatcher).future { operation() }
            completer.addCancellationListener({ task.cancel(false) }, Runnable::run)
            task.whenComplete { value, error ->
                if (error == null) completer.set(value) else completer.setException(error)
            }
            "RepositoryTasks"
        }

    fun write(operation: suspend () -> Unit): ListenableFuture<Void?> =
        read {
            operation()
            null
        }

    fun <T> observe(source: () -> Flow<T>): LiveData<T> =
        RepositoryObservation(source().flowOn(dispatcher))
}

private class RepositoryObservation<T>(val flow: Flow<T>) : LiveData<T>() {
    private val live = flow.asLiveData()
    private val observer = Observer<T> { value = it }

    override fun onActive() = live.observeForever(observer)
    override fun onInactive() = live.removeObserver(observer)
}

internal fun <T> LiveData<T>.repositoryFlow(): Flow<T> =
    if (this is RepositoryObservation<T>) flow else asFlow()
