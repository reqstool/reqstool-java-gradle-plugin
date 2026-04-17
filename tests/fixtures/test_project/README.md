# mypackage

Minimal test project for manual validation of `reqstool-java-gradle-plugin`.

## Source sets

| Source set | Directory | Annotates |
|---|---|---|
| `main` | `src/main/java` | `@Requirements` on `Hello.hello()` → `REQ_001` |
| `test` | `src/test/java` | `@SVCs` on `HelloTest.testHello()` → `SVC_001` |
| `integrationTest` | `src/integration-test/java` | `@SVCs` on `HelloIT.testHelloIntegration()` → `SVC_002` |

The `integrationTest` suite exercises multi-source-set annotation merging: both
`SVC_001` and `SVC_002` must appear in the combined `annotations.yml`.

## Prerequisites

- Java 21+
- Gradle 9+ (or use the repo's `gradlew` wrapper if available)
- `reqstool` CLI: `pip install reqstool`

## Validation

Run all commands from `tests/fixtures/test_project/`.

### 1 — Build

```bash
gradle build
```

This runs compilation (triggering the APT annotation processor for all source
sets), unit tests, integration tests, and `assembleRequirements` in one step.

Expected output:
```
> Task :compileJava
Note: Processing annotations: [io.github.reqstool.annotations.Requirements]
Note: Writing Requirements Annotations data to: build/.../main/resources/annotations.yml

> Task :compileTestJava
Note: Processing annotations: [io.github.reqstool.annotations.SVCs]
Note: Writing Requirements Annotations data to: build/.../test/resources/annotations.yml

> Task :compileIntegrationTestJava
Note: Processing annotations: [io.github.reqstool.annotations.SVCs]
Note: Writing Requirements Annotations data to: build/.../integrationTest/resources/annotations.yml

> Task :test
> Task :integrationTest
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
- `mypackage-0.1.0-reqstool/test_results/TEST-io.github.reqstool.example.HelloIT.xml`
- `mypackage-0.1.0-reqstool/reqstool_config.yml`

The combined `annotations.yml` must contain both SVCs:

```bash
grep "SVC_" build/reqstool/annotations.yml
# expected: SVC_001 and SVC_002
```

### 3 — Run reqstool status

The zip is self-contained (test results included), so just extract and run:

```bash
unzip -o build/reqstool/mypackage-0.1.0-reqstool.zip -d /tmp/mypackage-reqstool
reqstool status local -p /tmp/mypackage-reqstool/mypackage-0.1.0-reqstool
```

Expected: all green — `REQ_001` implemented, `SVC_001` and `SVC_002` both
covered, no missing tests or SVCs.
