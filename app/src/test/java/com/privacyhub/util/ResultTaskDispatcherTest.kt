package com.privacyhub.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class ResultTaskDispatcherTest {
    @Test
    fun workRunsOffCallerAndCompletionUsesTheCallbackExecutor() {
        val worker = Executors.newSingleThreadExecutor()
        val callback = Executors.newSingleThreadExecutor()
        try {
            val dispatcher = ResultTaskDispatcher(worker, callback)
            val caller = Thread.currentThread()
            val taskThread = arrayOfNulls<Thread>(1)
            val callbackThread = arrayOfNulls<Thread>(1)
            val result = arrayOfNulls<Result<Int>>(1)
            val finished = CountDownLatch(1)

            dispatcher.execute({
                taskThread[0] = Thread.currentThread()
                7
            }) {
                callbackThread[0] = Thread.currentThread()
                result[0] = it
                finished.countDown()
            }

            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertNotEquals(caller, taskThread[0])
            assertNotEquals(taskThread[0], callbackThread[0])
            assertEquals(Result.success(7), result[0])
        } finally {
            worker.shutdownNow()
            callback.shutdownNow()
        }
    }

    @Test
    fun taskFailureIsDeliveredWithoutEscapingWorker() {
        val worker = Executors.newSingleThreadExecutor()
        val callback = Executors.newSingleThreadExecutor()
        try {
            val dispatcher = ResultTaskDispatcher(worker, callback)
            val failure = CountDownLatch(1)
            var received: Throwable? = null
            dispatcher.execute<Int>({ error("disk failure") }) {
                received = it.exceptionOrNull()
                failure.countDown()
            }
            assertTrue(failure.await(2, TimeUnit.SECONDS))
            assertEquals("disk failure", received?.message)
        } finally {
            worker.shutdownNow()
            callback.shutdownNow()
        }
    }

    @Test
    fun submittingSlowDiskWorkDoesNotWaitForIt() {
        val worker = Executors.newSingleThreadExecutor()
        val callback = Executors.newSingleThreadExecutor()
        try {
            val dispatcher = ResultTaskDispatcher(worker, callback)
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val completed = CountDownLatch(1)
            val elapsedMillis = AtomicLong()

            val startedAt = System.nanoTime()
            dispatcher.execute({
                started.countDown()
                release.await(2, TimeUnit.SECONDS)
                1
            }) { completed.countDown() }
            elapsedMillis.set((System.nanoTime() - startedAt) / 1_000_000)

            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertTrue(elapsedMillis.get() < 100, "Submitting disk work must return without waiting")
            release.countDown()
            assertTrue(completed.await(2, TimeUnit.SECONDS))
        } finally {
            worker.shutdownNow()
            callback.shutdownNow()
        }
    }
}
