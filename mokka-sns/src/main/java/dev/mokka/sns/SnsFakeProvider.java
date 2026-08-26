package dev.mokka.sns;

import dev.mokka.core.FakeInstance;
import dev.mokka.core.MokkaFakeProvider;
import software.amazon.awssdk.services.sns.SnsClient;

public final class SnsFakeProvider implements MokkaFakeProvider {

    @Override
    public boolean supports(Class<?> clientType) {
        return clientType == SnsClient.class;
    }

    @Override
    public String serviceName() {
        return "SNS";
    }

    @Override
    public FakeInstance create() {
        return SnsFake.create();
    }
}
