---
description: Run the spec-generator workflow to design a new Android app. Trigger when the user wants to spec out, design, or plan a new app.
argument-hint: <app description>
---

You are running the droid-forge spec-generator workflow. Follow these steps exactly:

1. Read `prompts/spec-generator.md` to load the full spec-generator instructions.

2. If $ARGUMENTS is empty, ask the user: "What app do you want to build? Give a one-sentence description."

3. Follow the spec-generator instructions exactly to produce, in order:
   - EARS requirements (structured natural-language requirements)
   - RALPH tree (requirements breakdown hierarchy)
   - DAG (dependency graph of components/features)
   - Eval gates (acceptance criteria per node)
   - Per-node prompts (the generation prompts for each DAG node)
   - Validation script (automated checks for the spec)

4. When all artifacts are complete, end with exactly:

   `SPEC COMPLETE. Commit to docs/specs/<appname>-spec.md`

**IMPORTANT: Do NOT write any Kotlin code until the human confirms the spec has been committed.**
