package dev.mokka.s3;

import dev.mokka.core.FakeInstance;
import dev.mokka.core.MokkaFakeProvider;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Discovered by ServiceLoader — teaches the JUnit 5 extension and the Spring Boot
 * starter that mokka-s3 can fake {@link S3Client} fields.
 */
public final class S3FakeProvider implements MokkaFakeProvider {

    @Override
    public boolean supports(Class<?> clientType) {
        return clientType == S3Client.class;
    }

    @Override
    public String serviceName() {
        return "S3";
    }

    @Override
    public FakeInstance create() {
        return S3Fake.create();
    }
}
