# Design documents

The design spec and implementation plan the app was built from, kept as a record
of the intended design rather than as current documentation. Where they disagree
with the code, the code is what shipped.

- [specs/2026-08-05-followlens-android-design.md](specs/2026-08-05-followlens-android-design.md)
  — what the app should do and why
- [plans/2026-08-05-followlens-android-v1.md](plans/2026-08-05-followlens-android-v1.md)
  — the task-by-task build order

## A note on paths

Both documents were written while the app lived in the `android/` subdirectory of
an earlier `follow-lens` monorepo, and their file references are relative to that
layout. This repository is the result of splitting that subdirectory out, so
`android/` is now the repository root: a path written as
`android/app/src/main/java/...` is `app/src/main/java/...` here.

Their 114 path references are otherwise left as written — rewriting them in a
historical record would risk changing what it says.
