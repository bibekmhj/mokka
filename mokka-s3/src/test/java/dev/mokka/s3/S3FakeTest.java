package dev.mokka.s3;

import dev.mokka.core.MokkaUnimplementedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3FakeTest {

    private S3Fake fake;
    private S3Client s3;

    @BeforeEach
    void setUp() {
        fake = S3Fake.create();
        s3 = fake.client();
    }

    @Test
    void putThenGetRoundTrip() {
        s3.createBucket(b -> b.bucket("photos"));
        s3.putObject(r -> r.bucket("photos").key("hi.txt").contentType("text/plain"),
                     RequestBody.fromString("hello"));

        ResponseBytes<GetObjectResponse> got =
            s3.getObjectAsBytes(r -> r.bucket("photos").key("hi.txt"));

        assertThat(got.asUtf8String()).isEqualTo("hello");
        assertThat(got.response().contentType()).isEqualTo("text/plain");
        assertThat(got.response().contentLength()).isEqualTo(5L);
        assertThat(got.response().eTag()).isNotNull();
    }

    @Test
    void putAutoCreatesBucket() {
        s3.putObject(r -> r.bucket("auto-created").key("x"),
                     RequestBody.fromString("ok"));
        assertThat(s3.getObjectAsBytes(r -> r.bucket("auto-created").key("x")).asUtf8String())
            .isEqualTo("ok");
    }

    @Test
    void headObjectReturnsMetadata() {
        s3.putObject(r -> r.bucket("b").key("k").contentType("application/json"),
                     RequestBody.fromString("{}"));
        HeadObjectResponse head = s3.headObject(r -> r.bucket("b").key("k"));
        assertThat(head.contentType()).isEqualTo("application/json");
        assertThat(head.contentLength()).isEqualTo(2L);
    }

    @Test
    void headObjectMissingThrowsNoSuchKey() {
        s3.createBucket(b -> b.bucket("b"));
        assertThatThrownBy(() -> s3.headObject(r -> r.bucket("b").key("missing")))
            .isInstanceOf(NoSuchKeyException.class);
    }

    @Test
    void headBucketMissingThrowsNoSuchBucket() {
        assertThatThrownBy(() -> s3.headBucket(b -> b.bucket("nope")))
            .isInstanceOf(NoSuchBucketException.class);
    }

    @Test
    void deleteObjectRemovesAndSubsequentGetFails() {
        s3.putObject(r -> r.bucket("b").key("k"), RequestBody.fromString("bye"));
        s3.deleteObject(r -> r.bucket("b").key("k"));
        assertThatThrownBy(() -> s3.getObjectAsBytes(r -> r.bucket("b").key("k")))
            .isInstanceOf(NoSuchKeyException.class);
    }

    @Test
    void deleteMissingObjectIsNoop() {
        s3.createBucket(b -> b.bucket("b"));
        // Does not throw — matches real S3 semantics.
        s3.deleteObject(r -> r.bucket("b").key("never-existed"));
    }

    @Test
    void listObjectsV2ReturnsAllInPrefix() {
        for (String k : new String[]{"a/1", "a/2", "b/1"}) {
            s3.putObject(r -> r.bucket("bkt").key(k), RequestBody.fromString(k));
        }
        ListObjectsV2Response list = s3.listObjectsV2(r -> r.bucket("bkt").prefix("a/"));
        assertThat(list.keyCount()).isEqualTo(2);
        assertThat(list.contents()).extracting("key").containsExactlyInAnyOrder("a/1", "a/2");
    }

    @Test
    void listObjectsV2RespectsMaxKeys() {
        for (int i = 0; i < 10; i++) {
            int idx = i;
            s3.putObject(r -> r.bucket("bkt").key("k" + idx), RequestBody.fromString("x"));
        }
        ListObjectsV2Response list = s3.listObjectsV2(r -> r.bucket("bkt").maxKeys(3));
        assertThat(list.keyCount()).isEqualTo(3);
    }

    @Test
    void resetWipesEverything() {
        s3.putObject(r -> r.bucket("b").key("k"), RequestBody.fromString("x"));
        fake.reset();
        assertThatThrownBy(() -> s3.headBucket(b -> b.bucket("b")))
            .isInstanceOf(NoSuchBucketException.class);
    }

    @Test
    void unimplementedOperationThrowsWithLink() {
        s3.createBucket(b -> b.bucket("b"));
        assertThatThrownBy(() -> s3.restoreObject(r -> r.bucket("b").key("k")))
            .isInstanceOf(MokkaUnimplementedException.class)
            .hasMessageContaining("S3.restoreObject")
            .hasMessageContaining("ROADMAP.md");
    }

    @Test
    void s3ClientCloseNeverThrows() {
        s3.close();
    }
}
