package io.buybrain.hamq;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Wither;

/**
 * Connection and retry configuration for HAmq connections
 */
@Value
@Wither
@AllArgsConstructor
public class Config {
    @NonNull String host;
    int port;
    @NonNull String username;
    @NonNull String password;
    @NonNull String vhost;
    @NonNull RetryPolicy retryPolicy;

    public Config() {
        host = "localhost";
        port = 5672;
        username = "guest";
        password = "guest";
        vhost = "/";
        retryPolicy = new RetryPolicy();
    }
}
