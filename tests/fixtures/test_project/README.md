# mypackage

Minimal test project for manual validation of `reqstool-java-gradle-plugin`.

## Prerequisites

- Java 21+
- Gradle 8+ (or use the repo's `gradlew` wrapper if available)
- `reqstool` CLI: `pip install reqstool`

## Validation

Run all commands from `tests/fixtures/test_project/`.

### 1 — Build

```bash
gradle build
```

This runs compilation (triggering the APT annotation processor), tests, and
`assembleRequirements` in one step.

Expected output:
```
> Task :compileJava
Note: Processing annotations: [io.github.reqstool.annotations.Requirements]
Note: Writing Requirements Annotations data to: build/.../annotations.yml

> Task :compileTestJava
Note: Processing annotations: [io.github.reqstool.annotations.SVCs]
Note: Writing Requirements Annotations data to: build/.../annotations.yml

> Task :test
> Task :assembleRequirements
BUILD SUCCESSFUL
```

### 2 — Check artefacts

```bash
# zip must exist
ls build/reqstool/mypackage-0.1.0-reqstool.zip

# zip must contain all reqstool files + test results
unzip -l build/reqstool/mypackage-0.1.0-reqstool.zip
```

Expected entries in the zip:
- `mypackage-0.1.0-reqstool/requirements.yml`
- `mypackage-0.1.0-reqstool/software_verification_cases.yml`
- `mypackage-0.1.0-reqstool/annotations.yml`
- `mypackage-0.1.0-reqstool/test_results/TEST-io.github.reqstool.example.HelloTest.xml`
- `mypackage-0.1.0-reqstool/reqstool_config.yml`

### 3 — Run reqstool status

The zip is self-contained (test results included), so just extract and run:

```bash
unzip -o build/reqstool/mypackage-0.1.0-reqstool.zip -d /tmp/mypackage-reqstool
reqstool status local -p /tmp/mypackage-reqstool/mypackage-0.1.0-reqstool
```

Expected: all green — `REQ_001` implemented, `T1 P1`, no missing tests or SVCs.
