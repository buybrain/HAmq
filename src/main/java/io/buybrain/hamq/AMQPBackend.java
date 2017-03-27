package io.buybrain.hamq;

import com.rabbitmq.client.ConnectionFactory;
import lombok.NonNull;
import lombok.val;

/**
 * AMQP backend that uses the Java RabbitMQ client library for communication
 */
class AMQPBackend implements Backend {
    /**
     * Create a new RabbitMQ client connection
     *
     * @param config connection configuration
     * @return the created connection
     * @throws Exception when an error occurred during connecting
     */
    @Override
    public BackendConnection newConnection(@NonNull Config config) throws Exception {
        val factory = new ConnectionFactory();
        factory.setHost(config.getHost());
        factory.setPort(config.getPort());
        factory.setUsername(config.getUsername());
        factory.setPassword(config.getPassword());
        factory.setVirtualHost(config.getVhost());
        factory.setAutomaticRecoveryEnabled(false);

        return new AMQPBackendConnection(factory.newConnection());
    }
}
