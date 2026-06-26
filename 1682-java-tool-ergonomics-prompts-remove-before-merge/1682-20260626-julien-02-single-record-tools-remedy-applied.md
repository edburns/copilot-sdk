# Single-record tools remedy plan (PR #1792 follow-up)

## Goals

- Ensure single-record `@CopilotTool` methods generate schema and handler logic with matching payload shape.
- Prevent additional single-record edge cases from slipping through.
- Add focused regression tests that fail on the old behavior and pass on the corrected behavior.

## Plan with progress checkboxes

- [x] **Align single-record schema and handler**
  - [x] Add a single-record fast path in `CopilotToolProcessor.generateSchemaWithParamMetadata(...)`.
  - [x] For that path, emit the record schema directly at top level (record components become top-level properties).
  - [x] Keep runtime extraction aligned by converting directly from `invocation.getArguments()` for single-record methods.

- [x] **Eliminate local variable naming collision**
  - [x] Ensure generated single-record code does not declare local `Map<String, Object> args`.
  - [x] Verify a record parameter literally named `args` no longer collides with generated locals.

- [x] **Add compile-time validation for unsupported wrapper metadata**
  - [x] For single-record wrapper params, reject `@Param(defaultValue=...)`.
  - [x] Evaluate and, if needed, reject other wrapper-only options that are semantically invalid when flattened (e.g., rename/required overrides on wrapper param).
  - [x] Emit clear diagnostic messages guiding users to place constraints on record components instead.

- [x] **Add/update processor tests**
  - [x] Add test: single-record schema is flattened (not nested under wrapper parameter name).
  - [x] Add test: generated lambda uses `invocation.getArguments()` conversion path for single-record methods.
  - [x] Add test: record parameter named `args` compiles cleanly (no generated local-name conflict).
  - [x] Add test(s): unsupported `@Param` metadata on single-record wrapper emits compile error.

- [x] **Run only impacted tests in isolation**
  - [x] Run `CopilotToolProcessorTest` with Maven.
  - [x] If failures indicate nearby impact, run only the minimum additional related processor/tool tests.

- [x] **Finalize**
  - [x] Review diff for scope and consistency.
  - [x] Update this checklist with completed items.
  - [x] Prepare commit message (no push until instructed).
