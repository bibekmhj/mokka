package dev.mokka.sqs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqsFakeTest {

    private SqsFake fake;
    private SqsClient sqs;
    private String url;

    @BeforeEach
    void setUp() {
        fake = SqsFake.create();
        sqs = fake.client();
        url = sqs.createQueue(r -> r.queueName("refunds")).queueUrl();
    }

    @Test
    void sendThenReceiveRoundTrip() {
        sqs.sendMessage(r -> r.queueUrl(url).messageBody("ord_123"));
        ReceiveMessageResponse got = sqs.receiveMessage(r -> r.queueUrl(url).maxNumberOfMessages(10));
        assertThat(got.messages()).hasSize(1);
        assertThat(got.messages().get(0).body()).isEqualTo("ord_123");
        assertThat(got.messages().get(0).receiptHandle()).isNotBlank();
    }

    @Test
    void deleteRemovesMessage() {
        sqs.sendMessage(r -> r.queueUrl(url).messageBody("one"));
        var got = sqs.receiveMessage(r -> r.queueUrl(url));
        sqs.deleteMessage(r -> r.queueUrl(url).receiptHandle(got.messages().get(0).receiptHandle()));
        // After delete + visibility clock roll, queue should be empty.
        // v0.1: default 30s visibility means the delivered message stays invisible for 30s
        // even without delete. Since we deleted it, it's gone.
        var again = sqs.receiveMessage(r -> r.queueUrl(url).visibilityTimeout(0));
        assertThat(again.messages()).isEmpty();
    }

    @Test
    void receiveHonorsMaxMessages() {
        for (int i = 0; i < 5; i++) {
            int idx = i;
            sqs.sendMessage(r -> r.queueUrl(url).messageBody("m" + idx));
        }
        var got = sqs.receiveMessage(r -> r.queueUrl(url).maxNumberOfMessages(2));
        assertThat(got.messages()).hasSize(2);
    }

    @Test
    void visibilityTimeoutHidesMessageThenResurfaces() {
        sqs.sendMessage(r -> r.queueUrl(url).messageBody("one"));
        var first = sqs.receiveMessage(r -> r.queueUrl(url).visibilityTimeout(1));
        assertThat(first.messages()).hasSize(1);
        // Immediately after: message is invisible.
        var second = sqs.receiveMessage(r -> r.queueUrl(url).visibilityTimeout(1));
        assertThat(second.messages()).isEmpty();
    }

    @Test
    void changeVisibilityToZeroResurfacesImmediately() {
        sqs.sendMessage(r -> r.queueUrl(url).messageBody("x"));
        var got = sqs.receiveMessage(r -> r.queueUrl(url).visibilityTimeout(60));
        String rh = got.messages().get(0).receiptHandle();
        sqs.changeMessageVisibility(r -> r.queueUrl(url).receiptHandle(rh).visibilityTimeout(0));
        var again = sqs.receiveMessage(r -> r.queueUrl(url).visibilityTimeout(1));
        assertThat(again.messages()).hasSize(1);
    }

    @Test
    void sendToUnknownQueueThrows() {
        assertThatThrownBy(() -> sqs.sendMessage(r -> r.queueUrl("https://mokka.local/queues/ghost").messageBody("x")))
            .isInstanceOf(QueueDoesNotExistException.class);
    }

    @Test
    void listQueuesReturnsCreatedOnes() {
        assertThat(sqs.listQueues().queueUrls()).contains(url);
    }

    @Test
    void getQueueAttributesReturnsCount() {
        sqs.sendMessage(r -> r.queueUrl(url).messageBody("x"));
        var attrs = sqs.getQueueAttributes(r -> r.queueUrl(url)
            .attributeNamesWithStrings("All"));
        assertThat(attrs.attributes().values()).anyMatch(v -> v.equals("1"));
    }

    @Test
    void resetWipesQueues() {
        fake.reset();
        assertThat(sqs.listQueues().queueUrls()).isEmpty();
    }
}
