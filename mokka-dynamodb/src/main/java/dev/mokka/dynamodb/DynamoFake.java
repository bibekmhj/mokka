package dev.mokka.dynamodb;

import dev.mokka.core.FakeInstance;
import dev.mokka.core.Mokka;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Entry point for the in-process DynamoDB fake.
 *
 * <pre>{@code
 * DynamoFake fake = DynamoFake.create();
 * DynamoDbClient ddb = fake.client();
 *
 * ddb.createTable(r -> r.tableName("Users")
 *     .keySchema(k -> k.attributeName("id").keyType(KeyType.HASH))
 *     .attributeDefinitions(a -> a.attributeName("id").attributeType("S"))
 *     .billingMode(BillingMode.PAY_PER_REQUEST));
 *
 * ddb.putItem(r -> r.tableName("Users")
 *     .item(Map.of("id", AttributeValue.fromS("u1"),
 *                  "name", AttributeValue.fromS("Ada"))));
 *
 * GetItemResponse got = ddb.getItem(r -> r.tableName("Users")
 *     .key(Map.of("id", AttributeValue.fromS("u1"))));
 * }</pre>
 */
public final class DynamoFake implements FakeInstance {

    private final DynamoState state;
    private final DynamoHandler handler;
    private final DynamoDbClient client;

    private DynamoFake() {
        this.state = new DynamoState();
        this.handler = new DynamoHandler(state);
        this.client = Mokka.wrap(DynamoDbClient.class, handler);
    }

    public static DynamoFake create() {
        return new DynamoFake();
    }

    public DynamoDbClient client() {
        return client;
    }

    public DynamoHandler handler() {
        return handler;
    }

    public void reset() {
        handler.reset();
    }
}
