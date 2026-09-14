# LINKPAS Agent Rules

This repository is the source of truth for continuation work.

## Authority order

1. `AGENTS.md`
2. `docs/APK_CHECKPOINTS.md`
3. `PROJECT_STATE.json`
4. Current Git / PR / CI / deployment reality
5. Chat context

If chat instructions conflict with the repository state, follow the repository unless the user explicitly changes the plan.

## Meaning of "lanjutkan"

When the user says **"lanjutkan"** (or an equivalent request to continue):

1. Read `AGENTS.md`, `docs/APK_CHECKPOINTS.md`, and `PROJECT_STATE.json` first.
2. Execute **exactly the checkpoint in `PROJECT_STATE.json.current_checkpoint`**.
3. Complete that checkpoint end-to-end, including its required validation and evidence.
4. Do **not** start the next checkpoint in the same turn, even if time remains.
5. At completion, update `PROJECT_STATE.json` so the next checkpoint becomes current.
6. Report `COMPLETE`, `BLOCKED`, or `FAILED`, with concise evidence and the exact next checkpoint.

"Lanjutkan" never means redesign the roadmap, add adjacent features, harden unrelated areas, or perform the next checkpoint early.

## Checkpoint timebox

- Target per checkpoint: **12-15 minutes**.
- Hard maximum: **18 minutes**. A checkpoint must be designed to finish in **less than 20 minutes**.
- Before implementation begins, if the current checkpoint cannot reasonably fit inside 18 minutes, split only that checkpoint into minimal sub-checkpoints (`A4a`, `A4b`, `D4a`, etc.) and update the roadmap/state first.
- Once execution starts, do not silently extend the checkpoint past 18 minutes.
- If an unexpected external blocker prevents completion within 18 minutes, stop at a clean boundary and mark it `BLOCKED` with evidence.
- Do not compensate by doing multiple checkpoints at once.

## Scope discipline

For every checkpoint:

- Work only on its stated scope and acceptance criteria.
- Do not introduce new product features unless required to satisfy the checkpoint.
- Do not refactor unrelated code.
- Prefer the smallest reversible change that satisfies the acceptance criteria.
- Preserve current LINKPAS web behavior unless the checkpoint explicitly changes it.
- Never expose signing keys, passwords, service-role keys, or other secrets in Git.

## Validation gate

A checkpoint is `COMPLETE` only when every acceptance criterion in `docs/APK_CHECKPOINTS.md` for that checkpoint is verified.

If a required build/test/deployment is queued or still running, stop at that gate and report it instead of pretending success.

If the same failure occurs 3 times without new evidence, stop changing code and mark the checkpoint `BLOCKED` with diagnostics.

## State updates

`PROJECT_STATE.json` must always reflect repository reality.

On successful completion:

- add the completed checkpoint to `completed_checkpoints`;
- set `current_checkpoint` to the next checkpoint;
- set `status` to `READY`;
- update `last_completed_checkpoint`;
- update `next_action`.

On a blocker:

- keep `current_checkpoint` unchanged;
- set `status` to `BLOCKED`;
- record a short `blocker`;
- do not advance.

## End-of-checkpoint report

Use this compact shape:

- `STATUS: COMPLETE | BLOCKED | FAILED`
- `CHECKPOINT:` exact current checkpoint ID (`A#`, `D#`, or split sub-checkpoint)
- `DONE:` what actually changed
- `EVIDENCE:` test/build/deployment evidence
- `NEXT:` exact next checkpoint from `PROJECT_STATE.json`

No claim of completion without evidence.