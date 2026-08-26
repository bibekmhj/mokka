package dev.mokka.secretsmanager;

import dev.mokka.core.MokkaUnimplementedException;
import dev.mokka.core.ServiceHandler;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.DescribeSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.DescribeSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsResponse;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceExistsException;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry;
import software.amazon.awssdk.services.secretsmanager.model.UpdateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.UpdateSecretResponse;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Fake behavior for Secrets Manager SDK operations mokka v0.1 supports:
 * {@code createSecret}, {@code getSecretValue}, {@code putSecretValue},
 * {@code updateSecret}, {@code describeSecret}, {@code listSecrets}.
 */
public final class SecretsHandler implements ServiceHandler {

    private final SecretsState state;

    public SecretsHandler(SecretsState state) {
        this.state = state;
    }

    @Override public String serviceName() { return "SecretsManager"; }
    @Override public void reset() { state.reset(); }

    @Override
    public Object handle(Method method, Object[] args) {
        try {
            return switch (method.getName()) {
                case "createSecret" -> createSecret(args);
                case "getSecretValue" -> getSecretValue(args);
                case "putSecretValue" -> putSecretValue(args);
                case "updateSecret" -> updateSecret(args);
                case "describeSecret" -> describeSecret(args);
                case "listSecrets" -> listSecrets(args);
                default -> throw new MokkaUnimplementedException("SecretsManager", method.getName());
            };
        } catch (SecretsState.NotFoundMarker missing) {
            throw ResourceNotFoundException.builder()
                .message("Secrets Manager can't find the specified secret: " + missing.id)
                .build();
        } catch (SecretsState.AlreadyExistsMarker exists) {
            throw ResourceExistsException.builder()
                .message("The operation failed because the secret " + exists.name + " already exists.")
                .build();
        }
    }

    private CreateSecretResponse createSecret(Object[] args) {
        CreateSecretRequest request = (CreateSecretRequest) args[0];
        SecretsState.FakeSecret secret = state.createSecret(
            request.name(), request.secretString(), request.secretBinary());
        return CreateSecretResponse.builder()
            .arn(secret.arn)
            .name(secret.name)
            .versionId(secret.versionId)
            .build();
    }

    private GetSecretValueResponse getSecretValue(Object[] args) {
        GetSecretValueRequest request = (GetSecretValueRequest) args[0];
        SecretsState.FakeSecret secret = state.get(request.secretId());
        GetSecretValueResponse.Builder b = GetSecretValueResponse.builder()
            .arn(secret.arn)
            .name(secret.name)
            .versionId(secret.versionId)
            .createdDate(secret.createdDate);
        if (secret.stringValue != null) b.secretString(secret.stringValue);
        if (secret.binaryValue != null) b.secretBinary(secret.binaryValue);
        return b.build();
    }

    private PutSecretValueResponse putSecretValue(Object[] args) {
        PutSecretValueRequest request = (PutSecretValueRequest) args[0];
        SecretsState.FakeSecret secret = state.get(request.secretId());
        secret.putValue(request.secretString(), request.secretBinary());
        return PutSecretValueResponse.builder()
            .arn(secret.arn)
            .name(secret.name)
            .versionId(secret.versionId)
            .build();
    }

    private UpdateSecretResponse updateSecret(Object[] args) {
        UpdateSecretRequest request = (UpdateSecretRequest) args[0];
        SecretsState.FakeSecret secret = state.get(request.secretId());
        if (request.secretString() != null || request.secretBinary() != null) {
            secret.putValue(request.secretString(), request.secretBinary());
        }
        return UpdateSecretResponse.builder()
            .arn(secret.arn)
            .name(secret.name)
            .versionId(secret.versionId)
            .build();
    }

    private DescribeSecretResponse describeSecret(Object[] args) {
        DescribeSecretRequest request = (DescribeSecretRequest) args[0];
        SecretsState.FakeSecret secret = state.get(request.secretId());
        return DescribeSecretResponse.builder()
            .arn(secret.arn)
            .name(secret.name)
            .createdDate(secret.createdDate)
            .lastChangedDate(secret.lastChangedDate)
            .build();
    }

    private ListSecretsResponse listSecrets(Object[] args) {
        List<SecretListEntry> entries = new ArrayList<>();
        for (SecretsState.FakeSecret s : state.all()) {
            entries.add(SecretListEntry.builder()
                .arn(s.arn)
                .name(s.name)
                .createdDate(s.createdDate)
                .lastChangedDate(s.lastChangedDate)
                .build());
        }
        return ListSecretsResponse.builder().secretList(entries).build();
    }
}
