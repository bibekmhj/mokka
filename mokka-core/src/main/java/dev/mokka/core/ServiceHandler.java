package dev.mokka.core;

import java.lang.reflect.Method;

/**
 * Per-service dispatch layer that maps AWS SDK operation calls to fake behavior.
 *
 * <p>Each mokka-&lt;service&gt; module provides one implementation. The handler receives
 * a reflectively intercepted method call (from {@link MokkaProxy}) and either returns
 * a faked response or throws {@link MokkaUnimplementedException} to signal that this
 * operation has no fake behavior yet.
 *
 * <p>Handlers should be stateful (holding the in-memory bucket/table/queue state)
 * and thread-safe.
 */
public interface ServiceHandler {

    /**
     * The AWS service name this handler faked, e.g. {@code "S3"}, {@code "DynamoDB"}.
     * Used in error messages and diagnostics.
     */
    String serviceName();

    /**
     * Handle one SDK operation call.
     *
     * @param method the SDK client interface method that was invoked
     * @param args the arguments passed to that method (may be {@code null} for no-arg calls)
     * @return the faked response value, or {@code null} for void methods
     * @throws MokkaUnimplementedException if this handler has no fake for the operation
     * @throws Throwable if the fake behavior itself throws (e.g., a faked service exception)
     */
    Object handle(Method method, Object[] args) throws Throwable;

    /**
     * Reset all in-memory state. Called between tests by the JUnit 5 extension.
     */
    void reset();
}
