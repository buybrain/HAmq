package io.buybrain.hamq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

/**
 * Interface for channels created by {@link AMQPBackendConnection} instances
 */
interface BackendChannel extends Closeable {
    void exchangeDeclare(
        String exchange,
        String type,
        boolean durable,
        boolean autoDelete,
        boolean internal,
        Map<String, Object> arguments
    ) throws IOException;

    void queueDeclare(
        String queue,
        boolean durable,
        boolean exclusive,
        boolean autoDelete,
        Map<String, Object> arguments
    ) throws IOException;

    void basicQos(int prefetch) throws IOException;

    void basicPublish(
        String exchange,
        String routingKey,
        boolean mandatory,
        AMQP.BasicProperties props,
        byte[] body
    ) throws IOException;

    void basicConsume(
        String queue,
        String consumerTag,
        boolean noLocal,
        boolean exclusive,
        Map<String, Object> arguments,
        Consumer callback
    ) throws IOException;

    void basicAck(long deliveryTag) throws IOException;

    void basicNack(long deliveryTag) throws IOException;

    void basicCancel(String consumerTag) throws IOException;
}
