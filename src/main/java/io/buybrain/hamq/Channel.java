package io.buybrain.hamq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import io.buybrain.util.function.ThrowingConsumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

    private static final AtomicInteger tagCounter = new AtomicInteger();

    // The following fields capture the state created through this channel, and will be used for recreating channels
    // after reconnect events
    private final List<ExchangeSpec> exchanges = new ArrayList<>();
    private final List<QueueSpec> queues = new ArrayList<>();
    private final List<BindSpec> binds = new ArrayList<>();
    private final Map<String, ConsumeSpec> consumers = new HashMap<>();
    private PrefetchSpec prefetchSpec;

    private final Lock getChannelLock = new ReentrantLock();

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
        val propBuilder = new AMQP.BasicProperties.Builder()
            .deliveryMode(spec.isDurable() ? 2 : 1);

        if (!spec.getHeaders().isEmpty()) {
            propBuilder.headers(spec.getHeaders());
        }

        perform(chan -> chan.basicPublish(
            spec.getExchange(),
            spec.getRoutingKey(),
            spec.isMandatory(),
            propBuilder.build(),
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
        val closed = new AtomicBoolean(false);

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
                    closed.set(true);
                    reset(chan);
                }

                @Override
                public void handleRecoverOk(String consumerTag) {
                }

                @Override
                @SneakyThrows
                public void handleDelivery(
                    String consumerTag,
                    Envelope envelope,
                    AMQP.BasicProperties properties,
                    byte[] body
                ) {
                    if (closed.get()) {
                        throw new RuntimeException("Consumer is closed");
                    }
                    trying(() -> spec.getCallback().accept(new Delivery(chan, envelope, properties, body)))
                        .orElse(ex -> {
                            // Processing the delivery failed, which means that acking or nacking must have failed
                            // since consumer callbacks are supposed to deal with their own internal errors. We have
                            // to clean up this channel and connection and retry consuming.
                            trying(() -> chan.basicCancel(consumerTag));
                            log.warn("Error while (n)acking delivery, will retry consuming", ex);
                            closed.set(true);
                            reset(chan);
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
                    reset(null);
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

    private BackendChannel activeChannel() {
        getChannelLock.lock();
        if (channel == null) {
            channel = retryer.performWithRetry(
                () -> connection.activeConnection().newChannel(),
                new RetryPolicy().withRetryAll(true)
            );
        }
        getChannelLock.unlock();
        return channel;
    }

    @SneakyThrows
    private void reset(BackendChannel errorChan) {
        if (channel != null && errorChan != null && channel != errorChan) {
            // The channel on which the error occurred is not the same as the current active channel, which means that
            // we got an error on an old, already closed/reset channel. This can happen in consumers where message
            // acknowledgement fails because the connection and channel were already reset.
            return;
        }
        // Cancel consumers and close the current channel
        if (channel != null) {
            consumers.keySet().forEach(tag -> trying(() -> channel.basicCancel(tag)));
            trying(channel::close);
        }
        // Reset the connection
        connection.reset();
        channel = null;

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
