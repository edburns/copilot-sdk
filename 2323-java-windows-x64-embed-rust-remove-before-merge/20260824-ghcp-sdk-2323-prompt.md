# Add Java in-process native runtime packaging for Windows x64

Work autonomously in the current `github/copilot-sdk` checkout. Use `copilot --yolo`-level initiative: inspect the existing implementation, make the complete surgical change, run the required validation, and iterate until it works. Do not stop after proposing a plan.

The current host is native **Windows x64**. The checked-out branch is based on `main` after PRs #2301 and #2345 and is the authoritative starting point. Preserve unrelated work.

## Objective

Extend the experimental Java in-process Copilot CLI support from Linux x64 glibc to **Windows x64**.

The expected implementation is primarily packaging and build wiring. PR #2301 already added:

* The JNA-based in-process runtime and C ABI bridge.
* Runtime platform detection for all ADR classifiers, including `win32-x64`.
* Windows-aware classpath extraction using `native/win32-x64/runtime.node` and `native/win32-x64/copilot.exe`.
* Build-time fetching from `@github/copilot-<classifier>`, including existing `copilot.exe` handling for Windows.
* Per-platform classifier JARs.

PR #2345 then made native packaging host-gated so unsupported hosts cannot accidentally publish a `linux-x64` artifact. Keep and generalize those protections. A Windows x64 build may produce a `win32-x64` classifier JAR, but must never produce a `linux-x64` JAR. Linux x64 glibc behavior must remain unchanged, and unsupported hosts must remain placeholder-only.

The end-user behavior must be:

1. The user's POM includes `com.github:copilot-sdk-java-runtime:<version>:win32-x64` and JNA in addition to the SDK.
1. The user's Java code explicitly selects `RuntimeConnection.forInProcess()`.
1. On Windows x64, the SDK loads the classifier JAR's `runtime.node`, extracts its version-matched `copilot.exe`, and uses that embedded CLI through the native runtime instead of requiring a preinstalled CLI.
1. Existing subprocess behavior remains the default when in-process mode is not selected.
1. In-process selection remains strict: failures must surface rather than silently falling back to a preinstalled CLI transport.

## Required investigation

Before editing:

1. Read all repository and Java instructions, including the Java coding skill.
1. Inspect `git status`, the current branch history, and the current diff.
1. Use `gh` to read PR #2301 and PR #2345, including their descriptions, commits, changed files, and relevant diffs. Understand why #2345 introduced host and libc guards before modifying them.
1. Read `java/docs/adr/adr-007-native-bundling-strategy.md` in full.
1. Read at least:
   * `java/copilot-native/pom.xml`
   * `java/copilot-native/scripts/fetch-native.mjs`
   * `java/copilot-native/scripts/fetch-native.test.mjs`
   * `java/copilot-native/scripts/validate-native-host.mjs`
   * `java/copilot-native/scripts/validate-native-host.test.mjs`
   * `java/sdk/pom.xml`, especially its `inprocess` profile and CLI path
   * `java/sdk/src/main/java/com/github/copilot/ffi/PlatformDetector.java`
   * `java/sdk/src/main/java/com/github/copilot/ffi/NativeRuntimeLoader.java`
   * Their tests
   * `java/README.md`
   * `.github/workflows/java-sdk-tests.yml`
   * The `@github/copilot-win32-x64` entry in `nodejs/package-lock.json`
1. Confirm the actual host is Windows x64 before invoking the Windows native lifecycle. Do not fake Windows activation on another platform.

Use the existing shared mechanisms. Do not reimplement the FFI bridge or introduce a second Windows-specific fetch/package pipeline.

## Implementation requirements

### Maven native module

Update `java/copilot-native/pom.xml` so Windows x64 is an implemented, host-matched classifier:

* Add a `native-win32-x64` host profile with exact Windows x64 activation.
* Set `copilot.native.classifier=win32-x64`.
* Set `copilot.native.cli.filename=copilot.exe`.
* Bind the same shared `validate-native-host`, `fetch-native`, `test-fetch-native`, `jar-native`, and `verify-native-jars` executions used by the Linux implementation. Do not duplicate their configuration.
* Ensure the Windows classifier JAR is named `copilot-sdk-java-runtime-<version>-win32-x64.jar`.
* Ensure it contains exactly the expected native resource set under `native/win32-x64/`: nonempty `runtime.node`, `copilot.exe`, and `platform.properties`.
* Ensure `platform.properties` identifies `classifier=win32-x64` and the pinned native package version.
* Keep the primary JAR OS-neutral and free of native binaries.
* Preserve sources and Javadoc artifacts required by Maven Central.
* Preserve `copilot.native.skip.download=true`: it must disable validation, download, staging, classifier packaging, and native structural checks while still producing placeholder artifacts.
* Do not add `os-maven-plugin`; Maven host profiles are the established design.

Pay special attention to the existing `inprocess` profile in this module. It currently hard-codes `linux-x64`. Refactor the profile interaction so `mvn -Pinprocess` selects and packages the actual implemented host classifier on Linux x64 glibc or Windows x64. It must not let one active profile overwrite another platform's classifier. Unsupported hosts must still fail early and clearly when in-process tests are explicitly requested.

### Host validation and scripts

Extend `validate-native-host.mjs` and its tests:

* Accept `win32-x64` only when Node reports `platform=win32` and `arch=x64`.
* Do not apply a glibc requirement on Windows.
* Retain the Linux x64 glibc validation and rejection of musl/unknown libc.
* Reject classifier/host mismatches and unimplemented classifiers with actionable messages.

`fetch-native.mjs` already contains Windows handling. Preserve and verify it rather than replacing it. Make its tests genuinely cross-platform:

* Exercise the Windows `copilot.exe` staged artifact and fast-path integrity invariant on Windows.
* Avoid POSIX-only fake executables or shell assumptions in tests running on Windows; use an appropriate `.cmd`/launcher when needed.
* Retain coverage that a missing, stale, or mismatched CLI forces restaging.
* Retain exact package-lock version/integrity verification and same-package pairing of `runtime.node` and the CLI.

### SDK in-process test wiring

Remove Linux-only assumptions from `java/sdk/pom.xml` without changing the public API:

* The `-Pinprocess` test dependency must resolve the host's implemented classifier (`linux-x64` or `win32-x64`), not always `linux-x64`.
* The default in-process CLI path must use the matching npm package and filename:
  * Linux: `@github/copilot-linux-x64/copilot`
  * Windows: `@github/copilot-win32-x64/copilot.exe`
* Keep classifier selection explicit and host-gated. Do not make unsupported platforms appear supported.
* Keep normal consumers responsible for declaring the classifier(s) they need.

The production `PlatformDetector` and `NativeRuntimeLoader` already claim Windows support. Change production Java only if investigation or a failing Windows test reveals a real gap. Add or adapt focused tests so Windows verifies `win32-x64`, `copilot.exe`, extraction, cache reuse, and entrypoint resolution instead of skipping all meaningful loader tests behind Linux-only assumptions. Keep POSIX executable-bit assertions restricted to POSIX hosts.

### CI and documentation

Update Java-related GitHub Actions so Windows x64 native packaging and in-process behavior receive blocking coverage while retaining Linux x64 glibc coverage. Prefer a clear matrix or a dedicated Windows job over shell-condition complexity. Use PowerShell on Windows and bash on Linux. Do not weaken existing Linux validation or make experimental failures non-blocking.

Update:

* `java/README.md` to document Linux x64 and Windows x64 support, the `win32-x64` dependency, JNA, explicit Java opt-in, Windows build commands, produced artifacts, and placeholder-only behavior on unsupported hosts.
* ADR-007's current platform scope and consequences so the implemented set is Linux x64 glibc plus Windows x64. Preserve the per-platform classifier decision and the remaining-platform follow-up status.

Follow `.github/instructions/docs-style.instructions.md` for ADR changes.

## Boundaries

* Keep changes inside `java/**` and Java-related GitHub Actions, apart from this task directory. Ask before touching anything else.
* Do not hand-edit generated Java sources.
* Do not add macOS, ARM64, or Linux musl packaging.
* Do not weaken or remove PR #2345's host/classifier and libc protections.
* Do not force a Windows classifier on non-Windows hosts or a Linux classifier on Windows.
* Do not publish a classifier whose binary was not fetched and validated for the actual host.
* Do not make in-process mode the default.
* Do not add silent fallback to stdio, TCP, PATH, or a preinstalled CLI after explicit in-process selection.
* Do not duplicate common native build configuration across platform profiles.
* Do not commit, push, or open a PR unless explicitly requested.

## Windows validation

All Java-related shell commands must first source:

```powershell
. "C:\Users\edburns\bin\env-java25.ps1"
```

Start every Maven command in `java`. Pipe stdout and stderr through `Tee-Object` to an exact filename matching `YYYYMMDD-HHMM-job-logs.txt`, inspect that exact log, and do not use a guessed/latest glob. If two Maven commands would start in the same minute, wait for a new minute so each required log remains distinct.

At minimum, perform these checks on this native Windows x64 host:

1. Run the Node script tests for fetch and host validation.
1. Run `mvn -pl copilot-native help:active-profiles` and confirm `native-win32-x64` is active and `native-linux-x64` is not.
1. Run `mvn -pl copilot-native clean verify`.
1. Inspect the exact classifier JAR contents and confirm:
   * `native/win32-x64/runtime.node`
   * `native/win32-x64/copilot.exe`
   * `native/win32-x64/platform.properties`
   * no `native/linux-x64/**`
1. Confirm the primary JAR contains no `runtime.node` or native CLI.
1. Run the focused SDK unit tests affected by Windows loader/profile changes.
1. Run the full Windows in-process reactor validation with `mvn clean verify -Pinprocess -Dcopilot.native.skip.download=false`. Confirm tests use the embedded/classpath Windows artifacts and do not require a separately installed Copilot CLI for the in-process transport.
1. Run the placeholder-only check:

```powershell
mvn clean package -pl copilot-native -DskipTests -Dcopilot.native.skip.download=true
```

Confirm there is no `native-staging` directory, no `-win32-x64.jar`, no `-linux-x64.jar`, no native download, and no native structural verification, while the primary, sources, and Javadoc JARs are present.

Use the repository-required Maven command shape, for example:

```powershell
. "C:\Users\edburns\bin\env-java25.ps1"
Set-Location java
$log = "$(Get-Date -Format 'yyyyMMdd-HHmm')-job-logs.txt"
mvn -pl copilot-native clean verify 2>&1 | Tee-Object -FilePath $log
```

Evaluate success from both Maven's exit code and the exact retained log. If registry authentication or another external prerequisite blocks a real package download, still complete all deterministic script/unit/profile work, report the exact blocker plainly, and do not claim end-to-end success.

## Regression expectations

Reason through and preserve these behaviors even though this host cannot execute all of them:

| Host / invocation | Expected result |
| --- | --- |
| Windows x64 normal Java build | Produce only `win32-x64` native classifier plus OS-neutral artifacts |
| Windows x64 `-Pinprocess` | Package/use `win32-x64`; in-process tests run |
| Windows x64 with `copilot.native.skip.download=true` | Placeholder artifacts only |
| Linux x64 glibc with the documented opt-in or `-Pinprocess` | Existing `linux-x64` behavior remains |
| Linux x64 musl | No normal native classifier; explicit in-process request fails validation |
| macOS, ARM64, or another unsupported host | Normal build is placeholder-only; explicit in-process request fails clearly |

Where feasible, cover Maven profile logic with deterministic effective-profile checks or CI rather than pretending to run another OS locally.

## Completion criteria

The task is complete only when:

* Windows x64 produces a verified `win32-x64` classifier JAR and never a Linux classifier.
* The classifier contains the pinned, version-matched `runtime.node` and `copilot.exe`.
* Java in-process mode resolves and uses those embedded Windows resources.
* `-Pinprocess` is host-aware in both Maven modules.
* PR #2345's Linux libc, unsupported-host, and skip-download guarantees remain intact.
* Windows-focused tests and CI coverage exist.
* README and ADR accurately describe the two implemented classifiers.
* Required Windows validation passes, or any external blocker is reported without overstating completion.

In the final response, report the files changed, exact commands and exact `job-logs` filenames, produced artifact names and inspected entries, each acceptance result, and any remaining blocker.
