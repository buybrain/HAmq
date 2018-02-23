package io.buybrain.hamq;

import lombok.*;
import lombok.experimental.Wither;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

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
    @NonNull Map<String, Object> headers;
    boolean mandatory;
    boolean durable;
    byte[] body;

    /**
     * @param exchange   the exchange to publish to
     * @param routingKey the routing key
     * @param body       the message body as a byte array
     */
    public PublishSpec(@NonNull String exchange, @NonNull String routingKey, byte[] body) {
        this.exchange = exchange;
        this.routingKey = routingKey;
        mandatory = false;
        durable = true;
        this.body = body;
        this.headers = emptyMap();
    }

    public PublishSpec withHeader(@NonNull String key, @NonNull Object value) {
        val newHeaders = new HashMap<String, Object>(headers);
        newHeaders.put(key, value);
        return withHeaders(newHeaders);
    }

    /**
     * Static factory shorthand for publishing directly to a queue
     *
     * @param queueName the name of the queue to publish to
     * @param body      the body as a byte array
     * @return the specification
     */
    public static PublishSpec queue(@NonNull String queueName, byte[] body) {
        return new PublishSpec("", queueName, body);
    }
}
