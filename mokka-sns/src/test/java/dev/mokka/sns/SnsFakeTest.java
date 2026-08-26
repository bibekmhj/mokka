package dev.mokka.sns;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnsFakeTest {

    private SnsFake fake;
    private SnsClient sns;
    private String topicArn;

    @BeforeEach
    void setUp() {
        fake = SnsFake.create();
        sns = fake.client();
        topicArn = sns.createTopic(t -> t.name("payments-events")).topicArn();
    }

    @Test
    void publishRecordsMessage() {
        var res = sns.publish(p -> p.topicArn(topicArn).message("order:1").subject("new-order"));
        assertThat(res.messageId()).isNotBlank();
        assertThat(fake.publishedMessages(topicArn)).hasSize(1);
        assertThat(fake.publishedMessages(topicArn).get(0).message()).isEqualTo("order:1");
        assertThat(fake.publishedMessages(topicArn).get(0).subject()).isEqualTo("new-order");
    }

    @Test
    void publishToUnknownTopicThrows() {
        assertThatThrownBy(() -> sns.publish(p -> p.topicArn(SnsState.ARN_PREFIX + "ghost").message("x")))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void subscribeThenListReturnsSubscription() {
        sns.subscribe(s -> s.topicArn(topicArn).protocol("sqs").endpoint("arn:aws:sqs:...:q"));
        var subs = sns.listSubscriptionsByTopic(l -> l.topicArn(topicArn));
        assertThat(subs.subscriptions()).hasSize(1);
        assertThat(subs.subscriptions().get(0).protocol()).isEqualTo("sqs");
    }

    @Test
    void listTopicsReturnsCreated() {
        assertThat(sns.listTopics().topics()).extracting("topicArn").contains(topicArn);
    }

    @Test
    void resetWipes() {
        fake.reset();
        assertThat(sns.listTopics().topics()).isEmpty();
    }
}
