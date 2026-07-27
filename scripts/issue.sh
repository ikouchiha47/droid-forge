#!/usr/bin/env bash
set -euo pipefail

if ! command -v gh &>/dev/null; then
  echo "error: 'gh' not found. Install: https://cli.github.com" >&2
  exit 1
fi

if ! git rev-parse --git-dir &>/dev/null 2>&1; then
  echo "error: not in a git repo" >&2
  exit 1
fi

if ! git remote get-url origin &>/dev/null 2>&1; then
  echo "error: no git remote 'origin' found" >&2
  exit 1
fi

REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || true)"
if [[ -z "$REPO" ]]; then
  echo "error: could not determine GitHub repo. Run 'gh repo set-default'" >&2
  exit 1
fi

ensure_label() {
  local label="$1"
  local color="${2:-"#ededed"}"
  gh label create "$label" --color "$color" --force --repo "$REPO" &>/dev/null
}

cmd="${1:-}"
shift || true

case "$cmd" in
  plan)
    title="${1:-}"
    if [[ -z "$title" ]]; then
      echo "usage: issue.sh plan \"<title>\" [app/<name>]" >&2
      exit 1
    fi
    app_label="${2:-}"

    ensure_label "planning" "#0075ca"
    labels="planning"

    if [[ -n "$app_label" ]]; then
      ensure_label "$app_label" "#e4e669"
      labels="$labels,$app_label"
    fi

    url="$(gh issue create \
      --repo "$REPO" \
      --title "$title" \
      --label "$labels" \
      --body "" \
      2>&1)"
    echo "$url"
    ;;

  ls)
    filter_label="${1:-}"
    if [[ -n "$filter_label" ]]; then
      label_flag="--label $filter_label"
    else
      label_flag="--label planning"
    fi

    # shellcheck disable=SC2086
    gh issue list \
      --repo "$REPO" \
      --state open \
      $label_flag \
      --json number,title,labels \
      --jq '.[] | "#\(.number) \(.title)\n  labels: \(.labels | map(.name) | join(", "))"'
    ;;

  close)
    number="${1:-}"
    comment="${2:-}"
    if [[ -z "$number" ]]; then
      echo "usage: issue.sh close <number> [\"comment\"]" >&2
      exit 1
    fi

    if [[ -n "$comment" ]]; then
      gh issue comment "$number" --repo "$REPO" --body "$comment"
    fi
    gh issue close "$number" --repo "$REPO"
    echo "closed #$number"
    ;;

  milestone)
    ms_title="${1:-}"
    if [[ -z "$ms_title" ]]; then
      echo "usage: issue.sh milestone \"<title>\" [create]" >&2
      exit 1
    fi
    action="${2:-}"

    if [[ "$action" == "create" ]]; then
      existing="$(gh api "repos/$REPO/milestones" \
        --jq ".[] | select(.title == \"$ms_title\") | .number" 2>/dev/null || true)"
      if [[ -n "$existing" ]]; then
        echo "milestone '$ms_title' already exists (#$existing)"
      else
        gh api "repos/$REPO/milestones" \
          --method POST \
          --field title="$ms_title" \
          --jq '"created milestone: \(.title) (#\(.number))"'
      fi
    else
      ms_number="$(gh api "repos/$REPO/milestones" \
        --jq ".[] | select(.title == \"$ms_title\") | .number" 2>/dev/null || true)"
      if [[ -z "$ms_number" ]]; then
        echo "error: milestone '$ms_title' not found. Create it with: issue.sh milestone \"$ms_title\" create" >&2
        exit 1
      fi
      gh issue list \
        --repo "$REPO" \
        --state all \
        --json number,title,state,labels \
        --jq ".[] | select(.milestone.title == \"$ms_title\" or true) | \"#\(.number) [\(.state)] \(.title)\"" \
        2>/dev/null || \
      gh api "repos/$REPO/issues?milestone=$ms_number&state=all&per_page=100" \
        --jq '.[] | "#\(.number) [\(.state)] \(.title)'\''\n  labels: \(.labels | map(.name) | join(", "))"'
    fi
    ;;

  note)
    number="${1:-}"
    comment="${2:-}"
    if [[ -z "$number" || -z "$comment" ]]; then
      echo "usage: issue.sh note <number> \"<comment>\"" >&2
      exit 1
    fi
    gh issue comment "$number" --repo "$REPO" --body "$comment"
    echo "noted on #$number"
    ;;

  *)
    cat <<'EOF'
usage: issue.sh <command> [args]

commands:
  plan "title" [app/<name>]      create planning issue
  ls [app/<name>]                list open issues
  close <num> ["comment"]        close issue with optional comment
  milestone "v1.0.0"             list issues in milestone
  milestone "v1.0.0" create      create milestone
  note <num> "comment"           add comment to issue
EOF
    exit 1
    ;;
esac
