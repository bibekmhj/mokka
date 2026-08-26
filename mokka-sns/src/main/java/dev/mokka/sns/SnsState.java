package dev.mokka.sns;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory state for the SNS fake.
 *
 * <p>Topics are identified by ARN of the form
 * {@code arn:aws:sns:mokka-region:000000000000:{name}}. Each topic keeps a
 * subscription list and a chronological log of published messages so tests
 * can assert on what fanned out.
 */
final class SnsState {

    static final String ARN_PREFIX = "arn:aws:sns:mokka-region:000000000000:";

    private final Map<String, FakeTopic> topics = new ConcurrentHashMap<>();

    String createTopic(String name) {
        String arn = ARN_PREFIX + name;
        topics.putIfAbsent(arn, new FakeTopic(arn, name));
        return arn;
    }

    FakeTopic topic(String arn) {
        FakeTopic t = topics.get(arn);
        if (t == null) {
            throw new UnknownTopicMarker(arn);
        }
        return t;
    }

    Collection<String> topicArns() {
        return List.copyOf(topics.keySet());
    }

    void reset() {
        topics.clear();
    }

    static final class PublishedMessage {
        final String messageId;
        final String message;
        final String subject;
        final Map<String, Object> attributes;

        PublishedMessage(String message, String subject, Map<String, Object> attributes) {
            this.messageId = UUID.randomUUID().toString();
            this.message = message;
            this.subject = subject;
            this.attributes = attributes;
        }
    }

    static final class Subscription {
        final String subscriptionArn;
        final String protocol;
        final String endpoint;

        Subscription(String topicArn, String protocol, String endpoint) {
            this.subscriptionArn = topicArn + ":" + UUID.randomUUID();
            this.protocol = protocol;
            this.endpoint = endpoint;
        }
    }

    static final class FakeTopic {
        final String arn;
        final String name;
        final List<Subscription> subscriptions = new ArrayList<>();
        final List<PublishedMessage> published = new ArrayList<>();

        FakeTopic(String arn, String name) {
            this.arn = arn;
            this.name = name;
        }

        synchronized String subscribe(String protocol, String endpoint) {
            Subscription s = new Subscription(arn, protocol, endpoint);
            subscriptions.add(s);
            return s.subscriptionArn;
        }

        synchronized String publish(String message, String subject, Map<String, Object> attributes) {
            PublishedMessage p = new PublishedMessage(message, subject, attributes);
            published.add(p);
            return p.messageId;
        }
    }

    /** Sentinel — handler turns this into NotFoundException. */
    static final class UnknownTopicMarker extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String arn;
        UnknownTopicMarker(String arn) { this.arn = arn; }
    }
}
