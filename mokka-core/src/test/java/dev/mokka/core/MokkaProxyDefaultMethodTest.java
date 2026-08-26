package dev.mokka.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors the AWS SDK v2 pattern where EVERY method on the client interface is a
 * default method — the concrete overload just throws UnsupportedOperationException,
 * the lambda overload builds a request via a Consumer and re-invokes the concrete
 * overload, and the no-arg overload delegates to the builder form.
 *
 * <p>These tests exist so the proxy contract is verified without touching the
 * real AWS SDK: the sandbox can't reach Maven Central and we shipped one regression
 * ({@link InvocationHandler#invokeDefault} tripping on the concrete overload)
 * because we didn't have a synthetic model of this shape.
 */
class MokkaProxyDefaultMethodTest {

    static final class SomeRequest {
        final String name;
        SomeRequest(String name) { this.name = name; }
        static Builder builder() { return new Builder(); }
        static final class Builder {
            String name;
            Builder name(String v) { this.name = v; return this; }
            SomeRequest build() { return new SomeRequest(name); }
            Builder applyMutation(Consumer<Builder> c) { c.accept(this); return this; }
        }
    }

    static final class SomeResponse {
        final String echoed;
        SomeResponse(String echoed) { this.echoed = echoed; }
    }

    /** Shape-for-shape mirror of an AWS SDK v2 client interface. */
    interface FakeSdkClient {
        default SomeResponse op(SomeRequest request) {
            // AWS SDK's default for the "abstract-like" overload always throws.
            throw new UnsupportedOperationException(
                "This is the fake SDK's default for the concrete overload; a real "
                    + "SDK client would override this.");
        }
        default SomeResponse op(Consumer<SomeRequest.Builder> consumer) {
            return op(SomeRequest.builder().applyMutation(consumer).build());
        }
        default SomeResponse op() {
            return op(SomeRequest.builder().build());
        }
    }

    private static ServiceHandler echoHandler() {
        return new ServiceHandler() {
            @Override public String serviceName() { return "Fake"; }
            @Override public Object handle(Method method, Object[] args) {
                if (args == null || args.length == 0) {
                    return new SomeResponse("no-arg");
                }
                if (args[0] instanceof SomeRequest req) {
                    return new SomeResponse("built:" + req.name);
                }
                throw new AssertionError("handler saw unexpected arg type: " + args[0].getClass());
            }
            @Override public void reset() {}
        };
    }

    @Test
    void concreteOverloadReachesHandlerDirectly() {
        FakeSdkClient client = MokkaProxy.wrap(FakeSdkClient.class, echoHandler());
        SomeResponse res = client.op(new SomeRequest("direct"));
        assertThat(res.echoed).isEqualTo("built:direct");
    }

    @Test
    void consumerOverloadDelegatesToDefaultThenReachesHandler() {
        FakeSdkClient client = MokkaProxy.wrap(FakeSdkClient.class, echoHandler());
        SomeResponse res = client.op(b -> b.name("via-consumer"));
        assertThat(res.echoed).isEqualTo("built:via-consumer");
    }

    @Test
    void noArgOverloadDelegatesToDefaultThenReachesHandler() {
        FakeSdkClient client = MokkaProxy.wrap(FakeSdkClient.class, echoHandler());
        SomeResponse res = client.op();
        // No-arg default -> invokeDefault -> op(builder().build()) -> proxy sees
        // the concrete overload with a built SomeRequest (name=null) -> handler.
        assertThat(res.echoed).isEqualTo("built:null");
    }

    @Test
    void unimplementedHandlerBubblesForConcreteOverload() {
        FakeSdkClient client = Mokka.fake(FakeSdkClient.class, "Fake");
        assertThatThrownBy(() -> client.op(new SomeRequest("x")))
            .isInstanceOf(MokkaUnimplementedException.class);
    }

    @Test
    void unimplementedHandlerBubblesThroughConsumerOverload() {
        FakeSdkClient client = Mokka.fake(FakeSdkClient.class, "Fake");
        // The consumer overload delegates to invokeDefault, which builds the request
        // and re-invokes the concrete overload via the proxy — which hits the
        // UnimplementedHandler and throws.
        assertThatThrownBy(() -> client.op(b -> b.name("x")))
            .isInstanceOf(MokkaUnimplementedException.class);
    }
}
