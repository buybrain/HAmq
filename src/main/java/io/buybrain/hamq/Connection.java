package io.buybrain.hamq;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import static io.buybrain.hamq.Retryer.performWithRetry;

/**
 * HAmq connection representation
 */
@RequiredArgsConstructor
public class Connection {
    @NonNull private final Config config;
    @NonNull private final Backend backend;
    private BackendConnection connection;

    /**
     * Create a new AMQP channel.
     *
     * @return the channel
     */
    public Channel createChannel() {
        return new Channel(this);
    }

    /**
     * Get the retry policy as defined in the given connection configuration.
     *
     * @return the default retry policy
     */
    public RetryPolicy getRetryPolicy() {
        return config.getRetryPolicy();
    }

    /**
     * Get the currently active connection implementation.
     *
     * @return the actual implementation of an AMQP connection
     */
    synchronized BackendConnection activeConnection() {
        if (connection == null) {
            connection = performWithRetry(
                () -> backend.newConnection(config),
                new RetryPolicy().withRetryAll(true)
            );
        }
        return connection;
    }

    /**
     * Close and remove the currently active connection implementation.
     * This will force a new connection to be created the next time {@link #activeConnection()} is called.
     */
    synchronized void reset() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
        connection = null;
    }
}
