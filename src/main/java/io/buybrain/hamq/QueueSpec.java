package io.buybrain.hamq;

import lombok.*;
import lombok.experimental.Wither;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

/**
 * Specification for declaring queues as used by {@link Channel#queueDeclare}.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@Wither
@AllArgsConstructor
public class QueueSpec extends OperationSpec<QueueSpec> {
    @NonNull String name;
    boolean durable;
    boolean exclusive;
    boolean autoDelete;
    @NonNull Map<String, Object> args;

    public QueueSpec(@NonNull String name) {
        this.name = name;
        durable = true;
        exclusive = false;
        autoDelete = false;
        args = emptyMap();
    }

    QueueSpec withArg(@NonNull String name, @NonNull Object value) {
        val newArgs = new HashMap<String, Object>(args);
        newArgs.put(name, value);
        return withArgs(newArgs);
    }
}
