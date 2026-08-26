# Implement Java `darwin-arm64` in-process runtime support

Work autonomously in the current `copilot-sdk` repository. Implement the change completely, validate it, and leave the worktree ready for review. Do not stop at a plan, do not ask routine implementation questions, and do not commit or push unless explicitly requested.

## Required context

Read `java/docs/adr/adr-007-native-bundling-strategy.md` and deeply understand it.

This work extends two merged pull requests. Before editing anything, use `gh` to fetch and thoroughly understand them in this order:

1. PR [#2301](https://github.com/github/copilot-sdk/pull/2301), which introduced the Java in-process FFI runtime and `linux-x64` classifier.
1. PR [#2393](https://github.com/github/copilot-sdk/pull/2393), which added `win32-x64`, host-specific packaging, multi-runner testing, artifact handoff, and coordinated Maven Central release/snapshot publication.

Read their descriptions, changed-file lists, commits, diffs, and relevant review context. Then inspect the current versions of all affected files; current `main` is authoritative where it differs from a merged PR diff.

Also read and follow all repository instructions that apply to Java, Maven, workflows, and documentation. Run every Maven command from `java/`, with the required Java environment and tee-to-`*job-logs*` convention from the repository instructions.

The current local runtime is native `darwin-arm64`, so use it for real host-matched packaging and in-process validation rather than relying only on mocked tests.

## Goal

Add `darwin-arm64` as the third implemented and published Java native runtime classifier, alongside:

* `linux-x64` from PR #2301.
* `win32-x64` from PR #2393.

The result must support Java in-process mode on Apple Silicon macOS, build the macOS classifier only on a matching macOS ARM64 host, test it in CI, and publish all three classifiers under one coordinated Maven deployment for releases and snapshots.

Preserve existing default subprocess behavior. In-process mode remains experimental and opt-in.

## Non-negotiable invariants

Preserve and extend the safety model established by PR #2393:

* Build each classifier on its matching native host:
  * `linux-x64` on Ubuntu x64 glibc.
  * `win32-x64` on Windows x64.
  * `darwin-arm64` on Apple Silicon macOS.
* Validate the exact OS and architecture before download or packaging. Unsupported or mismatched hosts must fail rather than emit a mislabeled classifier.
* Fetch `@github/copilot-darwin-arm64` at the exact version and SHA-512 integrity pinned in `nodejs/package-lock.json`.
* Package these nonempty resources in the Darwin classifier:
  * `native/darwin-arm64/runtime.node`
  * `native/darwin-arm64/copilot`
  * `native/darwin-arm64/platform.properties`
* Keep the bundled `copilot` executable during the active Rust migration. Do not interpret issue #2399's title as permission to remove the transitional embedded-host executable or broadly remove Node.js requirements.
* Keep the primary runtime JAR OS-neutral and free of every platform-native resource.
* Reject cross-classifier contamination in classifier JARs.
* Build all classifiers from the same immutable source and Maven version.
* Perform exactly one Maven deployment from Ubuntu after validating and attaching the Windows and Darwin handoffs. This preserves one snapshot timestamp/build number and one signed release artifact set.
* Publish checksums and report all three classifier artifacts only after Maven deployment succeeds.
* Do not weaken release rollback safeguards, source identity checks, checksum checks, artifact-name checks, pinned-native-version checks, signing checks, or local publication validation.
* Do not hand-edit generated sources.
* Do not modify unrelated languages or workflows.

The Java runtime loader and `PlatformDetector` already recognize `darwin-arm64`. Do not rewrite them merely to show activity. Change Java production code only if investigation or native validation exposes a real Darwin-specific defect.

## Implementation requirements

Investigate all hard-coded one-platform and two-platform assumptions, not only the obvious workflow matrices. At minimum, address the following surfaces.

### Native Maven packaging

Update `java/copilot-native/pom.xml` following the existing Linux and Windows profiles:

* Add a host-activated `native-darwin-arm64` profile for Apple Silicon macOS.
* Set the classifier to `darwin-arm64` and the CLI filename to `copilot`.
* Bind the shared host validation, fetch, script-test, classifier-JAR, and structural-verification executions exactly as the implemented host profiles do.
* Ensure `-Pinprocess` selects `darwin-arm64` on this host instead of retaining its Linux default.
* Add a validated external Darwin-classifier attachment profile for the Ubuntu publication aggregator, analogous to the external Windows attachment profile.
* Keep attachment validation before artifact attachment and use the exact expected Maven filename.
* Preserve skip-download behavior.

Update `java/sdk/pom.xml` so its `inprocess` test dependency resolves the `darwin-arm64` classifier on Apple Silicon macOS, analogous to the Windows override.

Use Maven's actual normalized OS/architecture values for the configured JDK. Confirm profile activation locally; do not guess at `arm64` versus `aarch64`.

### Native scripts and tests

Extend the reusable native tooling rather than adding Darwin-only copies:

* `validate-native-host.mjs`: accept only `platform=darwin`, `arch=arm64` for `darwin-arm64`, with no Linux libc requirement.
* `validate-native-host.test.mjs`: cover Darwin acceptance and wrong-OS/wrong-architecture rejection while retaining Linux and Windows coverage.
* `fetch-native.test.mjs`: include `darwin-arm64` in the platform table and preserve correct non-Windows CLI naming and executable behavior.
* `validate-native-artifact.mjs`: ensure Darwin classifier contents and metadata are validated and ensure placeholder validation rejects Darwin native resources too. Prefer a maintainable all-native-resource invariant over another fragile two-platform regex.
* `create-native-classifier-test-fixture.mjs`, `validate-native-artifact.test.mjs`, and `validate-local-publication.mjs`: extend fixtures, pinned package data, contamination tests, exact expected JAR sets, and signed local-publication validation to all three implemented classifiers.

Keep the fetch implementation generic if it already handles Darwin correctly. Add or change production logic only where required.

### Java and native CI

Update `.github/workflows/java-sdk-tests.yml`:

* Add `darwin-arm64` on an Apple Silicon GitHub-hosted macOS runner to the in-process test matrix.
* Run the same host validation and `mvn clean verify -Pinprocess` behavior as Linux and Windows.
* Add a Darwin native-publication input job parallel to the Windows input job. It must validate source identity, build only the Darwin classifier, validate the classifier and neutral placeholder, generate and validate a SHA-256 manifest, and upload only the Darwin JAR and checksum with a unique run/attempt-scoped artifact name.
* Make the Ubuntu publication-assembly job depend on both native input jobs.
* Verify that Linux, Windows, and Darwin inputs all have the same immutable source SHA and Maven version before local deployment.
* Download, checksum-validate, classifier-validate, and attach both external classifiers; build Linux locally; then validate one complete signed local publication containing exactly the neutral artifacts and all three classifier JARs.

Do not assume `macos-latest` is ARM64 without confirming the current GitHub-hosted runner label. Use the repository's existing pinned-action conventions.

### Maven Central release workflow

Update `.github/workflows/java-publish-maven.yml` by extending the PR #2393 coordinated model:

* Add a Darwin classifier build job after release preparation, parallel to the Windows job, on an Apple Silicon macOS runner.
* Check out the prepared release tag and verify its commit equals the recorded immutable tag commit.
* Validate the host, build and validate `darwin-arm64`, validate the neutral placeholder, create and validate its SHA-256 manifest, and upload only the classifier and checksum.
* Make the single Ubuntu deploy job require and download both Windows and Darwin handoffs.
* Validate exact filenames, checksums, pinned native metadata, source identity, and version before attaching either handoff.
* Pass both external classifier paths to Maven, build Linux locally, and deploy the neutral artifacts plus all three classifiers once.
* Include `darwin-arm64`, its macOS runner, artifact filename, and SHA-256 in the one post-success summary.
* Update rollback job dependencies so failures in either native build or deployment trigger the existing guarded rollback behavior. Do not otherwise loosen or redesign rollback safety.

### Maven Central snapshot workflow

Update `.github/workflows/java-publish-snapshot.yml` with the same three-host publication topology:

* Resolve one immutable snapshot source.
* Build Windows and Darwin classifiers independently on matching runners.
* Verify each checkout matches the resolved source and both Maven versions match the deploy checkout.
* Upload run/attempt-scoped JAR/checksum handoffs.
* In the sole Ubuntu deploy job, validate and attach both external classifiers, build Linux, and deploy all three classifiers once.
* Emit one post-success summary listing Linux, Windows, and Darwin with runner, filename, and SHA-256.
* Preserve scheduled/default-branch and manual-dispatch behavior from PR #2393.

Avoid copy/paste drift where a small, clear matrix or shared script is safer, but do not force a matrix when GitHub Actions output or artifact semantics become less reliable. Correct source/version handoff is more important than reducing YAML lines.

### Documentation

Update `java/README.md` and `java/docs/adr/adr-007-native-bundling-strategy.md` to describe the implemented state accurately:

* Supported in-process classifiers are now `linux-x64` glibc, `win32-x64`, and `darwin-arm64`.
* Show how consumers choose the Darwin classifier.
* Document native Apple Silicon Maven activation and `mvn -Pinprocess clean verify`.
* Document Darwin classifier contents.
* Describe the three-runner, one-deployment release and snapshot model.
* Change statements that currently describe macOS as unsupported or say only two classifiers are published.
* Preserve the distinction between all eight classifiers recognized by runtime detection and the three classifiers actually packaged/published.
* Keep the transitional bundled CLI explanation.

Keep documentation factual and avoid claiming `darwin-x64` or any ARM/ musl target other than `darwin-arm64` is implemented.

## Validation

Use the smallest focused checks while iterating, then run the complete host-matched validation. At minimum:

1. Run the Node native script test suite used by the Maven module.
1. Confirm Maven activates both native Darwin profiles on this `darwin-arm64` host and resolves the SDK test classifier as `darwin-arm64`.
1. From `java/`, run the full native in-process reactor validation:

   ```text
   mvn clean verify -Pinprocess
   ```

1. Inspect the produced Darwin classifier JAR and prove it contains exactly the expected Darwin native resource tree with nonempty `runtime.node`, `copilot`, and correct `platform.properties`.
1. Prove the primary runtime JAR has no platform-native resources.
1. Exercise local publication validation for exactly three classifiers, including signatures where the existing workflow requires them.
1. Run Spotless/checkstyle or the smallest existing formatting/lint checks covering changed Java/Maven files.
1. Validate edited workflow YAML with an existing repository mechanism if one exists; do not add a new lint dependency.
1. Review the final diff for stale two-platform wording and hard-coded classifier lists.

All Maven invocations and Maven log inspection must follow the repository's Java command/bootstrap and `*job-logs*` requirements. Do not use `-q` for `mvn verify`, do not pipe Maven output through `grep`, and do not claim success from an uninspected exit status/log.

If real in-process execution reveals a Darwin-specific loader, JNA, executable-permission, quarantine, atomic-publication, or library-locking issue, diagnose and fix the root cause with focused tests. Do not add broad catches, silent fallbacks, or platform skips to make CI green.

## Completion criteria

Finish only when:

* `darwin-arm64` packages and runs in-process successfully on the current host.
* Linux and Windows behavior remains intact.
* CI covers in-process execution on all three hosts.
* release, snapshot, and local publication assembly include exactly all three classifiers under one Maven deployment.
* checksums, source/version identity, classifier contents, placeholder purity, and signing validation remain enforced.
* README and ADR match the implemented behavior.
* the final worktree contains only intentional source changes and any pre-existing user files; temporary build artifacts and logs are not staged.

In the final response, lead with the implemented outcome, identify the meaningful packaging/workflow changes, and state any validation that could not be completed. Do not provide a plan or a list of optional follow-up work.
