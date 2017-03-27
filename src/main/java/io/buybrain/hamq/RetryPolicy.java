package io.buybrain.hamq;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Wither;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Specification of delays, multipliers and other settings for {@link Retryer}
 */
@Value
@Wither
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RetryPolicy {
    boolean retryAll;
    Duration initialDelay;
    Duration maxDelay;
    double delayMultiplier;
    @Wither(AccessLevel.PACKAGE) Consumer<Throwable> errorHandler;

    public RetryPolicy() {
        retryAll = false;
        errorHandler = null;
        initialDelay = Duration.ofSeconds(1);
        maxDelay = Duration.ofSeconds(30);
        delayMultiplier = 1.5;
    }
}
