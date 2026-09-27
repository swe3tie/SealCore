package sealmc.swe3tie.sealcore.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Awaits a {@link CompletableFuture} the provider returned.
 *
 * <p>Every asynchronous call bottoms out in a future completed by another
 * thread, so blocking an async worker is cheap. Blocking a region thread is not:
 * that is what stalls a Folia server. Call {@link #await(CompletableFuture)} from
 * the async scheduler only, never from a command or a tick.
 */
public final class Async {

    private static final long DEFAULT_TIMEOUT_MILLIS = 10_000L;

    private Async() {
    }

    public static <T> T await(CompletableFuture<T> future) {
        return await(future, DEFAULT_TIMEOUT_MILLIS);
    }

    public static <T> T await(CompletableFuture<T> future, long timeoutMillis) {
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException expired) {
            throw new IllegalStateException("Asynchronous call did not finish within " + timeoutMillis + "ms");
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause() == null ? failed : failed.getCause();
            throw new IllegalStateException("Asynchronous call failed: " + cause.getMessage(), cause);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for an asynchronous call");
        }
    }
}
