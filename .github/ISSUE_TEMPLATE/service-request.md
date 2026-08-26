---
name: New service or operation request
about: Ask for a specific AWS service or operation to be faked
title: "[Service] "
labels: service-request
---

**Service**

E.g. Kinesis / KMS / Lambda / SSM Parameter Store

**Operation(s) you actually use in your tests**

E.g. `Kinesis.PutRecord`, `Kinesis.GetRecords`

**How you use it**

```java
// A tiny snippet from your production code showing the call site,
// so we know what shape of behavior matters.
```

**Would you be up for opening a PR?**

The [ROADMAP](../ROADMAP.md) explains the module shape — it's small.
