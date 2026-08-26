package dev.mokka.core;

/**
 * Service Provider Interface used by the JUnit 5 extension (and other integrations)
 * to discover per-service fakes on the classpath.
 *
 * <p>Each {@code mokka-<service>} module ships one implementation and registers it
 * via {@code META-INF/services/dev.mokka.core.MokkaFakeProvider}. When a test
 * declares a {@code @MokkaClient S3Client s3} field the extension iterates every
 * provider, finds the one that {@link #supports(Class)} the field type, and asks
 * it to {@link #create()} a fresh {@link FakeInstance}.
 *
 * <p>This lets mokka add services without ever touching the junit5 or
 * spring-boot-starter modules — you drop a new artifact on the classpath and it
 * lights up.
 */
public interface MokkaFakeProvider {

    /**
     * Does this provider know how to fake instances of {@code clientType}?
     * Typically the check is a simple class equality against an AWS SDK client
     * interface, e.g. {@code clientType == S3Client.class}.
     */
    boolean supports(Class<?> clientType);

    /** Human-readable service name for diagnostics, e.g. {@code "S3"}. */
    String serviceName();

    /** Build a fresh fake with empty state. */
    FakeInstance create();
}
