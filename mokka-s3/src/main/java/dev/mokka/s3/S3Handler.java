package dev.mokka.s3;

import dev.mokka.core.MokkaUnimplementedException;
import dev.mokka.core.ServiceHandler;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fake behavior for the S3 SDK client operations mokka v0.1 supports.
 *
 * <p>Deep behavior:
 * <ul>
 *     <li>{@code createBucket}, {@code headBucket}</li>
 *     <li>{@code putObject}, {@code getObject}, {@code getObjectAsBytes}</li>
 *     <li>{@code deleteObject}, {@code headObject}</li>
 *     <li>{@code listObjectsV2}</li>
 * </ul>
 *
 * <p>Every other S3 operation throws {@link MokkaUnimplementedException} when called;
 * they still compile and can be dependency-injected today.
 */
public final class S3Handler implements ServiceHandler {

    private final S3State state;

    public S3Handler(S3State state) {
        this.state = state;
    }

    @Override
    public String serviceName() {
        return "S3";
    }

    @Override
    public void reset() {
        state.reset();
    }

    @Override
    public Object handle(Method method, Object[] args) {
        try {
            return switch (method.getName()) {
                case "createBucket" -> createBucket(args);
                case "headBucket" -> headBucket(args);
                case "putObject" -> putObject(args);
                case "getObject" -> getObject(method, args);
                case "getObjectAsBytes" -> getObjectAsBytes(args);
                case "headObject" -> headObject(args);
                case "deleteObject" -> deleteObject(args);
                case "listObjectsV2" -> listObjectsV2(args);
                default -> throw new MokkaUnimplementedException("S3", method.getName());
            };
        } catch (S3State.NoSuchBucketMarker missing) {
            throw NoSuchBucketException.builder()
                .message("The specified bucket does not exist: " + missing.bucket)
                .build();
        }
    }

    // --- Buckets ---------------------------------------------------------------------

    private CreateBucketResponse createBucket(Object[] args) {
        String bucket = ((CreateBucketRequest) args[0]).bucket();
        state.createBucket(bucket);
        return CreateBucketResponse.builder().location("/" + bucket).build();
    }

    private HeadBucketResponse headBucket(Object[] args) {
        String bucket = ((HeadBucketRequest) args[0]).bucket();
        if (!state.bucketExists(bucket)) {
            throw NoSuchBucketException.builder()
                .message("The specified bucket does not exist: " + bucket)
                .build();
        }
        return HeadBucketResponse.builder().build();
    }

    // --- Objects ---------------------------------------------------------------------

    private PutObjectResponse putObject(Object[] args) {
        PutObjectRequest request = (PutObjectRequest) args[0];
        Object body = args[1];
        byte[] data = readBody(body);
        S3State.StoredObject stored = new S3State.StoredObject(
            data, request.contentType(), request.metadata());
        // Auto-create bucket on first put — matches most testing expectations.
        state.createBucket(request.bucket());
        String eTag = state.putObject(request.bucket(), request.key(), stored);
        return PutObjectResponse.builder().eTag(eTag).build();
    }

    @SuppressWarnings("unchecked")
    private Object getObject(Method method, Object[] args) {
        GetObjectRequest request = (GetObjectRequest) args[0];
        S3State.StoredObject stored = state.getObject(request.bucket(), request.key());
        if (stored == null) {
            throw NoSuchKeyException.builder()
                .message("The specified key does not exist: " + request.key())
                .build();
        }
        GetObjectResponse response = toResponse(stored);
        InputStream in = new ByteArrayInputStream(stored.data);

        // getObject overloads:
        //   ResponseInputStream<GetObjectResponse> getObject(GetObjectRequest)
        //   <ReturnT> ReturnT getObject(GetObjectRequest, ResponseTransformer<GetObjectResponse,ReturnT>)
        if (args.length == 1) {
            return new ResponseInputStream<>(response, AbortableInputStream.create(in));
        }
        if (args[1] instanceof ResponseTransformer<?, ?> transformer) {
            try {
                return ((ResponseTransformer<GetObjectResponse, Object>) transformer)
                    .transform(response, AbortableInputStream.create(in));
            } catch (Exception e) {
                throw new RuntimeException("mokka: ResponseTransformer threw during getObject", e);
            }
        }
        throw new MokkaUnimplementedException("S3", method.getName() + " overload");
    }

    private ResponseBytes<GetObjectResponse> getObjectAsBytes(Object[] args) {
        GetObjectRequest request = (GetObjectRequest) args[0];
        S3State.StoredObject stored = state.getObject(request.bucket(), request.key());
        if (stored == null) {
            throw NoSuchKeyException.builder()
                .message("The specified key does not exist: " + request.key())
                .build();
        }
        return ResponseBytes.fromByteArray(toResponse(stored), stored.data);
    }

    private HeadObjectResponse headObject(Object[] args) {
        HeadObjectRequest request = (HeadObjectRequest) args[0];
        S3State.StoredObject stored = state.getObject(request.bucket(), request.key());
        if (stored == null) {
            throw NoSuchKeyException.builder()
                .message("The specified key does not exist: " + request.key())
                .build();
        }
        return HeadObjectResponse.builder()
            .contentLength((long) stored.size())
            .contentType(stored.contentType)
            .eTag(stored.eTag)
            .lastModified(stored.lastModified)
            .metadata(stored.metadata)
            .build();
    }

    private DeleteObjectResponse deleteObject(Object[] args) {
        DeleteObjectRequest request = (DeleteObjectRequest) args[0];
        state.deleteObject(request.bucket(), request.key());
        return DeleteObjectResponse.builder().build();
    }

    private ListObjectsV2Response listObjectsV2(Object[] args) {
        ListObjectsV2Request request = (ListObjectsV2Request) args[0];
        int maxKeys = request.maxKeys() == null ? 1000 : request.maxKeys();
        Map<String, S3State.StoredObject> found = state.listObjects(
            request.bucket(), request.prefix(), request.startAfter(), maxKeys);
        List<S3Object> contents = new ArrayList<>(found.size());
        for (Map.Entry<String, S3State.StoredObject> e : found.entrySet()) {
            contents.add(S3Object.builder()
                .key(e.getKey())
                .size((long) e.getValue().size())
                .eTag(e.getValue().eTag)
                .lastModified(e.getValue().lastModified)
                .build());
        }
        return ListObjectsV2Response.builder()
            .name(request.bucket())
            .prefix(request.prefix())
            .maxKeys(maxKeys)
            .keyCount(contents.size())
            .isTruncated(false)
            .contents(contents)
            .build();
    }

    // --- Helpers ---------------------------------------------------------------------

    private static GetObjectResponse toResponse(S3State.StoredObject stored) {
        return GetObjectResponse.builder()
            .contentLength((long) stored.size())
            .contentType(stored.contentType)
            .eTag(stored.eTag)
            .lastModified(stored.lastModified)
            .metadata(stored.metadata)
            .build();
    }

    private static byte[] readBody(Object body) {
        if (body instanceof RequestBody rb) {
            try (InputStream in = rb.contentStreamProvider().newStream()) {
                return in.readAllBytes();
            } catch (Exception e) {
                throw new RuntimeException("mokka: failed to read RequestBody", e);
            }
        }
        throw new MokkaUnimplementedException("S3", "putObject with body type " + body.getClass().getName());
    }
}
