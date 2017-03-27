package io.buybrain.hamq;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 * Base class for all operation specifications, supporting optionally overriding the retry policy per operation.
 */
@AllArgsConstructor
public class OperationSpec<T extends OperationSpec> {
    @Getter private RetryPolicy retryPolicy;

    OperationSpec() {
    }

    public T withRetryPolicy(@NonNull RetryPolicy policy) {
        this.retryPolicy = policy;
        return (T) this;
    }
}
