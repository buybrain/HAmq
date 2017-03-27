package io.buybrain.hamq;

import java.io.Closeable;
import java.io.IOException;

/**
 * Interface for connections created by {@link Backend} instances
 */
interface BackendConnection extends Closeable {
    /**
     * Create a new channel
     *
     * @return the new channel
     * @throws IOException when an error occured during channel creation
     */
    BackendChannel newChannel() throws IOException;
}
