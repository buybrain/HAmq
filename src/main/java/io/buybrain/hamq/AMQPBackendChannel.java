package io.buybrain.hamq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.Map;

import static io.buybrain.util.Exceptions.rethrow;

/**
 * AMQP backend channel based on the RabbitMQ client library. All methods are literally copied from this library and
 * mimic the underlying API closely.
 */
@RequiredArgsConstructor
class AMQPBackendChannel implements BackendChannel {
    @NonNull com.rabbitmq.client.Channel channel;

    @Override
    public void exchangeDeclare(
        @NonNull String exchange,
        @NonNull String type,
        boolean durable,
        boolean autoDelete,
        boolean internal,
        @NonNull Map<String, Object> arguments
    ) throws IOException {
        channel.exchangeDeclare(exchange, type, durable, autoDelete, internal, arguments);
    }

    @Override
    public void queueDeclare(
        @NonNull String queue,
        boolean durable,
        boolean exclusive,
        boolean autoDelete,
        @NonNull Map<String, Object> arguments
    ) throws IOException {
        channel.queueDeclare(queue, durable, exclusive, autoDelete, arguments);
    }

    @Override
    public void basicQos(int prefetch) throws IOException {
        channel.basicQos(prefetch);
    }

    @Override
    public void basicPublish(
        @NonNull String exchange,
        @NonNull String routingKey,
        boolean mandatory,
        AMQP.BasicProperties props,
        byte[] body
    ) throws IOException {
        channel.basicPublish(exchange, routingKey, mandatory, props, body);
    }

    @Override
    public void basicConsume(
        @NonNull String queue,
        @NonNull String consumerTag,
        boolean noLocal,
        boolean exclusive,
        Map<String, Object> arguments,
        Consumer callback
    ) throws IOException {
        channel.basicConsume(queue, false, consumerTag, noLocal, exclusive, arguments, callback);
    }

    @Override
    public void basicAck(long deliveryTag) throws IOException {
        channel.basicAck(deliveryTag, false);
    }

    @Override
    public void basicNack(long deliveryTag) throws IOException {
        channel.basicNack(deliveryTag, false, false);
    }

    @Override
    public void basicCancel(@NonNull String consumerTag) throws IOException {
        channel.basicCancel(consumerTag);
    }

    @Override
    public void close() throws IOException {
        rethrow(() -> channel.close());
    }
}
