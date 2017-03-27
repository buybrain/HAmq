package io.buybrain.hamq;

/**
 * Interface for backend implementations. A backend represents the client code that is used for communicating with an
 * AMQP broker.
 */
interface Backend {
    /**
     * Create a new connection
     *
     * @param config connection configuration
     * @return the created connection
     * @throws Exception when an error occurred during connecting
     */
    BackendConnection newConnection(Config config) throws Exception;
}
