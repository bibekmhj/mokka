package dev.mokka.core;

/**
 * Entry point for the low-level mokka core.
 *
 * <p>Typical usage goes through the per-service modules (e.g. {@code S3Fake.create()}
 * or the JUnit 5 {@code @MokkaClient} field injection). Use this class directly only
 * when you want to fake an AWS service that has no dedicated mokka module yet.
 *
 * <pre>{@code
 * // A service with no dedicated fake — every method throws MokkaUnimplementedException
 * // when actually invoked, but the client compiles and can be injected today.
 * KinesisClient kinesis = Mokka.fake(KinesisClient.class, "Kinesis");
 * }</pre>
 */
public final class Mokka {

    private Mokka() {
        // Static factory only.
    }

    /**
     * Return a mokka proxy for {@code clientInterface} that throws
     * {@link MokkaUnimplementedException} for every method call.
     *
     * <p>Useful when you need a client type to compile and be injected, but the
     * code path under test does not actually invoke the client.
     *
     * @param clientInterface an AWS SDK v2 client interface, e.g. {@code KinesisClient.class}
     * @param serviceName human-readable service name for error messages
     */
    public static <T> T fake(Class<T> clientInterface, String serviceName) {
        return MokkaProxy.wrap(clientInterface, new UnimplementedHandler(serviceName));
    }

    /**
     * Return a mokka proxy for {@code clientInterface} wired to {@code handler}.
     *
     * <p>Used by mokka's per-service modules. Application code should not usually
     * call this directly.
     */
    public static <T> T wrap(Class<T> clientInterface, ServiceHandler handler) {
        return MokkaProxy.wrap(clientInterface, handler);
    }
}
