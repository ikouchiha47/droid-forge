---
description: Stream filtered logcat from the running Android app via bridge
disable-model-invocation: true
allowed-tools: Bash
argument-hint: [package-or-tag] [level]
---

Stream logcat output from the connected Android device via loopback ADB.

If $ARGUMENTS is provided, pass them directly:

```bash
bash scripts/logcat.sh $ARGUMENTS
```

If no arguments were given, ask the user for the package name or tag before running.

Note: logcat streams continuously — the user must press Ctrl+C to stop it.
