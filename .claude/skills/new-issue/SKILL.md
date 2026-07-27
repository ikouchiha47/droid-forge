---
description: Create a GitHub planning issue. Trigger when the user says "log this as an issue", "create an issue", or "track this".
allowed-tools: Bash
argument-hint: <title> [app/branch]
---

Create a GitHub planning issue using the issue script.

If $ARGUMENTS is provided:

```bash
bash scripts/issue.sh plan "$ARGUMENTS"
```

If no arguments were given, ask the user for the issue title (and optionally the app or branch) before running.

Report the created issue URL from the output.
