package dev.mokka.sns;

import dev.mokka.core.FakeInstance;
import dev.mokka.core.Mokka;
import software.amazon.awssdk.services.sns.SnsClient;

import java.util.Collections;
import java.util.List;

/**
 * Entry point for the in-process SNS fake.
 *
 * <p>Beyond the standard SDK operations, {@link #publishedMessages(String)} lets
 * tests assert directly on what was published to a topic — the single most useful
 * verification for fan-out code paths.
 */
public final class SnsFake implements FakeInstance {

    private final SnsState state;
    private final SnsHandler handler;
    private final SnsClient client;

    private SnsFake() {
        this.state = new SnsState();
        this.handler = new SnsHandler(state);
        this.client = Mokka.wrap(SnsClient.class, handler);
    }

    public static SnsFake create() {
        return new SnsFake();
    }

    public SnsClient client() {
        return client;
    }

    public SnsHandler handler() {
        return handler;
    }

    public void reset() {
        handler.reset();
    }

    /** Snapshot of every message published to the given topic ARN (empty if unknown). */
    public List<PublishedMessage> publishedMessages(String topicArn) {
        try {
            SnsState.FakeTopic topic = state.topic(topicArn);
            List<PublishedMessage> out = new java.util.ArrayList<>(topic.published.size());
            for (SnsState.PublishedMessage p : topic.published) {
                out.add(new PublishedMessage(p.messageId, p.message, p.subject));
            }
            return Collections.unmodifiableList(out);
        } catch (SnsState.UnknownTopicMarker e) {
            return List.of();
        }
    }

    /** A snapshot of one message that was published to a topic. */
    public record PublishedMessage(String messageId, String message, String subject) {}
}
