package dev.mokka.dynamodb;

import dev.mokka.core.MokkaUnimplementedException;
import dev.mokka.core.ServiceHandler;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fake behavior for the DynamoDB SDK client operations mokka v0.1 supports:
 * {@code createTable}, {@code describeTable}, {@code listTables},
 * {@code putItem}, {@code getItem}, {@code deleteItem}, {@code query}, {@code scan}.
 */
public final class DynamoHandler implements ServiceHandler {

    private final DynamoState state;

    public DynamoHandler(DynamoState state) {
        this.state = state;
    }

    @Override public String serviceName() { return "DynamoDB"; }
    @Override public void reset() { state.reset(); }

    @Override
    public Object handle(Method method, Object[] args) {
        try {
            return switch (method.getName()) {
                case "createTable" -> createTable(args);
                case "describeTable" -> describeTable(args);
                case "listTables" -> listTables(args);
                case "putItem" -> putItem(args);
                case "getItem" -> getItem(args);
                case "deleteItem" -> deleteItem(args);
                case "query" -> query(args);
                case "scan" -> scan(args);
                default -> throw new MokkaUnimplementedException("DynamoDB", method.getName());
            };
        } catch (DynamoState.UnknownTableMarker missing) {
            throw ResourceNotFoundException.builder()
                .message("Requested resource not found: Table: " + missing.table + " not found")
                .build();
        }
    }

    private CreateTableResponse createTable(Object[] args) {
        CreateTableRequest request = (CreateTableRequest) args[0];
        String partitionKey = null;
        String sortKey = null;
        for (KeySchemaElement k : request.keySchema()) {
            if (k.keyType() == KeyType.HASH) partitionKey = k.attributeName();
            else if (k.keyType() == KeyType.RANGE) sortKey = k.attributeName();
        }
        if (partitionKey == null) {
            throw new IllegalArgumentException("createTable: HASH key required");
        }
        boolean created = state.createTable(
            request.tableName(),
            new DynamoState.TableSchema(partitionKey, sortKey));
        if (!created) {
            throw ResourceInUseException.builder()
                .message("Table already exists: " + request.tableName())
                .build();
        }
        return CreateTableResponse.builder()
            .tableDescription(describe(request.tableName()))
            .build();
    }

    private DescribeTableResponse describeTable(Object[] args) {
        DescribeTableRequest request = (DescribeTableRequest) args[0];
        return DescribeTableResponse.builder().table(describe(request.tableName())).build();
    }

    private ListTablesResponse listTables(Object[] args) {
        return ListTablesResponse.builder()
            .tableNames(new ArrayList<>(state.tableNames()))
            .build();
    }

    private PutItemResponse putItem(Object[] args) {
        PutItemRequest request = (PutItemRequest) args[0];
        state.table(request.tableName()).put(request.item());
        return PutItemResponse.builder().build();
    }

    private GetItemResponse getItem(Object[] args) {
        GetItemRequest request = (GetItemRequest) args[0];
        DynamoState.FakeTable table = state.table(request.tableName());
        DynamoState.ItemKey key = keyFromRequest(table, request.key());
        Map<String, AttributeValue> item = table.get(key);
        GetItemResponse.Builder builder = GetItemResponse.builder();
        if (item != null) {
            builder.item(item);
        }
        return builder.build();
    }

    private DeleteItemResponse deleteItem(Object[] args) {
        DeleteItemRequest request = (DeleteItemRequest) args[0];
        DynamoState.FakeTable table = state.table(request.tableName());
        table.delete(keyFromRequest(table, request.key()));
        return DeleteItemResponse.builder().build();
    }

    private QueryResponse query(Object[] args) {
        QueryRequest request = (QueryRequest) args[0];
        DynamoState.FakeTable table = state.table(request.tableName());
        AttributeValue partitionValue = extractPartitionFromKeyConditions(request);
        List<Map<String, AttributeValue>> matches = new ArrayList<>();
        synchronized (table.insertionOrder) {
            for (DynamoState.ItemKey k : table.insertionOrder) {
                Map<String, AttributeValue> item = table.items.get(k);
                if (item == null) continue;
                AttributeValue pk = item.get(table.schema.partitionKey);
                if (partitionValue.equals(pk)) {
                    matches.add(new LinkedHashMap<>(item));
                }
            }
        }
        int limit = request.limit() == null ? matches.size() : Math.min(request.limit(), matches.size());
        List<Map<String, AttributeValue>> page = matches.subList(0, limit);
        return QueryResponse.builder()
            .items(page)
            .count(page.size())
            .scannedCount(matches.size())
            .build();
    }

    private ScanResponse scan(Object[] args) {
        ScanRequest request = (ScanRequest) args[0];
        DynamoState.FakeTable table = state.table(request.tableName());
        List<Map<String, AttributeValue>> all = new ArrayList<>();
        synchronized (table.insertionOrder) {
            for (DynamoState.ItemKey k : table.insertionOrder) {
                Map<String, AttributeValue> item = table.items.get(k);
                if (item != null) {
                    all.add(new LinkedHashMap<>(item));
                }
            }
        }
        int limit = request.limit() == null ? all.size() : Math.min(request.limit(), all.size());
        List<Map<String, AttributeValue>> page = all.subList(0, limit);
        return ScanResponse.builder()
            .items(page)
            .count(page.size())
            .scannedCount(all.size())
            .build();
    }

    private TableDescription describe(String tableName) {
        DynamoState.FakeTable table = state.table(tableName);
        List<KeySchemaElement> schema = new ArrayList<>();
        schema.add(KeySchemaElement.builder()
            .attributeName(table.schema.partitionKey)
            .keyType(KeyType.HASH).build());
        if (table.schema.sortKey != null) {
            schema.add(KeySchemaElement.builder()
                .attributeName(table.schema.sortKey)
                .keyType(KeyType.RANGE).build());
        }
        List<AttributeDefinition> defs = new ArrayList<>();
        defs.add(AttributeDefinition.builder()
            .attributeName(table.schema.partitionKey)
            .attributeType("S").build());
        if (table.schema.sortKey != null) {
            defs.add(AttributeDefinition.builder()
                .attributeName(table.schema.sortKey)
                .attributeType("S").build());
        }
        return TableDescription.builder()
            .tableName(tableName)
            .tableStatus(TableStatus.ACTIVE)
            .itemCount((long) table.items.size())
            .keySchema(schema)
            .attributeDefinitions(defs)
            .build();
    }

    private DynamoState.ItemKey keyFromRequest(DynamoState.FakeTable table,
                                               Map<String, AttributeValue> key) {
        AttributeValue pk = key.get(table.schema.partitionKey);
        if (pk == null) {
            throw new IllegalArgumentException(
                "Key missing partition attribute " + table.schema.partitionKey);
        }
        AttributeValue sk = table.schema.sortKey == null ? null : key.get(table.schema.sortKey);
        return new DynamoState.ItemKey(pk, sk);
    }

    /**
     * Extract the partition-key value from a query request. Supports both the modern
     * expression form ({@code keyConditionExpression + expressionAttributeValues})
     * and the legacy {@code keyConditions} form. Only equality on the partition key
     * is supported in v0.1 — filters and sort-key ranges are on the roadmap.
     */
    private AttributeValue extractPartitionFromKeyConditions(QueryRequest request) {
        Map<String, AttributeValue> values = request.expressionAttributeValues();
        if (values != null && !values.isEmpty()) {
            // Return the first :placeholder — for typical "pk = :v" this is the partition value.
            return values.values().iterator().next();
        }
        if (request.hasKeyConditions() && !request.keyConditions().isEmpty()) {
            return request.keyConditions().values().iterator().next()
                .attributeValueList().get(0);
        }
        throw new IllegalArgumentException(
            "query: could not extract partition key from request. mokka v0.1 supports "
                + "keyConditionExpression with expressionAttributeValues or the legacy "
                + "keyConditions map.");
    }
}
