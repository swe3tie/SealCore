package sealmc.swe3tie.sealcore.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Runs a suspending block to completion on the calling thread.
 *
 * Only ever call this from the async scheduler: every suspending function in
 * SealCore bottoms out in a CompletableFuture, so blocking an async worker is
 * cheap, whereas blocking a region thread would stall the server.
 */
fun <T> runSuspendBlocking(timeoutMillis: Long = 10_000L, block: suspend () -> T): T {
    val latch = CountDownLatch(1)
    var outcome: Result<T>? = null

    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(result: Result<T>) {
            outcome = result
            latch.countDown()
        }
    })

    if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
        throw IllegalStateException("Suspending call did not finish within ${timeoutMillis}ms")
    }
    return outcome!!.getOrThrow()
}
