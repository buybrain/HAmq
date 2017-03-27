package io.buybrain.hamq;

import lombok.val;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.net.SocketException;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class MockBackendTest {
    private Connection SUT;
    private Backend backend;

    @BeforeTest
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
}
