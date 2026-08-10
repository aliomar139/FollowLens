# Design documents

The design spec and implementation plan the app was built from, kept verbatim as
a record of the intended design rather than as current documentation. Where they
disagree with the code, the code is what shipped.

- [specs/2026-08-05-followlens-android-design.md](specs/2026-08-05-followlens-android-design.md)
  — what the app should do and why
- [plans/2026-08-05-followlens-android-v1.md](plans/2026-08-05-followlens-android-v1.md)
  — the task-by-task build order

## A note on paths

Both documents were written while the app lived in the `android/` subdirectory of
the [follow-lens](https://github.com/kira/follow-lens) monorepo, and their
file references are relative to that layout. This repository is the result of
splitting that subdirectory out, so `android/` is now the repository root: a path
written as `android/app/src/main/java/...` is `app/src/main/java/...` here.

The documents are left unedited on purpose — rewriting 114 path references in a
historical record would risk changing what it says.
