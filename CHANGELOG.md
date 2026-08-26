# Changelog

All notable changes to mokka will be documented here. This project follows
[semantic versioning](https://semver.org/).

## Unreleased

## 0.1.0 — 2026-08-26

Initial release.

### Added

- `mokka-core`: reflective proxy engine, `MokkaFakeProvider` SPI, `FakeInstance`
  contract, `MokkaUnimplementedException` with a link back to the roadmap.
- `mokka-s3`: in-memory S3 fake — createBucket, headBucket, putObject, getObject,
  getObjectAsBytes, headObject, deleteObject, listObjectsV2.
- `mokka-dynamodb`: in-memory DynamoDB fake — createTable, describeTable,
  listTables, putItem, getItem, deleteItem, query (partition-key equality),
  scan. Composite key support.
- `mokka-sqs`: in-memory SQS fake with visibility timeout — createQueue,
  getQueueUrl, listQueues, sendMessage, receiveMessage, deleteMessage,
  changeMessageVisibility, getQueueAttributes.
- `mokka-sns`: in-memory SNS fake — createTopic, listTopics, subscribe,
  listSubscriptionsByTopic, publish. `SnsFake.publishedMessages(arn)` for direct
  fan-out assertions.
- `mokka-secretsmanager`: in-memory Secrets Manager fake — createSecret,
  getSecretValue, putSecretValue, updateSecret, describeSecret, listSecrets.
- `mokka-junit5`: `@ExtendWith(MokkaExtension.class)` + `@MokkaClient` field
  injection. Fakes cached per test instance, state reset between every `@Test`.
- Every AWS SDK v2 client interface compiles against `mokka-core` today. Ask
  for one with `Mokka.fake(SomeClient.class, "ServiceName")`; individual
  operations throw `MokkaUnimplementedException` only when actually invoked.
