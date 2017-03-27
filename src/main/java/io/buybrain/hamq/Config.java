package io.buybrain.hamq;

import io.buybrain.util.Env;
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

    public static Config ofEnv(@NonNull Env env) {
        return ofEnv(env, "AMQP_");
    }

    public static Config ofEnv(@NonNull Env env, @NonNull String prefix) {
        Config config = new Config();
        return config
            .withHost(env.getString(prefix + "HOST", config.getHost()))
            .withPort(env.getInt(prefix + "PORT", config.getPort()))
            .withUsername(env.getString(prefix + "USER", config.getUsername()))
            .withPassword(env.getString(prefix + "PASS", config.getPassword()))
            .withVhost(env.getString(prefix + "VHOST", config.getVhost()));
    }
}
