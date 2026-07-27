---
description: Download and install the latest APK from CI
disable-model-invocation: true
allowed-tools: Bash
argument-hint: [branch]
---

Download and install the latest APK from CI. If $ARGUMENTS is empty, use the current branch.

```bash
BRANCH=${ARGUMENTS:-$(git rev-parse --abbrev-ref HEAD)}
bash scripts/download-apk.sh "$BRANCH"
```

Report where the APK landed and confirm the Android installer was triggered.
