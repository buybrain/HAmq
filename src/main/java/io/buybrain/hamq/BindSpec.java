package io.buybrain.hamq;

import lombok.*;
import lombok.experimental.Wither;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

/**
 * Specification for declaring queue bindings as used by {@link Channel#queueBind}.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@Wither
@AllArgsConstructor
public class BindSpec extends OperationSpec<BindSpec> {
    @NonNull String queue;
    @NonNull String exchange;
    @NonNull String routingKey;
    @NonNull Map<String, Object> args;

    public BindSpec(@NonNull String queue, @NonNull String exchange) {
        this.queue = queue;
        this.exchange = exchange;
        routingKey = "";
        args = emptyMap();
    }

    BindSpec withArg(@NonNull String name, @NonNull Object value) {
        val newArgs = new HashMap<String, Object>(args);
        newArgs.put(name, value);
        return withArgs(newArgs);
    }
}
