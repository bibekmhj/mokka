package dev.mokka.core;

import java.lang.reflect.Method;

/**
 * Default handler used when you call {@code Mokka.fake(SomeAwsClient.class)} for
 * a service with no dedicated mokka module.
 *
 * <p>Every method throws {@link MokkaUnimplementedException} on invocation, but the
 * client interface compiles and can be dependency-injected today — you only pay the
 * cost when your test actually exercises that specific method. This is how mokka
 * ships "every AWS service compiles against mokka" on Day 1.
 */
public final class UnimplementedHandler implements ServiceHandler {

    private final String serviceName;

    public UnimplementedHandler(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public String serviceName() {
        return serviceName;
    }

    @Override
    public Object handle(Method method, Object[] args) {
        throw new MokkaUnimplementedException(serviceName, method.getName());
    }

    @Override
    public void reset() {
        // No state.
    }
}
