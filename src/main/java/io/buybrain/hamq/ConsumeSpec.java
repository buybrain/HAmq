package io.buybrain.hamq;

import io.buybrain.util.function.ThrowingConsumer;
import lombok.*;
import lombok.experimental.Wither;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

/**
 * Specification for consuming messages as used by {@link Channel#consume}.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@Wither
@AllArgsConstructor
public class ConsumeSpec extends OperationSpec<ConsumeSpec> {
    @NonNull String queue;
    @NonNull ThrowingConsumer<Delivery> callback;
    boolean noLocal;
    boolean exclusive;
    @NonNull Map<String, Object> args;

    /**
     * @param queue the name of the queue to consume
     * @param callback callback for processing incoming messages. Deliveries must be acked or nacked.
     */
    public ConsumeSpec(@NonNull String queue, @NonNull ThrowingConsumer<Delivery> callback) {
        this.queue = queue;
        this.callback = callback;
        noLocal = false;
        exclusive = false;
        args = emptyMap();
    }

    public ConsumeSpec withArg(@NonNull String name, @NonNull Object value) {
        val newArgs = new HashMap<String, Object>(args);
        newArgs.put(name, value);
        return withArgs(newArgs);
    }
}
