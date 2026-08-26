package dev.mokka.secretsmanager;

import software.amazon.awssdk.core.SdkBytes;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory state for the Secrets Manager fake.
 *
 * <p>Each secret has a name and a current version. v0.1 does not model version
 * staging labels beyond {@code AWSCURRENT}, or rotation. Adding versioning is
 * on the roadmap.
 */
final class SecretsState {

    static final String ARN_PREFIX = "arn:aws:secretsmanager:mokka-region:000000000000:secret:";

    private final Map<String, FakeSecret> secrets = new ConcurrentHashMap<>();

    FakeSecret createSecret(String name, String stringValue, SdkBytes binaryValue) {
        FakeSecret existing = secrets.get(name);
        if (existing != null) {
            throw new AlreadyExistsMarker(name);
        }
        FakeSecret secret = new FakeSecret(name);
        secret.putValue(stringValue, binaryValue);
        secrets.put(name, secret);
        return secret;
    }

    FakeSecret get(String id) {
        // Real SDK accepts either name or ARN — mokka accepts both.
        FakeSecret s = secrets.get(id);
        if (s != null) return s;
        if (id.startsWith(ARN_PREFIX)) {
            String name = id.substring(ARN_PREFIX.length()).replaceAll("-[A-Za-z0-9]{6}$", "");
            s = secrets.get(name);
            if (s != null) return s;
        }
        throw new NotFoundMarker(id);
    }

    Collection<FakeSecret> all() {
        return List.copyOf(secrets.values());
    }

    void reset() {
        secrets.clear();
    }

    static final class FakeSecret {
        final String name;
        final String arn;
        volatile String versionId;
        volatile String stringValue;
        volatile SdkBytes binaryValue;
        volatile Instant createdDate = Instant.now();
        volatile Instant lastChangedDate = Instant.now();

        FakeSecret(String name) {
            this.name = name;
            this.arn = ARN_PREFIX + name + "-" + UUID.randomUUID().toString().substring(0, 6);
        }

        void putValue(String stringValue, SdkBytes binaryValue) {
            this.versionId = UUID.randomUUID().toString();
            this.stringValue = stringValue;
            this.binaryValue = binaryValue;
            this.lastChangedDate = Instant.now();
        }
    }

    static final class NotFoundMarker extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String id;
        NotFoundMarker(String id) { this.id = id; }
    }

    static final class AlreadyExistsMarker extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String name;
        AlreadyExistsMarker(String name) { this.name = name; }
    }
}
