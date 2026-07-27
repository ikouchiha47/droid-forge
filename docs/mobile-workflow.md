# Mobile Workflow — Android Development from a Phone

Develop Android apps entirely from your Android phone using Termux + proot-distro + opencode. GitHub Actions builds the APK; you install it on the same phone.

---

## One-Time Setup

### 1. Install Termux and Termux:API

Install both from **F-Droid** (not the Play Store — the Play Store builds are outdated and break).

- [F-Droid](https://f-droid.org/)
- Search for **Termux** and **Termux:API**, install both.

### 2. Install base packages in Termux

```sh
pkg update && pkg upgrade
pkg install termux-api git gh android-tools
```

### 3. Grant storage access

```sh
termux-setup-storage
```

Approve the prompt. This creates `~/storage/shared/` pointing to your internal storage.

### 4. Install proot-distro and Ubuntu

```sh
pkg install proot-distro
proot-distro install ubuntu
```

### 5. Set up Ubuntu inside proot

```sh
proot-distro login ubuntu
apt update && apt install -y git gh curl unzip
```

Install opencode inside proot (check [opencode releases](https://github.com/sst/opencode) for the latest binary):

```sh
curl -fsSL https://opencode.ai/install | sh
```

### 6. Clone the repo inside proot

```sh
git clone git@github.com:ikouchiha47/droid-forge.git ~/droid-forge
cd ~/droid-forge
```

### 7. Authenticate GitHub CLI

```sh
gh auth login
```

Follow the browser prompt or paste a personal access token. This works from inside proot.

### 8. ADB loopback

Your phone runs `adbd` by default. No extra setup is required — just connect after each restart:

```sh
adb connect 127.0.0.1:5555
```

Run this inside proot. Re-run it after every phone reboot.

---

## Bridge Setup

The bridge lets opencode (running inside proot) trigger native phone actions — screenshots, APK installs, clipboard — via Termux.

**How it works:** Termux home (`~/`) is mounted at `/root` inside proot-distro. Files written to `~/bridge/` in Termux appear at `/root/bridge/` inside proot. Two scripts use this shared directory for IPC.

### Start the bridge daemon (in Termux, before entering proot)

Open a Termux session and run:

```sh
# Recommended: keep it in a named tmux session
pkg install tmux
tmux new-session -s bridge
~/droid-forge/scripts/bridge-daemon.sh
```

Leave this running. The daemon watches `~/bridge/cmd` and handles commands from inside proot.

### Use the bridge client (from inside proot)

```sh
/root/droid-forge/scripts/bridge-client.sh screenshot
/root/droid-forge/scripts/bridge-client.sh install-apk /path/to/app.apk
/root/droid-forge/scripts/bridge-client.sh copy-to-sdcard /path/to/file
/root/droid-forge/scripts/bridge-client.sh open /path/to/file
/root/droid-forge/scripts/bridge-client.sh clipboard-get
/root/droid-forge/scripts/bridge-client.sh clipboard-set "some text"
```

---

## Daily Workflow

### 1. Start the bridge daemon (Termux)

If not already running in a tmux session:

```sh
tmux attach -t bridge
# or, if session doesn't exist:
tmux new-session -s bridge
~/droid-forge/scripts/bridge-daemon.sh
```

### 2. Enter proot

Open another Termux session (swipe right or open a new window):

```sh
proot-distro login ubuntu
cd ~/droid-forge
```

### 3. Connect ADB

```sh
adb connect 127.0.0.1:5555
```

### 4. Create a new app from the skeleton

```sh
scripts/rename-package.sh com.yourname.appname YourAppName
git checkout -b feature/your-app
```

### 5. Start opencode

```sh
opencode
```

Point it at `CLAUDE.md` and `SKELETON.md` for context. Use the available skills:

| Skill | What it does |
|---|---|
| `/build` | Trigger a CI build via GitHub Actions |
| `/download-apk` | Download latest APK from CI and install it |
| `/logcat [tag]` | Stream Android logs via ADB |
| `/screenshot` | Take a screenshot and return the path |
| `/new-issue <title>` | Create a GitHub planning issue |
| `/spec` | Run the spec-generator workflow |

### 6. Push and build

```sh
git add -p
git commit -m "your message"
git push origin feature/your-app
```

Then inside opencode:
```
/build
```

### 7. Install the APK

When the CI run completes:

```
/download-apk
```

This runs `scripts/download-apk.sh`, which downloads the artifact from the latest CI run and installs it via the bridge. The APK also lands in `~/storage/shared/Downloads/` for manual install if needed.

### 8. Debug with logcat

```
/logcat com.yourname.appname
```

Or run directly:

```sh
scripts/logcat.sh com.yourname.appname W
```

### 9. Screenshots for AI context

```
/screenshot
```

Returns a path like `/root/bridge/screenshot.png`. Pass this to opencode as context for UI feedback.

### 10. Plan with issues

```
/new-issue "Add settings screen"
```

---

## Scripts Reference

All scripts are in `scripts/`:

| Script | Usage |
|---|---|
| `bridge-daemon.sh` | Run in Termux; handles native commands |
| `bridge-client.sh <cmd> [args]` | Run in proot; sends commands to daemon |
| `screenshot.sh` | Thin wrapper around bridge screenshot |
| `download-apk.sh [branch]` | Download latest CI APK and install |
| `logcat.sh [package] [level]` | Stream logcat via ADB loopback |
| `issue.sh` | `gh` wrapper for creating planning issues |
| `rename-package.sh <pkg> <Name>` | Rename skeleton for a new app |

---

## Tips

- **Keep `bridge-daemon.sh` running in tmux.** If the bridge times out, check that the daemon is running in Termux — not inside proot.
- **`adb connect 127.0.0.1:5555` after every reboot.** The connection does not persist across restarts.
- **APKs land in `~/storage/shared/Downloads/`** — visible in any file manager on the phone.
- **Termux home = proot `/root`.** `~/bridge/` in Termux is `/root/bridge/` in proot. This is the shared IPC channel.
- **Two Termux sessions minimum:** one for the bridge daemon, one for proot work. Use tmux or Termux's built-in multi-session (swipe right).
- **`gh auth` persists inside proot** across sessions — you only need to log in once.
- **CI builds take a few minutes.** While waiting, use `/logcat` or `/screenshot` to test the previously installed build.
