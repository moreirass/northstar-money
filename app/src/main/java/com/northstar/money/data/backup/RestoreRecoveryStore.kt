package com.northstar.money.data.backup

import android.content.Context
import android.util.AtomicFile
import java.io.File

interface RestoreRecoveryStore {
    fun save(payload: ByteArray)
    fun load(): ByteArray?
}

class FileRestoreRecoveryStore(context: Context) : RestoreRecoveryStore {
    private val atomicFile = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    override fun save(payload: ByteArray) {
        val output = atomicFile.startWrite()
        try {
            output.write(payload)
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    override fun load(): ByteArray? = if (atomicFile.baseFile.exists()) atomicFile.readFully() else null

    companion object {
        private const val FILE_NAME = "last-restore-recovery.nsmb"
    }
}
