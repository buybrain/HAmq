HAmq
===

HAmq (High Availability MQ, pronounce 'hammock') is a simple Java AMQP client that's designed for dealing with errors
by retrying operations. It's also opinionated and aimed at (micro)service workloads. Internally it uses the RabbitMQ
client library.

The API HAmq exposes is currently quite limited, but will be expanded in the future when the need arises. It is not a
direct copy of the RabbitMQ client API, and as such cannot be used as a drop-in replacement.

Requirements
---

Java 8 or higher is required

High Availability
---

Since AMQP is a networking protocol, things can and will go wrong. Networks will go down, brokers will
crash, messages will be dropped. HAmq will make sure that in these events connections and channels
will be transparently restored, queues and exchanges will be re-declared and consumers will be restarted.

Usage
---

Specify the configuration:
```java
Config config = new Config()
    .withHost("my-amqp-host")
    .withUser("me")
    .withPassword("my-pass");
```

Create a new connection:

```java
Connection conn = Connections.create(config);
```

And create a channel from the connection:

```java
Channel chan = conn.createChannel();
```

Connections and channels have the same semantics as usual in AMQP, but the above code so far hasn't
actually communicated with the broker yet. Only once channel methods are being called will the first
connection be established. 

Let's declare a queue and publish a message on it:

```java
chan.queueDeclare(new QueueSpec("my-queue"));
chan.publish(PublishSpec.queue("my-queue", "message".getBytes()));
```

Note the `QueueSpec` and `PublishSpec` objects here. All channel methods take these kinds of spec
objects as their argument, and the reason is to prevent introducing dozens of overloaded methods
with different combinations of possible options (AMQP methods tend to have a lot of options).

Spec objects accept the required fields in their constructor and support settings other options with
`with`er methods, like this:

```java
chan.queueDeclare(
    new QueueSpec("my-queue")
        .withDurable(false)
        .withExclusive(true)
        .withArg("x-message-ttl", 60000);
);
```
This way, you don't have to repeat default values, and it's clearer what is really being configured.

Finally, let's consume our queue:

```java
chan.consume(new ConsumeSpec("my-queue", delivery -> {
    System.out.println("Got message: " + delivery.getBodyAsString());
    delivery.ack();
}));
```

As you might see, specifying a consumer is a lot simpler than how it's usually done with the RabbitMQ
client. The reason this is possible is because HAmq is making some assumptions here:

- Every delivery will be either acked or nacked by the callback.
- Callbacks will not throw exceptions, but catch them and either ack or nack the message.
- Messages will be delivered at least once, but may be delivered multiple times.

Any exception thrown by the callback is assumed to be failure to ack or nack, which implies a network
or broker error, and will result in closing the connection and channel and reconnecting.

Project status
---
HAmq is a very new project and will be subject to change. It needs a lot of testing and better
documentation.

License
---

MIT
