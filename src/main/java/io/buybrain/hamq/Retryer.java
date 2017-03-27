package io.buybrain.hamq;

import com.rabbitmq.client.ShutdownSignalException;
import io.buybrain.util.function.ThrowingRunnable;
import io.buybrain.util.function.ThrowingSupplier;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.net.SocketException;

import static io.buybrain.util.Exceptions.rethrow;
import static io.buybrain.util.Exceptions.rethrowR;

/**
 * Utility for retrying operations until they succeed while applying exponential backoff between attempts
 */
@Slf4j
class Retryer {
    static void performWithRetry(@NonNull ThrowingRunnable operation, @NonNull RetryPolicy policy) {
        val state = new RetryState();
        rethrow(() -> {
            while (true) {
                try {
                    operation.run();
                    return;
                } catch (Exception ex) {
                    handleError(ex, policy, state);
                }
            }
        });
    }

    static <T> T performWithRetry(@NonNull ThrowingSupplier<T> operation, @NonNull RetryPolicy policy) {
        val state = new RetryState();
        return rethrowR(() -> {
            while (true) {
                try {
                    return operation.get();
                } catch (Exception ex) {
                    handleError(ex, policy, state);
                }
            }
        });
    }

    private static void handleError(Exception ex, RetryPolicy policy, RetryState state) throws Exception {
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
        } else {
            throw ex;
        }
    }

    private static boolean isRetryable(Throwable ex, RetryPolicy policy) {
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
