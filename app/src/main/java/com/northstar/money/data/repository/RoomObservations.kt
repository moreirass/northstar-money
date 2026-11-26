package com.northstar.money.data.repository

import androidx.lifecycle.LiveData
import java.util.function.Function
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal object RoomObservations {
    @JvmStatic
    fun <T, R> rows(source: Flow<List<T>>, mapper: Function<T, R>): LiveData<List<R>> =
        RepositoryTasks.observe { source.map { rows -> rows.map { mapper.apply(it) } } }
}
