package io.buybrain.hamq;

import com.rabbitmq.client.ShutdownSignalException;
import io.buybrain.util.Result;
import io.buybrain.util.function.ThrowingRunnable;
import io.buybrain.util.function.ThrowingSupplier;
import io.buybrain.util.time.Clock;
import io.buybrain.util.time.SystemClock;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.net.SocketException;

import static io.buybrain.util.Result.err;
import static io.buybrain.util.Result.trying;

/**
 * Utility for retrying operations until they succeed while applying exponential backoff between attempts
 */
@Slf4j
class Retryer {
    @Setter private Clock clock = SystemClock.get();
    
    void performWithRetry(@NonNull ThrowingRunnable operation, @NonNull RetryPolicy policy) {
        val state = new RetryState();
        while (true) {
            if (trying(operation).mapErr(ex -> handleError(ex, policy, state)).isOk()) {
                return;
            }
        }
    }

    <T> T performWithRetry(@NonNull ThrowingSupplier<T> operation, @NonNull RetryPolicy policy) {
        val state = new RetryState();
        while (true) {
            Result<T, ?> result = trying(operation).mapErr(ex -> handleError(ex, policy, state));
            if (result.isOk()) {
                return result.getUnsafe();
            }
        }
    }

    private Result handleError(Throwable ex, RetryPolicy policy, RetryState state) throws Throwable {
        if (isRetryable(ex, policy)) {
            log.warn("Encountered error, will retry later", ex);
            if (policy.getErrorHandler() != null) {
                policy.getErrorHandler().accept(ex);
            }
            if (state.delayMillis == 0) {
                state.delayMillis = policy.getInitialDelay().toMillis();
            }
            
            Thread.sleep(state.delayMillis);
            state.delayMillis = Math.min(
                (long) (state.delayMillis * policy.getDelayMultiplier()),
                policy.getMaxDelay().toMillis()
            );
            return err(ex);
        } else {
            throw ex;
        }
    }

    private boolean isRetryable(Throwable ex, RetryPolicy policy) {
        return policy.isRetryAll() || isNetworkError(ex);
    }

    public static boolean isNetworkError(@NonNull Throwable ex) {
        if (ex instanceof SocketException) {
            return true;
        }
        if (ex instanceof ShutdownSignalException) {
            return ((ShutdownSignalException) ex).isHardError();
        }
        if (ex.getCause() != null && ex.getCause() != ex) {
            return isNetworkError(ex.getCause());
        }
        return false;
    }

    public static boolean shouldReconnectToRecover(@NonNull Throwable ex) {
        if (isNetworkError(ex)) {
            return true;
        }
        if (ex instanceof ShutdownSignalException) {
            return true;
        }
        if (ex.getCause() != null && ex.getCause() != ex) {
            return shouldReconnectToRecover(ex.getCause());
        }
        return false;
    }

    private static class RetryState {
        long delayMillis;
    }
}
