package dev.mokka.core;

/**
 * The public shape of a mokka fake — one AWS client, its handler, and a reset hook.
 *
 * <p>Every {@code XxxFake} class in a service module implements this. The JUnit 5
 * extension and Spring Boot starter use it to hand a proxied client to test code
 * and to reset in-memory state between tests without needing to know which
 * specific fake it is holding.
 */
public interface FakeInstance {

    /** The proxied AWS SDK client. Cast to the concrete SDK interface at the call site. */
    Object client();

    /** The handler that owns the in-memory state — for advanced setup / fault injection. */
    ServiceHandler handler();

    /** Wipe in-memory state. Called between tests. */
    void reset();
}
