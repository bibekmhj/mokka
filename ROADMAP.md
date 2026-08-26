# Roadmap

Mokka's north star: **every AWS SDK v2 client interface compiles against mokka
today, with real fake behavior filling in as fast as the community moves**.

## v0.1 (shipped)

- `mokka-core` proxy engine + SPI
- Deep behavior: S3, DynamoDB, SQS, SNS, Secrets Manager
- 300+ other services compile against `Mokka.fake(...)` and throw a link-carrying
  `MokkaUnimplementedException` only when a specific method is called

## v0.2 (next)

Services listed by community usage. Each is one Maven module — a `State`, a
`Handler`, a `Fake`, a `Provider`, a `META-INF/services` line. Pick one and open
a PR:

- **Lambda** — Invoke (RequestResponse + Event), function metadata roundtrip
- **Kinesis** — PutRecord(s), GetShardIterator, GetRecords
- **KMS** — Encrypt, Decrypt, GenerateDataKey, Sign, Verify
- **SSM Parameter Store** — GetParameter(s), PutParameter, GetParametersByPath
- **EventBridge** — PutEvents, PutRule, ListRules

## v0.3

- Deeper behavior for v0.1 services:
  - S3 multipart upload; presigned URLs; bucket policies
  - DynamoDB `updateItem` with expression parser; GSIs; sort-key ranges
  - SQS FIFO ordering + deduplication
  - Secrets Manager version staging labels + rotation hook
- Spring Boot starter (`mokka-spring-boot-starter`) that auto-wires `@Bean`
  fake clients when it sees a `@SpringBootTest` classpath

## v0.4+

- **Fault injection framework** in `mokka-core` — any handler can be configured
  to throw a service-specific exception on Nth call, with per-operation matchers
- **Conformance test suite** — pluggable JUnit runner that runs the same test
  against mokka AND real AWS (opt-in, credentials required), reports drift
- **Kotlin extensions** — `mokka-kotlin` with idiomatic DSL and coroutine
  support for async clients

## v1.0 (year 1 target)

- ~15 services with deep behavior
- Spring Boot starter, Kotlin extensions, Testcontainers-drop-in module
- Documented "mokka vs LocalStack" decision table
- 2–5k stars, working contributor community

## Never

- **HTTP wire-protocol emulation.** That's LocalStack's job. Mokka is the fast
  in-process layer *above* it.
- **Real Lambda code execution.** Same reasoning.
- **Hosted / SaaS mode.** Mokka is a library, not a service.
- **Auth / IAM enforcement.** Mokka assumes the code under test has valid
  credentials — you're testing your business logic, not AWS's policy engine.

## How to add a service

Copy one of the existing service modules (`mokka-secretsmanager` is the smallest)
and rename. The pattern is intentionally boilerplate:

1. `XxxState.java` — thread-safe in-memory state
2. `XxxHandler.java` — `implements ServiceHandler`, dispatches `method.getName()`
3. `XxxFake.java` — `implements FakeInstance`, exposes `client()`
4. `XxxFakeProvider.java` — `implements MokkaFakeProvider`
5. `src/main/resources/META-INF/services/dev.mokka.core.MokkaFakeProvider` — one line
6. `XxxFakeTest.java` — mirror the existing service-fake tests

Everything else — the JUnit 5 extension, the (future) Spring starter, the
`Mokka.fake(...)` factory — picks it up via ServiceLoader with zero further wiring.

See [CONTRIBUTING.md](CONTRIBUTING.md).
