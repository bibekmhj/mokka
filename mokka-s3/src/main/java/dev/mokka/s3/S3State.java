package dev.mokka.s3;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Thread-safe in-memory state for the S3 fake.
 *
 * <p>Objects are keyed by bucket name and then by object key. Within a bucket the
 * keys are kept in a {@link ConcurrentSkipListMap} so listObjectsV2 can implement
 * the prefix / start-after semantics without extra sorting.
 */
final class S3State {

    private final Map<String, ConcurrentSkipListMap<String, StoredObject>> buckets =
        new ConcurrentHashMap<>();

    /** Create a bucket. Returns {@code true} if the bucket did not already exist. */
    boolean createBucket(String name) {
        return buckets.putIfAbsent(name, new ConcurrentSkipListMap<>()) == null;
    }

    boolean bucketExists(String name) {
        return buckets.containsKey(name);
    }

    /** Put an object. Returns the ETag of the stored object. */
    String putObject(String bucket, String key, StoredObject object) {
        requireBucket(bucket).put(key, object);
        return object.eTag;
    }

    /** Get an object or {@code null} if the object or bucket does not exist. */
    StoredObject getObject(String bucket, String key) {
        ConcurrentSkipListMap<String, StoredObject> b = buckets.get(bucket);
        return b == null ? null : b.get(key);
    }

    /** Delete an object. No-op if it does not exist (matches real S3 semantics). */
    void deleteObject(String bucket, String key) {
        ConcurrentSkipListMap<String, StoredObject> b = buckets.get(bucket);
        if (b != null) {
            b.remove(key);
        }
    }

    /**
     * List keys in a bucket, optionally filtered by {@code prefix}, starting after
     * {@code startAfter} (exclusive) if non-null. Returns at most {@code maxKeys}
     * entries in insertion / natural order.
     */
    NavigableMap<String, StoredObject> listObjects(String bucket,
                                                   String prefix,
                                                   String startAfter,
                                                   int maxKeys) {
        ConcurrentSkipListMap<String, StoredObject> b = buckets.get(bucket);
        if (b == null) {
            return Collections.emptyNavigableMap();
        }
        NavigableMap<String, StoredObject> tail =
            startAfter == null ? b : b.tailMap(startAfter, false);

        TreeMap<String, StoredObject> out = new TreeMap<>();
        int count = 0;
        for (Map.Entry<String, StoredObject> e : tail.entrySet()) {
            if (count >= maxKeys) break;
            if (prefix != null && !e.getKey().startsWith(prefix)) continue;
            out.put(e.getKey(), e.getValue());
            count++;
        }
        return out;
    }

    /** Wipe every bucket and every object. Called between tests. */
    void reset() {
        buckets.clear();
    }

    private ConcurrentSkipListMap<String, StoredObject> requireBucket(String bucket) {
        ConcurrentSkipListMap<String, StoredObject> b = buckets.get(bucket);
        if (b == null) {
            throw new NoSuchBucketMarker(bucket);
        }
        return b;
    }

    /** Value type for a stored object. */
    static final class StoredObject {
        final byte[] data;
        final String contentType;
        final Map<String, String> metadata;
        final Instant lastModified;
        final String eTag;

        StoredObject(byte[] data,
                     String contentType,
                     Map<String, String> metadata) {
            this.data = data;
            this.contentType = contentType == null ? "application/octet-stream" : contentType;
            this.metadata = metadata == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
            this.lastModified = Instant.now();
            this.eTag = "\"" + hex(data) + "\"";
        }

        int size() {
            return data.length;
        }

        private static String hex(byte[] bytes) {
            // Cheap deterministic ETag — not real MD5 to avoid FIPS surprises in tests.
            long h = 1125899906842597L;
            for (byte b : bytes) h = 31 * h + b;
            return Long.toHexString(h);
        }
    }

    /** Sentinel used inside the state so the handler can translate to NoSuchBucketException. */
    static final class NoSuchBucketMarker extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String bucket;
        NoSuchBucketMarker(String bucket) { this.bucket = bucket; }
    }
}
