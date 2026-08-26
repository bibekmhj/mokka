package dev.mokka.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test field to be auto-injected with a mokka fake of that field's type.
 *
 * <pre>{@code
 * @ExtendWith(MokkaExtension.class)
 * class RefundServiceTest {
 *     @MokkaClient S3Client s3;
 *     @MokkaClient DynamoDbClient ddb;
 *     @MokkaClient SqsClient sqs;
 *
 *     @Test
 *     void refundWritesAuditAndPublishesEvent() {
 *         // s3 / ddb / sqs are fresh mokka fakes here — reset between every @Test.
 *     }
 * }
 * }</pre>
 *
 * <p>The extension discovers per-service fakes via {@link java.util.ServiceLoader}
 * over {@code MokkaFakeProvider} — no per-service configuration in the test code.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MokkaClient {
}
