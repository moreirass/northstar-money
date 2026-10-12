package com.northstar.money.data.backup

import android.content.Context
import android.util.AtomicFile
import java.io.File
import com.northstar.money.data.importing.readBounded
import com.northstar.money.data.importing.ImportLimits

interface RestoreRecoveryStore {
    fun write(producer: (java.io.OutputStream) -> Unit) {
        java.io.ByteArrayOutputStream().use { output -> producer(output); save(output.toByteArray()) }
    }
    fun save(payload: ByteArray)
    fun load(): ByteArray?
}

class FileRestoreRecoveryStore(context: Context) : RestoreRecoveryStore {
    private val atomicFile = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    override fun save(payload: ByteArray) = write { it.write(payload) }

    override fun write(producer: (java.io.OutputStream) -> Unit) {
        val output = atomicFile.startWrite()
        try {
            val shield = object : java.io.FilterOutputStream(output) {
                override fun close() { flush() }
                override fun write(bytes: ByteArray, offset: Int, length: Int) { out.write(bytes, offset, length) }
            }
            producer(shield)
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    override fun load(): ByteArray? = try {
        atomicFile.openRead().use { it.readBounded(ImportLimits.BACKUP_BYTES) }
    } catch (_: java.io.FileNotFoundException) { null }

    companion object {
        private const val FILE_NAME = "last-restore-recovery.nsmb"
    }
}
