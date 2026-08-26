package dev.mokka.secretsmanager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.ResourceExistsException;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretsFakeTest {

    private SecretsFake fake;
    private SecretsManagerClient sm;

    @BeforeEach
    void setUp() {
        fake = SecretsFake.create();
        sm = fake.client();
    }

    @Test
    void createThenGetRoundTrip() {
        sm.createSecret(r -> r.name("db/prod/password").secretString("hunter2"));
        var got = sm.getSecretValue(r -> r.secretId("db/prod/password"));
        assertThat(got.secretString()).isEqualTo("hunter2");
        assertThat(got.name()).isEqualTo("db/prod/password");
        assertThat(got.arn()).startsWith("arn:aws:secretsmanager:");
    }

    @Test
    void putSecretValueRotatesValue() {
        var created = sm.createSecret(r -> r.name("s").secretString("v1"));
        String v1 = created.versionId();
        sm.putSecretValue(r -> r.secretId("s").secretString("v2"));
        var got = sm.getSecretValue(r -> r.secretId("s"));
        assertThat(got.secretString()).isEqualTo("v2");
        assertThat(got.versionId()).isNotEqualTo(v1);
    }

    @Test
    void updateSecretChangesValue() {
        sm.createSecret(r -> r.name("s").secretString("v1"));
        sm.updateSecret(r -> r.secretId("s").secretString("v2"));
        assertThat(sm.getSecretValue(r -> r.secretId("s")).secretString()).isEqualTo("v2");
    }

    @Test
    void duplicateCreateThrows() {
        sm.createSecret(r -> r.name("s").secretString("v"));
        assertThatThrownBy(() -> sm.createSecret(r -> r.name("s").secretString("v2")))
            .isInstanceOf(ResourceExistsException.class);
    }

    @Test
    void unknownSecretThrows() {
        assertThatThrownBy(() -> sm.getSecretValue(r -> r.secretId("nope")))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listSecretsReturnsAll() {
        sm.createSecret(r -> r.name("a").secretString("1"));
        sm.createSecret(r -> r.name("b").secretString("2"));
        var list = sm.listSecrets(r -> {});
        assertThat(list.secretList()).extracting("name").containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void resetWipes() {
        sm.createSecret(r -> r.name("a").secretString("1"));
        fake.reset();
        assertThat(sm.listSecrets(r -> {}).secretList()).isEmpty();
    }
}
