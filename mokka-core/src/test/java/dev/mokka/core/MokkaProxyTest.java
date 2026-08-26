package dev.mokka.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Core proxy tests use a synthetic client interface so the core module has no
 * AWS SDK dependency. The real AWS-SDK-backed contract lives in each per-service
 * module's test suite.
 */
class MokkaProxyTest {

    interface FakeClient {
        String someOperation(String arg);
        void close();
        String serviceName();
    }

    @Test
    void wrapsInterfaceWithProvidedHandler() {
        ServiceHandler handler = new ServiceHandler() {
            @Override public String serviceName() { return "Fake"; }
            @Override public Object handle(Method method, Object[] args) {
                return "handled:" + method.getName() + ":" + args[0];
            }
            @Override public void reset() {}
        };

        FakeClient fake = MokkaProxy.wrap(FakeClient.class, handler);

        assertThat(fake.someOperation("hi")).isEqualTo("handled:someOperation:hi");
    }

    @Test
    void closeAlwaysSucceeds() {
        FakeClient fake = Mokka.fake(FakeClient.class, "Fake");
        // Must not throw even though the handler would refuse it.
        fake.close();
    }

    @Test
    void serviceNameReturnsHandlerName() {
        FakeClient fake = Mokka.fake(FakeClient.class, "Fake");
        assertThat(fake.serviceName()).isEqualTo("Fake");
    }

    @Test
    void unimplementedThrowsWithHelpfulLink() {
        FakeClient fake = Mokka.fake(FakeClient.class, "Fake");

        assertThatThrownBy(() -> fake.someOperation("hi"))
            .isInstanceOf(MokkaUnimplementedException.class)
            .hasMessageContaining("Fake.someOperation")
            .hasMessageContaining("ROADMAP.md")
            .hasMessageContaining("github.com/bibekmhj/mokka");
    }

    @Test
    void toStringIdentifiesFake() {
        FakeClient fake = Mokka.fake(FakeClient.class, "Fake");
        assertThat(fake.toString()).isEqualTo("MokkaFake(FakeClient)");
    }

    @Test
    void proxyingConcreteClassIsRejected() {
        assertThatThrownBy(() -> Mokka.fake(String.class, "not-an-interface"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("can only proxy interfaces");
    }

    @Test
    void unimplementedExceptionCarriesServiceAndOperation() {
        MokkaUnimplementedException ex = new MokkaUnimplementedException("S3", "restoreObject");
        assertThat(ex.service()).isEqualTo("S3");
        assertThat(ex.operation()).isEqualTo("restoreObject");
    }
}
