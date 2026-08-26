package dev.mokka.s3;

import dev.mokka.core.FakeInstance;
import dev.mokka.core.Mokka;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Entry point for the in-process S3 fake.
 *
 * <pre>{@code
 * S3Fake fake = S3Fake.create();
 * S3Client s3 = fake.client();
 *
 * s3.createBucket(b -> b.bucket("photos"));
 * s3.putObject(r -> r.bucket("photos").key("hi.txt"), RequestBody.fromString("hello"));
 * String body = s3.getObjectAsBytes(r -> r.bucket("photos").key("hi.txt")).asUtf8String();
 * }</pre>
 *
 * <p>All state lives in memory. Call {@link #reset()} between tests (the JUnit 5
 * extension does this automatically).
 */
public final class S3Fake implements FakeInstance {

    private final S3State state;
    private final S3Handler handler;
    private final S3Client client;

    private S3Fake() {
        this.state = new S3State();
        this.handler = new S3Handler(state);
        this.client = Mokka.wrap(S3Client.class, handler);
    }

    /** Create a fresh S3 fake with empty state. */
    public static S3Fake create() {
        return new S3Fake();
    }

    /** The proxied {@link S3Client} instance to inject into code under test. */
    public S3Client client() {
        return client;
    }

    /** The underlying handler — exposed for advanced test setup and fault injection. */
    public S3Handler handler() {
        return handler;
    }

    /** Wipe every bucket and object. */
    public void reset() {
        handler.reset();
    }
}
