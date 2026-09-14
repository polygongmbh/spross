# Multi-agent orchestration

Read this when launching or conducting a multi-agent wave.
Single-agent sessions do not need it.

## Agent sizing

- Offload open-ended research and large implementations to subagents;
  hand each the full spec + the relevant `docs/` pointer.
- Fewer, larger agents: batch 2–3 work packages per agent, share context via a short digest.

## Concurrency

- One writer per source tree per wave. `kern/build/kotlin` is shared mutable state;
  overlapping Gradle runs corrupt it (`Unresolved reference` in files nobody touched).
  Fix: `rm -rf kern/build/kotlin`, rebuild — never a source edit.
  `xcodebuild`'s pre-build phase also reads/writes the kern framework, so it is a cache writer too.
- `scripts/catalog-format.py --fix` rewrites the WHOLE repo — only the conductor runs it,
  after all agents are done. Agents that need valid JSON write through the formatter's own
  `formatted()` function on their files only.

## Gates

- Quiet output: `gradle --console=plain -q`, `xcodebuild -quiet`, pipe long logs to a file.
- Full gates run once in the conductor, not per builder.
  In-flight checks target only the tests covering the change (`--tests 'Pattern'`).
- `catalog/audio/**` is excluded from `:kern:jvmTest`'s input set (`kern/build.gradle.kts`):
  an audio edit reports UP-TO-DATE and needs `--rerun-tasks`.
