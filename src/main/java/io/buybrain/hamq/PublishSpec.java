package io.buybrain.hamq;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Wither;

/**
 * Specification for publishing a message as used by {@link Channel#publish}.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@Wither
@AllArgsConstructor
public class PublishSpec extends OperationSpec<PublishSpec> {
    @NonNull String exchange;
    @NonNull String routingKey;
    boolean mandatory;
    boolean durable;
    byte[] body;

    /**
     * @param exchange the exchange to publish to
     * @param routingKey the routing key
     * @param body the message body as a byte array
     */
    public PublishSpec(@NonNull String exchange, @NonNull String routingKey, byte[] body) {
        this.exchange = exchange;
        this.routingKey = routingKey;
        mandatory = false;
        durable = true;
        this.body = body;
    }

    /**
     * Static factory shorthand for publishing directly to a queue
     *
     * @param queueName the name of the queue to publish to
     * @param body the body as a byte array
     * @return the specification
     */
    public static PublishSpec queue(@NonNull String queueName, byte[] body) {
        return new PublishSpec("", queueName, body);
    }
}
