package dev.mokka.sqs;

import dev.mokka.core.FakeInstance;
import dev.mokka.core.MokkaFakeProvider;
import software.amazon.awssdk.services.sqs.SqsClient;

public final class SqsFakeProvider implements MokkaFakeProvider {

    @Override
    public boolean supports(Class<?> clientType) {
        return clientType == SqsClient.class;
    }

    @Override
    public String serviceName() {
        return "SQS";
    }

    @Override
    public FakeInstance create() {
        return SqsFake.create();
    }
}
