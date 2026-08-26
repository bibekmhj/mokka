package dev.mokka.sqs;

import dev.mokka.core.MokkaUnimplementedException;
import dev.mokka.core.ServiceHandler;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Fake behavior for the SQS SDK client operations mokka v0.1 supports:
 * {@code createQueue}, {@code getQueueUrl}, {@code listQueues},
 * {@code sendMessage}, {@code receiveMessage}, {@code deleteMessage},
 * {@code changeMessageVisibility}, {@code getQueueAttributes}.
 */
public final class SqsHandler implements ServiceHandler {

    private static final int DEFAULT_VISIBILITY_SECONDS = 30;

    private final SqsState state;

    public SqsHandler(SqsState state) {
        this.state = state;
    }

    @Override public String serviceName() { return "SQS"; }
    @Override public void reset() { state.reset(); }

    @Override
    public Object handle(Method method, Object[] args) {
        try {
            return switch (method.getName()) {
                case "createQueue" -> createQueue(args);
                case "getQueueUrl" -> getQueueUrl(args);
                case "listQueues" -> listQueues(args);
                case "sendMessage" -> sendMessage(args);
                case "receiveMessage" -> receiveMessage(args);
                case "deleteMessage" -> deleteMessage(args);
                case "changeMessageVisibility" -> changeMessageVisibility(args);
                case "getQueueAttributes" -> getQueueAttributes(args);
                default -> throw new MokkaUnimplementedException("SQS", method.getName());
            };
        } catch (SqsState.UnknownQueueMarker missing) {
            throw QueueDoesNotExistException.builder()
                .message("The specified queue does not exist: " + missing.queueUrl)
                .build();
        }
    }

    private CreateQueueResponse createQueue(Object[] args) {
        CreateQueueRequest request = (CreateQueueRequest) args[0];
        return CreateQueueResponse.builder()
            .queueUrl(state.createQueue(request.queueName()))
            .build();
    }

    private GetQueueUrlResponse getQueueUrl(Object[] args) {
        GetQueueUrlRequest request = (GetQueueUrlRequest) args[0];
        if (!state.queueExists(request.queueName())) {
            throw QueueDoesNotExistException.builder()
                .message("Queue does not exist: " + request.queueName())
                .build();
        }
        return GetQueueUrlResponse.builder()
            .queueUrl(SqsState.URL_PREFIX + request.queueName())
            .build();
    }

    private ListQueuesResponse listQueues(Object[] args) {
        // v0.1 ignores the prefix filter.
        List<String> urls = new ArrayList<>();
        // No way to enumerate without adding a getter — use listTables-style API.
        // For v0.1, mokka's SqsState is intentionally small; iterate through the map.
        // We add a getter here via package access.
        for (String name : state.queueNames()) {
            urls.add(SqsState.URL_PREFIX + name);
        }
        return ListQueuesResponse.builder().queueUrls(urls).build();
    }

    private SendMessageResponse sendMessage(Object[] args) {
        SendMessageRequest request = (SendMessageRequest) args[0];
        SqsState.FakeQueue queue = state.queueByUrl(request.queueUrl());
        Map<String, Object> attributes = null;
        if (request.hasMessageAttributes()) {
            attributes = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, MessageAttributeValue> e : request.messageAttributes().entrySet()) {
                attributes.put(e.getKey(), e.getValue());
            }
        }
        String id = queue.send(request.messageBody(), attributes);
        return SendMessageResponse.builder().messageId(id).build();
    }

    private ReceiveMessageResponse receiveMessage(Object[] args) {
        ReceiveMessageRequest request = (ReceiveMessageRequest) args[0];
        SqsState.FakeQueue queue = state.queueByUrl(request.queueUrl());
        int max = request.maxNumberOfMessages() == null ? 1 : request.maxNumberOfMessages();
        int visibility = request.visibilityTimeout() == null
            ? DEFAULT_VISIBILITY_SECONDS
            : request.visibilityTimeout();
        List<SqsState.Message> received = queue.receive(max, visibility);
        List<Message> sdkMessages = new ArrayList<>(received.size());
        for (SqsState.Message m : received) {
            Message.Builder b = Message.builder()
                .messageId(m.id)
                .receiptHandle(m.receiptHandle)
                .body(m.body);
            if (!m.attributes.isEmpty()) {
                Map<String, MessageAttributeValue> mav = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, Object> e : m.attributes.entrySet()) {
                    if (e.getValue() instanceof MessageAttributeValue v) {
                        mav.put(e.getKey(), v);
                    }
                }
                if (!mav.isEmpty()) b.messageAttributes(mav);
            }
            sdkMessages.add(b.build());
        }
        return ReceiveMessageResponse.builder().messages(sdkMessages).build();
    }

    private DeleteMessageResponse deleteMessage(Object[] args) {
        DeleteMessageRequest request = (DeleteMessageRequest) args[0];
        SqsState.FakeQueue queue = state.queueByUrl(request.queueUrl());
        queue.delete(request.receiptHandle());
        return DeleteMessageResponse.builder().build();
    }

    private ChangeMessageVisibilityResponse changeMessageVisibility(Object[] args) {
        ChangeMessageVisibilityRequest request = (ChangeMessageVisibilityRequest) args[0];
        SqsState.FakeQueue queue = state.queueByUrl(request.queueUrl());
        queue.changeVisibility(request.receiptHandle(), request.visibilityTimeout());
        return ChangeMessageVisibilityResponse.builder().build();
    }

    private GetQueueAttributesResponse getQueueAttributes(Object[] args) {
        GetQueueAttributesRequest request = (GetQueueAttributesRequest) args[0];
        SqsState.FakeQueue queue = state.queueByUrl(request.queueUrl());
        Map<QueueAttributeName, String> attrs = new EnumMap<>(QueueAttributeName.class);
        attrs.put(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                  String.valueOf(queue.approximateNumberOfMessages()));
        attrs.put(QueueAttributeName.QUEUE_ARN,
                  "arn:aws:sqs:mokka-region:000000000000:" + queue.name);
        return GetQueueAttributesResponse.builder().attributes(attrs).build();
    }
}
