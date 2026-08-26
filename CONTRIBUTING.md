# Contributing to mokka

Thanks for wanting to help. Mokka is designed so adding an AWS service or an
operation is a small, self-contained PR that never touches the core.

## Setup

- Java 17 or newer
- Maven 3.9+
- `mvn -pl mokka-core -am verify` to build and run the core module tests
- `mvn verify` for the whole project (needs Maven Central for AWS SDK deps)

## Where to make changes

### Adding an operation to an existing service

1. Open `mokka-<service>/src/main/java/dev/mokka/<service>/XxxHandler.java`
2. Add a new `case "yourOp"` branch in the switch
3. Add faked behavior — usually a few lines against `XxxState`
4. Add a test in `mokka-<service>/src/test/java/...`
5. That's it. No core changes, no cross-cutting concerns.

### Adding a whole new AWS service

Copy `mokka-secretsmanager` (the smallest working module) and rename. You need:

- `XxxState.java` — in-memory state, thread-safe
- `XxxHandler.java` — `implements ServiceHandler`, dispatches by method name
- `XxxFake.java` — `implements FakeInstance`
- `XxxFakeProvider.java` — `implements MokkaFakeProvider`
- `src/main/resources/META-INF/services/dev.mokka.core.MokkaFakeProvider` — one line
- Tests

Add the module to the parent `pom.xml`'s `<modules>` list and to the
`<dependencyManagement>` block.

## Style

- Java 17+. Prefer records for value types.
- No new runtime dependencies without discussion in an issue first.
- Public types get Javadoc. Private types get comments only where they earn them.
- Tests use JUnit 5 and AssertJ.
- Handlers throw `MokkaUnimplementedException("Service", "operation")` for
  operations they don't yet fake — never silent no-ops.

## Commit messages

Use conventional-commits shape when practical:

```
feat(s3): support presigned URLs for getObject
fix(dynamodb): honor Limit in scan
docs(readme): clarify LocalStack positioning
test(sqs): visibility timeout resurfaces on next receive
```

## PR checklist

- [ ] Tests added or updated
- [ ] `mvn -pl <your-module> -am verify` passes
- [ ] Public API changes noted in `CHANGELOG.md` under Unreleased
- [ ] If you added a service, added it to `ROADMAP.md` and the README service table

## What we say no to

- New dependencies in `mokka-core`. Zero-dep is a feature.
- HTTP wire-protocol emulation. That's LocalStack.
- Real Lambda code execution. Same.
- "Config" that grows without bound. If a fake needs 10 knobs, one of them is
  probably wrong.

Say hi in an issue before large changes so we don't waste your time.
