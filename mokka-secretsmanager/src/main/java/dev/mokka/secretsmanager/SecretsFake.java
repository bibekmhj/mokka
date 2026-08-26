package dev.mokka.secretsmanager;

import dev.mokka.core.FakeInstance;
import dev.mokka.core.Mokka;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Entry point for the in-process Secrets Manager fake.
 *
 * <pre>{@code
 * SecretsFake fake = SecretsFake.create();
 * SecretsManagerClient sm = fake.client();
 *
 * sm.createSecret(r -> r.name("db/prod/password").secretString("hunter2"));
 * String value = sm.getSecretValue(r -> r.secretId("db/prod/password")).secretString();
 * }</pre>
 *
 * <p>v0.1 keeps only the current version of each secret. Version staging labels
 * and rotation are on the roadmap.
 */
public final class SecretsFake implements FakeInstance {

    private final SecretsState state;
    private final SecretsHandler handler;
    private final SecretsManagerClient client;

    private SecretsFake() {
        this.state = new SecretsState();
        this.handler = new SecretsHandler(state);
        this.client = Mokka.wrap(SecretsManagerClient.class, handler);
    }

    public static SecretsFake create() {
        return new SecretsFake();
    }

    public SecretsManagerClient client() {
        return client;
    }

    public SecretsHandler handler() {
        return handler;
    }

    public void reset() {
        handler.reset();
    }
}
