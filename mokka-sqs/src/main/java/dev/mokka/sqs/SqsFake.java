package dev.mokka.sqs;

import dev.mokka.core.FakeInstance;
import dev.mokka.core.Mokka;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Entry point for the in-process SQS fake.
 *
 * <pre>{@code
 * SqsFake fake = SqsFake.create();
 * SqsClient sqs = fake.client();
 * String url = sqs.createQueue(r -> r.queueName("refunds")).queueUrl();
 *
 * sqs.sendMessage(r -> r.queueUrl(url).messageBody("ord_123"));
 * ReceiveMessageResponse got = sqs.receiveMessage(r -> r.queueUrl(url));
 * }</pre>
 *
 * <p>Queues live at synthetic URLs of the form
 * {@code https://mokka.local/queues/{name}}. Visibility timeout defaults to 30
 * seconds and re-surfaces messages on the next {@code receiveMessage} call.
 */
public final class SqsFake implements FakeInstance {

    private final SqsState state;
    private final SqsHandler handler;
    private final SqsClient client;

    private SqsFake() {
        this.state = new SqsState();
        this.handler = new SqsHandler(state);
        this.client = Mokka.wrap(SqsClient.class, handler);
    }

    public static SqsFake create() {
        return new SqsFake();
    }

    public SqsClient client() {
        return client;
    }

    public SqsHandler handler() {
        return handler;
    }

    public void reset() {
        handler.reset();
    }
}
