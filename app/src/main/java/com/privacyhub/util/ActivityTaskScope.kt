package com.privacyhub.util

import android.app.Activity
import java.lang.ref.WeakReference

/** Lifecycle and stale-result guard for work whose UI owner is an Activity. */
class ActivityTaskScope<T : Activity>(owner: T) {
    private val owner = WeakReference(owner)
    private val gate = GenerationTaskGate()

    fun nextGeneration(): Long = gate.next()

    fun invalidate() {
        gate.invalidate()
    }

    fun close() {
        gate.close()
    }

    fun <R> executeDisk(
        generation: Long,
        task: () -> R,
        onResult: (T, Result<R>) -> Unit,
        onDiscard: (Result<R>) -> Unit = {}
    ) {
        AppExecutors.executeDiskResult(task) { result -> deliver(generation, result, onResult, onDiscard) }
    }

    fun <R> executeBackupIo(
        generation: Long,
        task: () -> R,
        onResult: (T, Result<R>) -> Unit,
        onDiscard: (Result<R>) -> Unit = {}
    ) {
        AppExecutors.executeBackupIoResult(task) { result -> deliver(generation, result, onResult, onDiscard) }
    }

    private fun <R> deliver(
        generation: Long,
        result: Result<R>,
        onResult: (T, Result<R>) -> Unit,
        onDiscard: (Result<R>) -> Unit
    ) {
        val activity = owner.get()
        if (activity == null || !gate.isCurrent(generation) || activity.isFinishing || activity.isDestroyed) {
            onDiscard(result)
            return
        }
        onResult(activity, result)
    }
}

/** Small, thread-safe token source shared by ActivityTaskScope and plain unit tests. */
class GenerationTaskGate {
    private var generation = 0L
    private var closed = false

    @Synchronized
    fun next(): Long {
        if (closed) return generation
        generation += 1L
        return generation
    }

    @Synchronized
    fun invalidate() {
        generation += 1L
    }

    @Synchronized
    fun close() {
        closed = true
        generation += 1L
    }

    @Synchronized
    fun isCurrent(token: Long): Boolean = !closed && token == generation
}

/** Guards a one-at-a-time UI operation such as save, delete, import, or restore. */
class SingleFlightGate {
    private var acquired = false

    @Synchronized
    fun tryAcquire(): Boolean {
        if (acquired) return false
        acquired = true
        return true
    }

    @Synchronized
    fun release() {
        acquired = false
    }

    @Synchronized
    fun isAcquired(): Boolean = acquired
}
