package io.buybrain.hamq;

import io.buybrain.util.time.Clock;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import static io.buybrain.util.Result.trying;

/**
 * HAmq connection representation
 */
@RequiredArgsConstructor
public class Connection {
    @NonNull private final Config config;
    @NonNull private final Backend backend;
    private BackendConnection connection;
    private Retryer retryer = new Retryer();

    /**
     * Set the clock implementation for this connection. Useful for testing.
     * 
     * @param clock the new clock
     */
    public void setClock(@NonNull Clock clock) {
        retryer.setClock(clock);
    }

    /**
     * Create a new AMQP channel.
     *
     * @return the channel
     */
    public Channel createChannel() {
        return new Channel(this, retryer);
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
            connection = retryer.performWithRetry(
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
            trying(connection::close);
        }
        connection = null;
    }
}
