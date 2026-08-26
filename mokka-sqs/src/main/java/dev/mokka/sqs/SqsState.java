package dev.mokka.sqs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory state for the SQS fake.
 *
 * <p>Each queue is a FIFO-ordered list of enqueued messages plus a map of
 * currently in-flight messages (those received but not yet deleted). Visibility
 * timeout is honored — v0.1 uses a fixed 30-second default and re-surfaces
 * expired in-flight messages on the next {@code receiveMessage} call.
 */
final class SqsState {

    static final String URL_PREFIX = "https://mokka.local/queues/";

    private final Map<String, FakeQueue> queues = new ConcurrentHashMap<>();

    /** Create a queue if it does not exist. Returns its URL. */
    String createQueue(String name) {
        queues.putIfAbsent(name, new FakeQueue(name));
        return URL_PREFIX + name;
    }

    FakeQueue queueByUrl(String url) {
        String name = queueNameFromUrl(url);
        FakeQueue q = queues.get(name);
        if (q == null) {
            throw new UnknownQueueMarker(url);
        }
        return q;
    }

    boolean queueExists(String name) {
        return queues.containsKey(name);
    }

    java.util.Collection<String> queueNames() {
        return java.util.List.copyOf(queues.keySet());
    }

    static String queueNameFromUrl(String url) {
        int slash = url.lastIndexOf('/');
        return slash < 0 ? url : url.substring(slash + 1);
    }

    void reset() {
        queues.clear();
    }

    static final class Message {
        final String id;
        String receiptHandle;
        final String body;
        final Map<String, Object> attributes;
        Instant visibleAt;

        Message(String body, Map<String, Object> attributes) {
            this.id = UUID.randomUUID().toString();
            this.body = body;
            this.attributes = attributes == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
            this.visibleAt = Instant.EPOCH;
        }
    }

    static final class FakeQueue {
        final String name;
        // All messages in insertion order. Reception marks them in-flight via visibleAt.
        final List<Message> messages = Collections.synchronizedList(new ArrayList<>());

        FakeQueue(String name) {
            this.name = name;
        }

        String send(String body, Map<String, Object> attributes) {
            Message m = new Message(body, attributes);
            messages.add(m);
            return m.id;
        }

        List<Message> receive(int max, int visibilityTimeoutSeconds) {
            List<Message> out = new ArrayList<>();
            Instant now = Instant.now();
            Instant newlyVisibleAt = now.plusSeconds(visibilityTimeoutSeconds);
            synchronized (messages) {
                for (Message m : messages) {
                    if (out.size() >= max) break;
                    if (!m.visibleAt.isAfter(now)) {
                        m.receiptHandle = UUID.randomUUID().toString();
                        m.visibleAt = newlyVisibleAt;
                        out.add(m);
                    }
                }
            }
            return out;
        }

        boolean delete(String receiptHandle) {
            synchronized (messages) {
                for (int i = 0; i < messages.size(); i++) {
                    Message m = messages.get(i);
                    if (receiptHandle.equals(m.receiptHandle)) {
                        messages.remove(i);
                        return true;
                    }
                }
            }
            return false;
        }

        boolean changeVisibility(String receiptHandle, int seconds) {
            synchronized (messages) {
                for (Message m : messages) {
                    if (receiptHandle.equals(m.receiptHandle)) {
                        m.visibleAt = Instant.now().plusSeconds(seconds);
                        return true;
                    }
                }
            }
            return false;
        }

        int approximateNumberOfMessages() {
            Instant now = Instant.now();
            int n = 0;
            synchronized (messages) {
                for (Message m : messages) {
                    if (!m.visibleAt.isAfter(now)) n++;
                }
            }
            return n;
        }
    }

    /** Sentinel — the handler turns this into QueueDoesNotExistException. */
    static final class UnknownQueueMarker extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String queueUrl;
        UnknownQueueMarker(String queueUrl) { this.queueUrl = queueUrl; }
    }
}
