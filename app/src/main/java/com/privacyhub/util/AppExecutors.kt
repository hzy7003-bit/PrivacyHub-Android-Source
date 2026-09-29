package com.privacyhub.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object AppExecutors {
    private val diskExecutor = Executors.newSingleThreadExecutor()
    /** Keeps long backup encryption/file operations from queueing behind ordinary database work. */
    private val backupIoExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val resultDispatcher = ResultTaskDispatcher(
        workerExecutor = diskExecutor,
        callbackExecutor = Executor { task -> mainHandler.post(task) }
    )
    private val backupIoResultDispatcher = ResultTaskDispatcher(
        workerExecutor = backupIoExecutor,
        callbackExecutor = Executor { task -> mainHandler.post(task) }
    )

    fun executeDisk(task: () -> Unit) {
        diskExecutor.execute(task)
    }

    fun executeMain(task: () -> Unit) {
        mainHandler.post(task)
    }

    fun <T> executeDiskResult(task: () -> T, onResult: (Result<T>) -> Unit) {
        resultDispatcher.execute(task, onResult)
    }

    fun executeBackupIo(task: () -> Unit) {
        backupIoExecutor.execute(task)
    }

    fun <T> executeBackupIoResult(task: () -> T, onResult: (Result<T>) -> Unit) {
        backupIoResultDispatcher.execute(task, onResult)
    }
}

/** Keeps background work and its main-thread completion callback ordered and testable. */
internal class ResultTaskDispatcher(
    private val workerExecutor: Executor,
    private val callbackExecutor: Executor
) {
    fun <T> execute(task: () -> T, onResult: (Result<T>) -> Unit) {
        workerExecutor.execute {
            val result = runCatching(task)
            callbackExecutor.execute { onResult(result) }
        }
    }
}
