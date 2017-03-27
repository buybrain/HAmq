package io.buybrain.hamq;

import lombok.*;
import lombok.experimental.Wither;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

/**
 * Specification for declaring exchanges as used by {@link Channel#exchangeDeclare}.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@Wither
@AllArgsConstructor
public class ExchangeSpec extends OperationSpec<ExchangeSpec> {
    @NonNull String name;
    @NonNull String type;
    boolean durable;
    boolean autoDelete;
    boolean internal;
    @NonNull Map<String, Object> args;

    public ExchangeSpec(@NonNull String name, @NonNull String type) {
        this.name = name;
        this.type = type;
        durable = true;
        autoDelete = false;
        internal = false;
        args = emptyMap();
    }

    public ExchangeSpec withArg(@NonNull String name, @NonNull Object value) {
        val newArgs = new HashMap<String, Object>(args);
        newArgs.put(name, value);
        return withArgs(newArgs);
    }
}
