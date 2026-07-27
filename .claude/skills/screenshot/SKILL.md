---
description: Take a screenshot via the bridge and return the file path for AI context. Trigger when the user says "take a screenshot" or "show me the screen".
allowed-tools: Bash
---

Take a screenshot of the connected Android device via the bridge.

```bash
bash scripts/bridge-client.sh screenshot
```

Print the returned file path. Then tell Claude: the screenshot image is at `/root/bridge/screenshot.png` — use the Read tool to load it into context if the model supports vision.
