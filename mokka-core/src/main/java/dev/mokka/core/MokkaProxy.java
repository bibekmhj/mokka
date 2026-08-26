package dev.mokka.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The reflective bridge that lets any AWS SDK v2 client interface be faked without
 * hand-writing every method.
 *
 * <p>Mokka wraps a service client interface in a {@link Proxy} whose invocations are
 * routed to a {@link ServiceHandler}. Methods that Object defines (equals, hashCode,
 * toString) are handled here. Well-known SDK client methods ({@code close},
 * {@code serviceName}) get sensible defaults so tests never fail on lifecycle calls.
 * Every other call is dispatched to the handler.
 *
 * <p>This is why <em>every</em> AWS SDK v2 client compiles against mokka on Day 1,
 * even for services with no dedicated fake yet: the proxy accepts the interface and
 * an {@link UnimplementedHandler} throws {@link MokkaUnimplementedException} lazily
 * only when a specific method is actually called.
 */
public final class MokkaProxy implements InvocationHandler {

    private final Class<?> clientInterface;
    private final ServiceHandler handler;

    private MokkaProxy(Class<?> clientInterface, ServiceHandler handler) {
        this.clientInterface = Objects.requireNonNull(clientInterface, "clientInterface");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /**
     * Wrap {@code clientInterface} in a proxy that dispatches to {@code handler}.
     *
     * @param clientInterface an AWS SDK v2 client interface, e.g. {@code S3Client.class}
     * @param handler the per-service handler that implements the fake behavior
     * @return an instance of {@code clientInterface} you can inject into code under test
     */
    @SuppressWarnings("unchecked")
    public static <T> T wrap(Class<T> clientInterface, ServiceHandler handler) {
        if (!clientInterface.isInterface()) {
            throw new IllegalArgumentException(
                "mokka can only proxy interfaces. " + clientInterface.getName()
                    + " is not an interface. AWS SDK v2 clients are interfaces — did you"
                    + " pass a concrete class or a v1 client by mistake?");
        }
        Object proxy = Proxy.newProxyInstance(
            clientInterface.getClassLoader(),
            new Class<?>[] { clientInterface },
            new MokkaProxy(clientInterface, handler));
        return (T) proxy;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        // Object methods.
        if (method.getDeclaringClass() == Object.class) {
            return switch (name) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "MokkaFake(" + clientInterface.getSimpleName() + ")";
                default -> method.invoke(this, args);
            };
        }

        // SDK client lifecycle: close() is common and must never fail.
        if ("close".equals(name) && (args == null || args.length == 0)) {
            return null;
        }

        // SDK client identity: serviceName()/serviceClientConfiguration() — return sane defaults.
        if ("serviceName".equals(name) && (args == null || args.length == 0)) {
            return handler.serviceName();
        }

        // In AWS SDK v2 EVERY method on the client interface is a default method:
        //   - the concrete overload `op(Request)`         → default throws UnsupportedOperationException
        //   - the lambda overload   `op(Consumer<Builder>)` → default builds the request and calls `op(Request)`
        //   - the no-arg overload   `op()`                → default calls `op(Request.builder().build())`
        // The concrete overload's default just throws, so we cannot blanket-delegate.
        // We DO want to delegate the useful defaults — lambda and no-arg — so the SDK
        // builds a concrete request and re-invokes the concrete overload via this
        // proxy, ensuring the handler always sees a typed request object.
        //
        // Rule: delegate to the SDK default when there's no first arg (no-arg
        // convenience) OR when the first arg is a Consumer lambda. Otherwise dispatch
        // to the handler — the concrete overload is exactly what the handler exists
        // to implement.
        if (method.isDefault()
            && (args == null || args.length == 0 || args[0] instanceof Consumer<?>)) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }

        return handler.handle(method, args);
    }
}
