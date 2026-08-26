package dev.mokka.secretsmanager;

import dev.mokka.core.FakeInstance;
import dev.mokka.core.MokkaFakeProvider;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

public final class SecretsFakeProvider implements MokkaFakeProvider {

    @Override
    public boolean supports(Class<?> clientType) {
        return clientType == SecretsManagerClient.class;
    }

    @Override
    public String serviceName() {
        return "SecretsManager";
    }

    @Override
    public FakeInstance create() {
        return SecretsFake.create();
    }
}
