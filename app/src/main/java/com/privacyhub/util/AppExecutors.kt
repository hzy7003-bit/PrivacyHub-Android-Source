package com.privacyhub.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask

object AppExecutors {
    private val diskExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val resultDispatcher = ResultTaskDispatcher(
        workerExecutor = diskExecutor,
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

    fun <T> runDiskBlocking(task: () -> T): T {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return task()
        }
        val future = FutureTask(Callable { task() })
        diskExecutor.execute(future)
        return future.get()
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
