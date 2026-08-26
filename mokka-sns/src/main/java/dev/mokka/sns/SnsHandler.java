package dev.mokka.sns;

import dev.mokka.core.MokkaUnimplementedException;
import dev.mokka.core.ServiceHandler;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.ListSubscriptionsByTopicRequest;
import software.amazon.awssdk.services.sns.model.ListSubscriptionsByTopicResponse;
import software.amazon.awssdk.services.sns.model.ListTopicsRequest;
import software.amazon.awssdk.services.sns.model.ListTopicsResponse;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.NotFoundException;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;
import software.amazon.awssdk.services.sns.model.Subscription;
import software.amazon.awssdk.services.sns.model.Topic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fake behavior for the SNS SDK client operations mokka v0.1 supports:
 * {@code createTopic}, {@code listTopics}, {@code subscribe},
 * {@code listSubscriptionsByTopic}, {@code publish}.
 */
public final class SnsHandler implements ServiceHandler {

    private final SnsState state;

    public SnsHandler(SnsState state) {
        this.state = state;
    }

    @Override public String serviceName() { return "SNS"; }
    @Override public void reset() { state.reset(); }

    @Override
    public Object handle(Method method, Object[] args) {
        try {
            return switch (method.getName()) {
                case "createTopic" -> createTopic(args);
                case "listTopics" -> listTopics(args);
                case "subscribe" -> subscribe(args);
                case "listSubscriptionsByTopic" -> listSubscriptionsByTopic(args);
                case "publish" -> publish(args);
                default -> throw new MokkaUnimplementedException("SNS", method.getName());
            };
        } catch (SnsState.UnknownTopicMarker missing) {
            throw NotFoundException.builder()
                .message("Topic does not exist: " + missing.arn)
                .build();
        }
    }

    private CreateTopicResponse createTopic(Object[] args) {
        CreateTopicRequest request = (CreateTopicRequest) args[0];
        return CreateTopicResponse.builder()
            .topicArn(state.createTopic(request.name()))
            .build();
    }

    private ListTopicsResponse listTopics(Object[] args) {
        List<Topic> topics = new ArrayList<>();
        for (String arn : state.topicArns()) {
            topics.add(Topic.builder().topicArn(arn).build());
        }
        return ListTopicsResponse.builder().topics(topics).build();
    }

    private SubscribeResponse subscribe(Object[] args) {
        SubscribeRequest request = (SubscribeRequest) args[0];
        SnsState.FakeTopic topic = state.topic(request.topicArn());
        String subArn = topic.subscribe(request.protocol(), request.endpoint());
        return SubscribeResponse.builder().subscriptionArn(subArn).build();
    }

    private ListSubscriptionsByTopicResponse listSubscriptionsByTopic(Object[] args) {
        ListSubscriptionsByTopicRequest request = (ListSubscriptionsByTopicRequest) args[0];
        SnsState.FakeTopic topic = state.topic(request.topicArn());
        List<Subscription> out = new ArrayList<>();
        for (SnsState.Subscription s : topic.subscriptions) {
            out.add(Subscription.builder()
                .subscriptionArn(s.subscriptionArn)
                .topicArn(topic.arn)
                .protocol(s.protocol)
                .endpoint(s.endpoint)
                .build());
        }
        return ListSubscriptionsByTopicResponse.builder().subscriptions(out).build();
    }

    private PublishResponse publish(Object[] args) {
        PublishRequest request = (PublishRequest) args[0];
        String arn = request.topicArn() != null ? request.topicArn() : request.targetArn();
        if (arn == null) {
            throw new IllegalArgumentException(
                "publish requires topicArn or targetArn. PhoneNumber publishes are not yet implemented in mokka v0.1.");
        }
        SnsState.FakeTopic topic = state.topic(arn);
        Map<String, Object> attributes = null;
        if (request.hasMessageAttributes()) {
            attributes = new LinkedHashMap<>();
            for (Map.Entry<String, MessageAttributeValue> e : request.messageAttributes().entrySet()) {
                attributes.put(e.getKey(), e.getValue());
            }
        }
        String messageId = topic.publish(request.message(), request.subject(), attributes);
        return PublishResponse.builder().messageId(messageId).build();
    }
}
