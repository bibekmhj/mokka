package dev.mokka.dynamodb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamoFakeTest {

    private DynamoFake fake;
    private DynamoDbClient ddb;

    @BeforeEach
    void setUp() {
        fake = DynamoFake.create();
        ddb = fake.client();
        ddb.createTable(r -> r.tableName("Users")
            .keySchema(k -> k.attributeName("id").keyType(KeyType.HASH))
            .attributeDefinitions(a -> a.attributeName("id").attributeType("S"))
            .billingMode(software.amazon.awssdk.services.dynamodb.model.BillingMode.PAY_PER_REQUEST));
    }

    @Test
    void putThenGetItemRoundTrip() {
        ddb.putItem(r -> r.tableName("Users").item(Map.of(
            "id", AttributeValue.fromS("u1"),
            "name", AttributeValue.fromS("Ada"))));
        GetItemResponse got = ddb.getItem(r -> r.tableName("Users")
            .key(Map.of("id", AttributeValue.fromS("u1"))));
        assertThat(got.item()).containsEntry("name", AttributeValue.fromS("Ada"));
    }

    @Test
    void getItemMissingReturnsEmptyMap() {
        GetItemResponse got = ddb.getItem(r -> r.tableName("Users")
            .key(Map.of("id", AttributeValue.fromS("nope"))));
        assertThat(got.hasItem()).isFalse();
    }

    @Test
    void deleteItemRemovesRow() {
        ddb.putItem(r -> r.tableName("Users").item(Map.of(
            "id", AttributeValue.fromS("u1"),
            "name", AttributeValue.fromS("Ada"))));
        ddb.deleteItem(r -> r.tableName("Users")
            .key(Map.of("id", AttributeValue.fromS("u1"))));
        assertThat(ddb.getItem(r -> r.tableName("Users")
            .key(Map.of("id", AttributeValue.fromS("u1")))).hasItem()).isFalse();
    }

    @Test
    void scanReturnsAllItems() {
        for (int i = 0; i < 3; i++) {
            int idx = i;
            ddb.putItem(r -> r.tableName("Users").item(Map.of(
                "id", AttributeValue.fromS("u" + idx))));
        }
        ScanResponse scan = ddb.scan(r -> r.tableName("Users"));
        assertThat(scan.count()).isEqualTo(3);
    }

    @Test
    void queryReturnsItemsMatchingPartitionKey() {
        ddb.createTable(r -> r.tableName("Events")
            .keySchema(
                k -> k.attributeName("user_id").keyType(KeyType.HASH),
                k -> k.attributeName("ts").keyType(KeyType.RANGE))
            .attributeDefinitions(
                a -> a.attributeName("user_id").attributeType("S"),
                a -> a.attributeName("ts").attributeType("S"))
            .billingMode(software.amazon.awssdk.services.dynamodb.model.BillingMode.PAY_PER_REQUEST));

        ddb.putItem(r -> r.tableName("Events").item(Map.of(
            "user_id", AttributeValue.fromS("u1"),
            "ts", AttributeValue.fromS("2026-08-25T10:00:00Z"),
            "action", AttributeValue.fromS("login"))));
        ddb.putItem(r -> r.tableName("Events").item(Map.of(
            "user_id", AttributeValue.fromS("u1"),
            "ts", AttributeValue.fromS("2026-08-25T11:00:00Z"),
            "action", AttributeValue.fromS("purchase"))));
        ddb.putItem(r -> r.tableName("Events").item(Map.of(
            "user_id", AttributeValue.fromS("u2"),
            "ts", AttributeValue.fromS("2026-08-25T12:00:00Z"),
            "action", AttributeValue.fromS("login"))));

        QueryResponse q = ddb.query(r -> r.tableName("Events")
            .keyConditionExpression("user_id = :u")
            .expressionAttributeValues(Map.of(":u", AttributeValue.fromS("u1"))));
        assertThat(q.count()).isEqualTo(2);
    }

    @Test
    void queryUnknownTableThrowsResourceNotFound() {
        assertThatThrownBy(() -> ddb.query(r -> r.tableName("Ghost")
            .keyConditionExpression("id = :u")
            .expressionAttributeValues(Map.of(":u", AttributeValue.fromS("x")))))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void describeTableReturnsSchema() {
        var desc = ddb.describeTable(r -> r.tableName("Users")).table();
        assertThat(desc.tableName()).isEqualTo("Users");
        assertThat(desc.keySchema()).hasSize(1);
        assertThat(desc.keySchema().get(0).attributeName()).isEqualTo("id");
    }

    @Test
    void listTablesReturnsCreatedTables() {
        assertThat(ddb.listTables().tableNames()).contains("Users");
    }

    @Test
    void resetWipesAllTables() {
        fake.reset();
        assertThat(ddb.listTables().tableNames()).isEmpty();
    }
}
