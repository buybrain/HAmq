package io.buybrain.hamq;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

/**
 * AMQP backend connection based on the RabbitMQ client library
 */
@RequiredArgsConstructor
class AMQPBackendConnection implements BackendConnection {
    @NonNull com.rabbitmq.client.Connection connection;

    /**
     * Create a new channel
     *
     * @return the new channel
     * @throws IOException when an error occured during channel creation
     */
    @Override
    public BackendChannel newChannel() throws IOException {
        return new AMQPBackendChannel(connection.createChannel());
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
