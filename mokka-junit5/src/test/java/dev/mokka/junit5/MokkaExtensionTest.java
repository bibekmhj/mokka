package dev.mokka.junit5;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MokkaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MokkaExtensionTest {

    @MokkaClient S3Client s3;

    @Test
    @Order(1)
    void injectsAndClientWorks() {
        assertThat(s3).isNotNull();
        s3.createBucket(b -> b.bucket("test"));
        s3.putObject(r -> r.bucket("test").key("k"), RequestBody.fromString("v"));
        assertThat(s3.getObjectAsBytes(r -> r.bucket("test").key("k")).asUtf8String())
            .isEqualTo("v");
    }

    @Test
    @Order(2)
    void stateIsResetBetweenTests() {
        // Bucket created in test 1 must not exist here.
        assertThatThrownBy(() -> s3.headBucket(b -> b.bucket("test")))
            .isInstanceOf(NoSuchBucketException.class);
    }
}
