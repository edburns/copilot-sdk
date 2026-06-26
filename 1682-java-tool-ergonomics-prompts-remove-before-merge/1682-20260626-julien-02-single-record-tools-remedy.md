# Single-record tools remedy plan (PR #1792 follow-up)

## Goals

- Ensure single-record `@CopilotTool` methods generate schema and handler logic with matching payload shape.
- Prevent additional single-record edge cases from slipping through.
- Add focused regression tests that fail on the old behavior and pass on the corrected behavior.

## Plan with progress checkboxes

- [ ] **Align single-record schema and handler**
  - [ ] Add a single-record fast path in `CopilotToolProcessor.generateSchemaWithParamMetadata(...)`.
  - [ ] For that path, emit the record schema directly at top level (record components become top-level properties).
  - [ ] Keep runtime extraction aligned by converting directly from `invocation.getArguments()` for single-record methods.

- [ ] **Eliminate local variable naming collision**
  - [ ] Ensure generated single-record code does not declare local `Map<String, Object> args`.
  - [ ] Verify a record parameter literally named `args` no longer collides with generated locals.

- [ ] **Add compile-time validation for unsupported wrapper metadata**
  - [ ] For single-record wrapper params, reject `@Param(defaultValue=...)`.
  - [ ] Evaluate and, if needed, reject other wrapper-only options that are semantically invalid when flattened (e.g., rename/required overrides on wrapper param).
  - [ ] Emit clear diagnostic messages guiding users to place constraints on record components instead.

- [ ] **Add/update processor tests**
  - [ ] Add test: single-record schema is flattened (not nested under wrapper parameter name).
  - [ ] Add test: generated lambda uses `invocation.getArguments()` conversion path for single-record methods.
  - [ ] Add test: record parameter named `args` compiles cleanly (no generated local-name conflict).
  - [ ] Add test(s): unsupported `@Param` metadata on single-record wrapper emits compile error.

- [ ] **Run only impacted tests in isolation**
  - [ ] Run `CopilotToolProcessorTest` with Maven.
  - [ ] If failures indicate nearby impact, run only the minimum additional related processor/tool tests.

- [ ] **Finalize**
  - [ ] Review diff for scope and consistency.
  - [ ] Update this checklist with completed items.
  - [ ] Prepare commit message (no push until instructed).
