package io.buybrain.hamq;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * This test assumes an empty RabbitMQ instance is running at localhost:5673 with a guest:guest login on vhost /.
 * <p>
 * Easily start one with docker run --rm -p 5672:5672 rabbitmq:3.6
 */
@Slf4j
public class AMQPBackendTest {
    private Connection SUT;
    private Backend backend;

    @BeforeMethod
    public void setUp() {
        val config = new Config();
        backend = new AMQPBackend();
        SUT = new Connection(config, backend);
    }

    /**
     * Not a unit test but a fixture that is used to debug what happens if a broker disconnects combined with a
     * publish and acknowledge inside of a consumer body.
     */
    @Test(enabled = false)
    public void testPublishInConsumerAndBrokerDisconnect() throws InterruptedException {
        val chan = SUT.createChannel();

        chan.queueDeclare(new QueueSpec("testing"));
        chan.queueDeclare(new QueueSpec("target"));
        // Publish some messages
        for (int i = 0; i < 10; i++) {
            chan.publish(new PublishSpec("", "testing", "hi".getBytes()));
        }

        chan.consume(new ConsumeSpec("testing", delivery -> {
            System.out.println("Pre publish, will sleep for 10 seconds");
            log.warn(Thread.currentThread().getName());
            Thread.sleep(1000);
            chan.publish(new PublishSpec("", "target", delivery.getBody()));
            System.out.println("Published, pre ack, will sleep for 10 seconds");
            log.warn(Thread.currentThread().getName());
            Thread.sleep(1000);
            delivery.ack();
        }));

        Thread.sleep(1000000000);
    }
}
