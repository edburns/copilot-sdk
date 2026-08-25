Plan

1. Add a Windows classifier build job
   - In both `java-publish-maven.yml` and `java-publish-snapshot.yml`, add a `windows-latest` job.
   - Check out the exact release tag or snapshot SHA.
   - Set up JDK 25 and Node.js 22.
   - Run host validation for `win32-x64`.
   - Package `copilot-native` without deploying.
   - Verify the JAR contains `runtime.node`, `copilot.exe`, and `platform.properties`.
   - Upload only the `win32-x64` classifier JAR plus a SHA-256 manifest.
2. Keep the final publisher on Ubuntu
   - Ubuntu remains the single Maven Central publisher.
   - It builds the `linux-x64` classifier with `copilot.native.libc=glibc`.
   - It downloads and verifies the Windows classifier artifact.
   - Avoid publishing duplicate primary, POM, sources, or Javadoc artifacts from Windows.
3. Attach the external Windows classifier
   - Add a guarded `copilot-native` Maven profile using `build-helper-maven-plugin:attach-artifact`.
   - Activate it only when an explicit Windows classifier path is supplied.
   - Validate the external JAR’s filename and required ZIP entries before attachment.
   - Attach it as type `jar`, classifier `win32-x64`.
   - The single Ubuntu `deploy -Prelease` invocation then signs and deploys both classifiers together.
4. Restructure release publication
   - Split `java-publish-maven.yml` into:
      1. Preflight and `release:prepare`.
      2. Windows build from the generated `java/v<version>` tag.
      3. Ubuntu deployment from that same tag.
   - Replace `release:perform` with an explicit tagged checkout plus `mvn deploy`, since `release:perform` cannot pause to collect another job’s artifact.
   - Preserve documentation updates, release commits, tag creation, development-version bump, rollback handling, and site deployment dependencies.
5. Restructure snapshot publication
   - Build Windows from the same immutable commit SHA used by Ubuntu.
   - Download it into the Ubuntu job.
   - Perform one Maven deploy containing neutral artifacts plus both classifiers. This ensures all snapshot artifacts share one timestamp/build number.
6. Validation
   - Add a non-publishing integration check using a temporary local Maven repository.
   - Confirm one deployment contains:
   - Primary runtime placeholder JAR
   - Sources and Javadoc JARs
   - `linux-x64` classifier
   - `win32-x64` classifier
   - Corresponding signatures for releases
   - Confirm each classifier was built on its matching runner and no job publishes independently or partially.

This provides one atomic Central publication while ensuring each native binary is built and validated on its actual OS.
