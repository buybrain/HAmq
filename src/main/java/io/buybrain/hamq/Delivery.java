package io.buybrain.hamq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import lombok.Value;

import java.io.IOException;

@Value
public class Delivery {
    BackendChannel channel;
    Envelope envelope;
    AMQP.BasicProperties properties;
    byte[] body;

    public String getBodyAsString() {
        return new String(body);
    }

    public void ack() throws IOException {
        channel.basicAck(envelope.getDeliveryTag());
    }

    public void nack() throws IOException {
        channel.basicNack(envelope.getDeliveryTag());
    }
}
