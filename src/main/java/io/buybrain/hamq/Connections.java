package io.buybrain.hamq;

import lombok.NonNull;

/**
 * Static factory for creating HAmq connections
 */
public class Connections {
    /**
     * Create a HAmq connection with the default backend implementation (real AMQP)
     *
     * @param config configuration for connecting and retrying
     * @return the new connection
     */
    public static Connection create(@NonNull Config config) {
        return new Connection(config, new AMQPBackend());
    }
}
