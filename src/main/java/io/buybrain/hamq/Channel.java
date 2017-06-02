package io.buybrain.hamq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import io.buybrain.util.function.ThrowingConsumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.buybrain.util.Result.trying;

/**
 * HAmq channel representation. All methods will be automatically retried in case of network or broker failures until
 * they succeed.
 */
@Slf4j
@RequiredArgsConstructor
public class Channel {
    @NonNull private final Connection connection;
    @NonNull private final Retryer retryer;
    private BackendChannel channel;

    private static AtomicInteger tagCounter = new AtomicInteger();

    // The following fields capture the state created through this channel, and will be used for recreating channels
    // after reconnect events
    private List<ExchangeSpec> exchanges = new ArrayList<>();
    private List<QueueSpec> queues = new ArrayList<>();
    private List<BindSpec> binds = new ArrayList<>();
    private Map<String, ConsumeSpec> consumers = new HashMap<>();
    private PrefetchSpec prefetchSpec;

    /**
     * Declare an exchange. Will create the exchange if it doesn't exist, or do nothing if it already exists with the
     * same settings. If settings differ, an exception will be thrown.
     *
     * @param spec the declare specification
     */
    public synchronized void exchangeDeclare(@NonNull ExchangeSpec spec) {
        doExchangeDeclare(spec);
        exchanges.add(spec);
    }

    private void doExchangeDeclare(@NonNull ExchangeSpec spec) {
        perform(chan -> chan.exchangeDeclare(
            spec.getName(),
            spec.getType(),
            spec.isDurable(),
            spec.isAutoDelete(),
            spec.isInternal(),
            spec.getArgs()
        ), spec);
    }

    /**
     * Declare a queue. Will create the queue if it doesn't exist, or do nothing if it already exists with the same
     * settings. If settings differ, an exception will be thrown.
     *
     * @param spec the declare specification
     */
    public synchronized void queueDeclare(@NonNull QueueSpec spec) {
        doQueueDeclare(spec);
        queues.add(spec);
    }

    private void doQueueDeclare(@NonNull QueueSpec spec) {
        perform(chan -> chan.queueDeclare(
            spec.getName(),
            spec.isDurable(),
            spec.isExclusive(),
            spec.isAutoDelete(),
            spec.getArgs()
        ), spec);
    }

    /**
     * Bind a queue to an exchange. Will create a new binding if it doesn't exist, or do nothing if the binding already
     * exists with the same settings.
     *
     * @param spec the bind specification
     */
    public synchronized void queueBind(@NonNull BindSpec spec) {
        doQueueBind(spec);
        binds.add(spec);
    }

    private void doQueueBind(@NonNull BindSpec spec) {
        perform(chan -> chan.queueBind(
            spec.getQueue(),
            spec.getExchange(),
            spec.getRoutingKey(),
            spec.getArgs()
        ), spec);
    }

    /**
     * Specify the maximum number of messages that may be prefetched for this channel
     *
     * @param spec the prefetch specification
     */
    public void prefetch(@NonNull PrefetchSpec spec) {
        doPrefetch(spec);
        prefetchSpec = spec;
    }

    private void doPrefetch(PrefetchSpec spec) {
        perform(chan -> chan.basicQos(spec.getAmount()), spec);
    }

    /**
     * Publish a message on this channel
     *
     * @param spec the publish specification
     */
    public void publish(@NonNull PublishSpec spec) {
        perform(chan -> chan.basicPublish(
            spec.getExchange(),
            spec.getRoutingKey(),
            spec.isMandatory(),
            new AMQP.BasicProperties.Builder()
                .deliveryMode(spec.isDurable() ? 2 : 1)
                .build(),
            spec.getBody()
        ), spec);
    }

    /**
     * Consume messages on this channel
     *
     * @param spec the consume specification
     */
    public void consume(@NonNull ConsumeSpec spec) {
        val consumerTag = "consumer-" + tagCounter.getAndIncrement();
        doConsume(consumerTag, spec);
        consumers.put(consumerTag, spec);
    }

    private void doConsume(@NonNull String consumerTag, @NonNull ConsumeSpec spec) {
        perform(chan -> chan.basicConsume(
            spec.getQueue(),
            consumerTag,
            spec.isNoLocal(),
            spec.isExclusive(),
            spec.getArgs(),
            new com.rabbitmq.client.Consumer() {
                @Override
                public void handleConsumeOk(String consumerTag) {
                }

                @Override
                public void handleCancelOk(String consumerTag) {
                }

                @Override
                public void handleCancel(String consumerTag) throws IOException {
                }

                @Override
                public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
                    reset();
                }

                @Override
                public void handleRecoverOk(String consumerTag) {
                }

                @Override
                public void handleDelivery(
                    String consumerTag,
                    Envelope envelope,
                    AMQP.BasicProperties properties,
                    byte[] body
                ) {
                    trying(() -> spec.getCallback().accept(new Delivery(chan, envelope, properties, body)))
                        .orElse(ex -> {
                            // Processing the delivery failed, which means that acking or nacking must have failed since
                            // consumer callbacks are supposed to deal with their own internal errors. We have to clean
                            // up this channel and connection and retry consuming.
                            trying(() -> chan.basicCancel(consumerTag));
                            log.warn("Error while (n)acking delivery, will retry consuming", ex);
                            reset();
                        });
                }
            }
        ), spec);
    }


    /**
     * Try to perform an operation on the channel, retrying it if necessary
     */
    private void perform(ThrowingConsumer<BackendChannel> operation, OperationSpec spec) {
        retryer.performWithRetry(
            () -> operation.accept(activeChannel()),
            getRetryPolicy(spec).withErrorHandler(ex -> {
                if (Retryer.shouldReconnectToRecover(ex)) {
                    reset();
                }
            })
        );
    }

    private RetryPolicy getRetryPolicy(OperationSpec spec) {
        if (spec.getRetryPolicy() != null) {
            return spec.getRetryPolicy();
        }
        return connection.getRetryPolicy();
    }

    private synchronized BackendChannel activeChannel() {
        if (channel == null) {
            channel = retryer.performWithRetry(
                () -> connection.activeConnection().newChannel(),
                new RetryPolicy().withRetryAll(true)
            );
        }
        return channel;
    }

    private synchronized void reset() {
        if (channel != null) {
            consumers.keySet().forEach(tag -> trying(() -> channel.basicCancel(tag)));
            trying(channel::close);
        }
        channel = null;
        connection.reset();

        // Restore state
        exchanges.forEach(this::doExchangeDeclare);
        queues.forEach(this::doQueueDeclare);
        binds.forEach(this::doQueueBind);
        if (prefetchSpec != null) {
            doPrefetch(prefetchSpec);
        }
        consumers.forEach(this::doConsume);
    }
}
