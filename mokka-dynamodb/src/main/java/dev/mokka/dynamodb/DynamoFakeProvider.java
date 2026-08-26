package dev.mokka.dynamodb;

import dev.mokka.core.FakeInstance;
import dev.mokka.core.MokkaFakeProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public final class DynamoFakeProvider implements MokkaFakeProvider {

    @Override
    public boolean supports(Class<?> clientType) {
        return clientType == DynamoDbClient.class;
    }

    @Override
    public String serviceName() {
        return "DynamoDB";
    }

    @Override
    public FakeInstance create() {
        return DynamoFake.create();
    }
}
