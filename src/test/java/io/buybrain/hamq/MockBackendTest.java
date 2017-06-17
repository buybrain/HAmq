package io.buybrain.hamq;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import lombok.val;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.SocketException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Function;

import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class MockBackendTest {
    private Connection SUT;
    private Backend backend;

    @BeforeMethod
    public void setUp() {
        val config = new Config();
        backend = mock(Backend.class);
        SUT = new Connection(config, backend);
    }

    @Test
    public void testScenario() throws Exception {
        val ch = SUT.createChannel();

        val backendConn = mock(BackendConnection.class);
        when(backend.newConnection(any())).thenReturn(backendConn);

        val backendChan = mock(BackendChannel.class);
        when(backendConn.newChannel()).thenReturn(backendChan);

        doThrow(new SocketException("Socket broke"))
            .doNothing()
            .when(backendChan).queueDeclare(eq("test_3"), anyBoolean(), anyBoolean(), anyBoolean(), anyMap());

        // Declare a test queue with defaults
        ch.queueDeclare(new QueueSpec("test_1"));
        // Declare another test queue with other settings
        ch.queueDeclare(
            new QueueSpec("test_2")
                .withDurable(false)
                .withAutoDelete(true)
                .withExclusive(true)
                .withArg("arg1", "val1")
        );
        // Declare a third queue. This one will initially fail with a network error, but it will be recovered
        ch.queueDeclare(new QueueSpec("test_3"));

        val ordered = inOrder(backend, backendConn, backendChan);
        ordered.verify(backend).newConnection(any());
        ordered.verify(backendConn).newChannel();
        ordered.verify(backendChan).queueDeclare("test_1", true, false, false, emptyMap());
        ordered.verify(backendChan).queueDeclare("test_2", false, true, true, singletonMap("arg1", "val1"));
        ordered.verify(backendChan).queueDeclare("test_3", true, false, false, emptyMap());
        ordered.verify(backendChan).close();
        ordered.verify(backendConn).close();
        ordered.verify(backend).newConnection(any());
        ordered.verify(backendConn).newChannel();
        // It should replay all queue declarations it handled so far
        ordered.verify(backendChan).queueDeclare("test_1", true, false, false, emptyMap());
        ordered.verify(backendChan).queueDeclare("test_2", false, true, true, singletonMap("arg1", "val1"));
        ordered.verify(backendChan).queueDeclare("test_3", true, false, false, emptyMap());

        ordered.verifyNoMoreInteractions();
    }

    @Test
    public void testFailingPublishInConsumer() throws Exception {
        // Here we test the case where a nested publish in a consumer fails, causing a reset. The original bug was
        // that the original consumer's handler function would keep running and run into a failing acknowledge,
        // triggering another reset and messing things up. Now, the consumer's thread should be forcefully killed
        // and never reach the ack or even retry the publish.

        val ch = SUT.createChannel();

        val backendConn = mock(BackendConnection.class);
        when(backend.newConnection(any())).thenReturn(backendConn);

        val backendChan = mock(BackendChannel.class);
        when(backendConn.newChannel()).thenReturn(backendChan);

        val resultQueue = new ArrayBlockingQueue<Integer>(1);

        Function<Long, Answer> answerWithDeliveryTag = tag -> invocation -> {
            Consumer consumer = (Consumer) invocation.getArguments()[5];
            Envelope envelope = new Envelope(tag, false, "source", "");
            System.out.println("Handling delivery with tag " + tag);
            consumer.handleDelivery("t1", envelope, null, "42".getBytes());
            return null;
        };

        doThrow(new AlreadyClosedException(mock(ShutdownSignalException.class)))
            .when(backendChan).basicAck(1L);

        doThrow(new AlreadyClosedException(mock(ShutdownSignalException.class)))
            .when(backendChan).basicNack(anyLong());

        doAnswer(answerWithDeliveryTag.apply(1L))
            .doAnswer(answerWithDeliveryTag.apply(2L))
            .when(backendChan).basicConsume(eq("source"), anyString(), anyBoolean(), anyBoolean(), anyMap(), any());

        doThrow(new SocketException("Socket broke"))
            .doAnswer(invocation -> {
                System.out.println("Basic publish will succeed!");
                resultQueue.put(Integer.parseInt(new String((byte[]) invocation.getArguments()[4])));
                return null;
            }).when(backendChan).basicPublish(eq(""), eq("target"), anyBoolean(), any(), any());

        ch.consume(new ConsumeSpec("source", delivery -> {
            // Multiply the body by 10 and publish the result
            try {
                ch.publish(new PublishSpec(
                    "",
                    "target",
                    Integer.toString(parseInt(delivery.getBodyAsString()) * 10).getBytes()
                ));
            } catch (Exception ex) {
                delivery.nack();
                return;
            }
            delivery.ack();
        }));

        val result = resultQueue.take();

        assertThat(result, is(42 * 10));
    }
}
