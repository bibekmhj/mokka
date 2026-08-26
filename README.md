# mokka

**The in-process AWS mock library for the JVM.**
Every AWS service. In-memory. Milliseconds per test. No Docker.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.bibekmhj/mokka-core.svg?label=Maven%20Central)](https://central.sonatype.com/namespace/io.github.bibekmhj)
[![CI](https://github.com/bibekmhj/mokka/actions/workflows/ci.yml/badge.svg)](https://github.com/bibekmhj/mokka/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache_2.0-blue.svg)](LICENSE)

---

## Why mokka exists

Python has [Moto](https://github.com/getmoto/moto) — 8k+ stars, in-process AWS fakes, tests run in milliseconds. Java doesn't. When a Java developer [asked AWS on re:Post](https://repost.aws/questions/QUXKTChdh-SwSHklCkcsNR5Q/moto-equivalent-library-for-java-kotlin) for the same, AWS's answer was, in effect, *"there isn't one."*

Today JVM teams have two options:

- **LocalStack + Testcontainers** — real behavior, but 20–60s of Docker startup per test suite, and [as of 2026](https://medium.com/@raphael.moutard/i-want-to-pay-for-localstack-but-i-wont-051f8e10d71e) their key features moved behind a paywall.
- **Hand-rolled Mockito** — fragile, incomplete, drifts every SDK upgrade.

Mokka is the third option. **Drop-in fakes for the AWS SDK for Java v2, running inside your JVM, faster than the SDK can build a request.**

## Install

```xml
<dependency>
    <groupId>io.github.bibekmhj</groupId>
    <artifactId>mokka-s3</artifactId>
    <version>0.1.1</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.github.bibekmhj</groupId>
    <artifactId>mokka-junit5</artifactId>
    <version>0.1.1</version>
    <scope>test</scope>
</dependency>
```

Add one dependency per service you want to fake: `mokka-s3`, `mokka-dynamodb`, `mokka-sqs`, `mokka-sns`, `mokka-secretsmanager`. Bring your own AWS SDK versions.

## 30-second example

```java
@ExtendWith(MokkaExtension.class)
class RefundServiceTest {

    @MokkaClient S3Client s3;
    @MokkaClient DynamoDbClient ddb;
    @MokkaClient SqsClient sqs;

    @Test
    void issuesRefund_writesAuditAndPublishesEvent() {
        // ---- given
        ddb.createTable(r -> r.tableName("refunds")
            .keySchema(k -> k.attributeName("id").keyType(KeyType.HASH))
            .attributeDefinitions(a -> a.attributeName("id").attributeType("S"))
            .billingMode(BillingMode.PAY_PER_REQUEST));
        String queueUrl = sqs.createQueue(r -> r.queueName("refund-events")).queueUrl();

        // ---- when
        refundService.refund("ord_123");

        // ---- then
        var events = sqs.receiveMessage(r -> r.queueUrl(queueUrl).maxNumberOfMessages(10));
        assertThat(events.messages()).hasSize(1);

        var stored = ddb.getItem(r -> r.tableName("refunds")
            .key(Map.of("id", AttributeValue.fromS("ord_123"))));
        assertThat(stored.item()).containsKey("status");

        var audit = s3.getObjectAsBytes(r -> r.bucket("audit").key("ord_123.json"));
        assertThat(audit.asUtf8String()).contains("REFUNDED");
    }
}
```

No `@BeforeEach` boilerplate. No Docker. No LocalStack container to warm up. State resets between every `@Test`. Each fake is a real AWS SDK client type — you can inject it anywhere your production code takes an `S3Client`.

## What mokka fakes today (v0.1)

| Service | Deep behavior | Notes |
|---|---|---|
| **S3** | createBucket, headBucket, putObject, getObject, getObjectAsBytes, headObject, deleteObject, listObjectsV2 | RequestBody supported. ETag deterministic. Metadata roundtrips. |
| **DynamoDB** | createTable, describeTable, listTables, putItem, getItem, deleteItem, query, scan | Composite (partition + sort) keys. Query supports `keyConditionExpression`. |
| **SQS** | createQueue, getQueueUrl, listQueues, sendMessage, receiveMessage, deleteMessage, changeMessageVisibility, getQueueAttributes | Visibility timeout honored. Message attributes roundtrip. |
| **SNS** | createTopic, listTopics, subscribe, listSubscriptionsByTopic, publish | `fake.publishedMessages(arn)` for direct fan-out assertions. |
| **Secrets Manager** | createSecret, getSecretValue, putSecretValue, updateSecret, describeSecret, listSecrets | Current version only in v0.1. |

**Every other AWS service also compiles against mokka today.** Ask for a `KinesisClient` with `Mokka.fake(KinesisClient.class, "Kinesis")` and it will dependency-inject cleanly — methods throw `MokkaUnimplementedException` only when actually invoked, with a link to open a PR. See [ROADMAP.md](ROADMAP.md) for what's next.

## Why this shape

Reflection-backed proxies means adding a new AWS service is one Maven module — a `State`, a `Handler`, a `Fake`, a `Provider`, a `META-INF/services` file. No touching the core, no touching the JUnit extension, no touching any other service. Contributors can own a service end-to-end.

## Positioning against alternatives

| | mokka | LocalStack + Testcontainers | Hand-rolled Mockito |
|---|---|---|---|
| Startup per test suite | ~50 ms | ~30–60 s | 0 |
| Per-test overhead | ~1 ms | ~50 ms | ~5 ms of setup |
| Requires Docker | No | Yes | No |
| Real AWS SDK client types | Yes | Yes | No (mocks are Mockito stubs) |
| Faithful to service behavior | v0.1: core ops | Very high | You write it |
| Survives SDK upgrades | Yes (interface-typed) | Usually | Fragile |
| Free | Yes, Apache 2.0 | Free tier limited (2026 paywall) | Yes |
| Coverage of AWS services | 5 deep + 300+ shallow | Broad, uneven | Whatever you wrote |

Mokka is best for **fast unit / component tests**. Use LocalStack + Testcontainers for **integration tests** that need real wire-protocol behavior. The two are complementary — you get speed for the inner loop and fidelity for the outer loop.

## Modules

- `mokka-core` — proxy engine + SPI. Zero AWS SDK dependency.
- `mokka-s3` — S3 fake.
- `mokka-dynamodb` — DynamoDB fake.
- `mokka-sqs` — SQS fake.
- `mokka-sns` — SNS fake.
- `mokka-secretsmanager` — Secrets Manager fake.
- `mokka-junit5` — `@ExtendWith(MokkaExtension.class)` + `@MokkaClient` field injection.

## Roadmap

See [ROADMAP.md](ROADMAP.md). Next services on the shortlist: Lambda, Kinesis, EventBridge, KMS, SSM Parameter Store.

## Contributing

Adding a new service or filling in more operations is deliberately easy — see [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Apache 2.0 — see [LICENSE](LICENSE).
