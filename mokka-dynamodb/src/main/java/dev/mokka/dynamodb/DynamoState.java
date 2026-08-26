package dev.mokka.dynamodb;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory state for the DynamoDB fake.
 *
 * <p>Each table has a {@link TableSchema key schema} and holds items keyed by a
 * composite key built from the item's partition (and optional sort) attribute
 * values.
 */
final class DynamoState {

    private final Map<String, FakeTable> tables = new ConcurrentHashMap<>();

    /** Create a table. Returns {@code true} if the table did not already exist. */
    boolean createTable(String name, TableSchema schema) {
        return tables.putIfAbsent(name, new FakeTable(name, schema)) == null;
    }

    FakeTable table(String name) {
        FakeTable t = tables.get(name);
        if (t == null) {
            throw new UnknownTableMarker(name);
        }
        return t;
    }

    boolean tableExists(String name) {
        return tables.containsKey(name);
    }

    Collection<String> tableNames() {
        return List.copyOf(tables.keySet());
    }

    void reset() {
        tables.clear();
    }

    /** Composite key for an item: (partitionValue) or (partitionValue, sortValue). */
    static final class ItemKey {
        private final AttributeValue partition;
        private final AttributeValue sort;

        ItemKey(AttributeValue partition, AttributeValue sort) {
            this.partition = partition;
            this.sort = sort;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ItemKey k)) return false;
            return Objects.equals(partition, k.partition) && Objects.equals(sort, k.sort);
        }

        @Override
        public int hashCode() {
            return Objects.hash(partition, sort);
        }

        @Override
        public String toString() {
            return "(" + partition + (sort != null ? "," + sort : "") + ")";
        }
    }

    static final class TableSchema {
        final String partitionKey;
        final String sortKey; // nullable

        TableSchema(String partitionKey, String sortKey) {
            this.partitionKey = partitionKey;
            this.sortKey = sortKey;
        }
    }

    static final class FakeTable {
        final String name;
        final TableSchema schema;
        final Map<ItemKey, Map<String, AttributeValue>> items = new ConcurrentHashMap<>();
        // Insertion-ordered list of partition-key values in the order they were inserted —
        // stable listing for scan / query without needing to hash-sort.
        final List<ItemKey> insertionOrder = Collections.synchronizedList(new ArrayList<>());

        FakeTable(String name, TableSchema schema) {
            this.name = name;
            this.schema = schema;
        }

        ItemKey keyOf(Map<String, AttributeValue> item) {
            AttributeValue pk = item.get(schema.partitionKey);
            if (pk == null) {
                throw new IllegalArgumentException(
                    "Item missing partition key attribute: " + schema.partitionKey);
            }
            AttributeValue sk = schema.sortKey == null ? null : item.get(schema.sortKey);
            return new ItemKey(pk, sk);
        }

        void put(Map<String, AttributeValue> item) {
            ItemKey key = keyOf(item);
            if (items.put(key, new LinkedHashMap<>(item)) == null) {
                insertionOrder.add(key);
            }
        }

        Map<String, AttributeValue> get(ItemKey key) {
            Map<String, AttributeValue> item = items.get(key);
            return item == null ? null : new LinkedHashMap<>(item);
        }

        void delete(ItemKey key) {
            if (items.remove(key) != null) {
                insertionOrder.remove(key);
            }
        }
    }

    /** Sentinel — the handler turns this into ResourceNotFoundException. */
    static final class UnknownTableMarker extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String table;
        UnknownTableMarker(String table) { this.table = table; }
    }
}
