package dev.mokka.junit5;

import dev.mokka.core.FakeInstance;
import dev.mokka.core.MokkaFakeProvider;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * JUnit 5 extension that scans {@code @MokkaClient} fields, injects a matching
 * fake for each field type, and resets all fakes between tests.
 *
 * <p>Fakes are created lazily on first {@code beforeEach} of a test instance and
 * cached for the life of that instance — so field references stay stable — while
 * every {@code afterEach} wipes their in-memory state via
 * {@link FakeInstance#reset()}. This avoids re-allocating proxies for every test
 * while still guaranteeing state isolation.
 */
public final class MokkaExtension implements BeforeEachCallback, AfterEachCallback, Extension {

    private static final ExtensionContext.Namespace NS =
        ExtensionContext.Namespace.create(MokkaExtension.class);

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Object testInstance = context.getRequiredTestInstance();
        InstanceState state = state(context, testInstance);
        state.ensureInjected(testInstance);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        InstanceState state = context.getStore(NS)
            .get(context.getRequiredTestInstance(), InstanceState.class);
        if (state != null) {
            state.resetAll();
        }
    }

    private InstanceState state(ExtensionContext context, Object testInstance) {
        return context.getStore(NS).getOrComputeIfAbsent(
            testInstance,
            key -> new InstanceState(),
            InstanceState.class);
    }

    private static final class InstanceState {
        private final List<FakeInstance> fakes = new ArrayList<>();
        private final List<MokkaFakeProvider> providers = loadProviders();
        private boolean injected;

        void ensureInjected(Object testInstance) throws IllegalAccessException {
            if (injected) return;
            injected = true;
            Map<Class<?>, FakeInstance> byType = new HashMap<>();
            for (Field field : allFields(testInstance.getClass())) {
                if (!field.isAnnotationPresent(MokkaClient.class)) continue;
                Class<?> fieldType = field.getType();
                FakeInstance fake = byType.computeIfAbsent(fieldType, this::newFakeFor);
                field.setAccessible(true);
                field.set(testInstance, fake.client());
                if (!fakes.contains(fake)) fakes.add(fake);
            }
        }

        void resetAll() {
            for (FakeInstance fake : fakes) {
                fake.reset();
            }
        }

        private FakeInstance newFakeFor(Class<?> fieldType) {
            for (MokkaFakeProvider provider : providers) {
                if (provider.supports(fieldType)) {
                    return provider.create();
                }
            }
            throw new IllegalStateException(
                "mokka: no MokkaFakeProvider on the classpath supports "
                    + fieldType.getName() + ". Add the matching mokka-<service> dependency"
                    + " (e.g. mokka-s3, mokka-dynamodb, mokka-sqs, mokka-sns, mokka-secretsmanager).");
        }

        private static List<MokkaFakeProvider> loadProviders() {
            List<MokkaFakeProvider> list = new ArrayList<>();
            for (MokkaFakeProvider p : ServiceLoader.load(MokkaFakeProvider.class)) {
                list.add(p);
            }
            return list;
        }

        private static List<Field> allFields(Class<?> type) {
            List<Field> out = new ArrayList<>();
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    out.add(f);
                }
            }
            return out;
        }
    }
}
