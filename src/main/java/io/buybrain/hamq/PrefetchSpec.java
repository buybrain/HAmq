package io.buybrain.hamq;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.Wither;

/**
 * Specification for configuring prefetching as used by {@link Channel#prefetch}.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@Wither
@AllArgsConstructor
public class PrefetchSpec extends OperationSpec<PrefetchSpec> {
    int amount;
}
