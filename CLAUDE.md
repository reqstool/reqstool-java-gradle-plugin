# CLAUDE.md

This file orients contributors and AI agents working in this repository.

## What this project is

A Gradle plugin (`io.github.reqstool.gradle-plugin`) for
[reqstool](https://github.com/reqstool/reqstool-client). It collects `@Requirements` and
`@SVCs` annotations emitted by the reqstool annotation processor during compilation,
combines them with a dataset (`requirements.yml`, etc.) and test result XML files, and
packages everything into a `<name>-<version>-reqstool.zip` artifact that the reqstool CLI
can analyze. Requires Java 21+.

See `README.md` for the user-facing configuration reference (extension properties, task
behavior, JUnit 5 parameterized test setup) and `CONTRIBUTING.md` for setup/DCO/commit
conventions. This file focuses on internal structure, tests, and fixtures.

## Project structure

```
src/main/java/io/github/reqstool/gradle/
  RequirementsToolPlugin.java     # registers the assembleRequirements task, wires
                                   # compileJava/build dependencies, configures publishing
  RequirementsToolTask.java       # core logic: combine/merge annotation YAML, assemble zip
  RequirementsToolExtension.java  # DSL + zero-config defaults

src/test/java/io/github/reqstool/gradle/
  RequirementsToolTaskTest.java   # unit tests (see below)
  BuildLifecycleWiringTest.java   # TestKit: how the task joins `build` (issue #88)

src/test/resources/{yml,zip}/     # tracked reference fixtures (see "Fixtures" below)

tests/fixtures/test_project/      # standalone Gradle project for manual end-to-end validation

docs/modules/ROOT/                # Antora documentation source (published to reqstool.github.io)
```

The main task is `assembleRequirements` (group `build`). When the `java` plugin is applied,
the plugin auto-wires it to depend on `check`, on `compileJava` and on every non-main source
set's compile task, and makes `build` depend on `assembleRequirements`.

Use `dependsOn` rather than `finalizedBy` for that last edge. `dependsOn` is idempotent, so
a consumer that wired the lifecycle by hand — as 0.1.0 and 0.1.1 required — merely repeats
an edge that already exists. `finalizedBy` states the opposite order and Gradle rejects the
pair as a circular dependency at configuration time; that was issue #88.

## Running tests

```bash
./gradlew test     # unit tests only
./gradlew build    # compile, test, assemble plugin jar
./gradlew format   # apply io.spring.javaformat formatting
```

## Unit tests — `RequirementsToolTaskTest`

Uses `ProjectBuilder` with a `@TempDir` project directory and builds YAML in-memory via
Jackson (`ObjectMapper` + `YAMLFactory`) — it does **not** read from `src/test/resources`.

| Test | Validates |
|---|---|
| `testCombineOutput_bothEmpty` / `_withImplementations` / `_withTests` / `_withBoth` | `combineOutput()` produces a `requirement_annotations` node with `implementations`/`tests` keys present only when non-empty |
| `testTaskConfiguration` | Extension defaults (`outputDirectory`, `datasetPath`, `skip`, `skipAssembleZipArtifact`, etc.) flow correctly into task properties |
| `testSkipExecution` | `skip = true` causes `task.execute()` to no-op without throwing |
| `testMergeTestNodes_mergesTwoFiles` | `mergeTestNodes()` unions SVC entries from multiple source-set `annotations.yml` files, merging arrays for keys present in both (e.g. `SVC_001` ends up with entries from both files) |
| `testSetSvcsAnnotationsFilesMarksExplicit` | Setting `svcsAnnotationsFiles` explicitly marks the extension as no longer using auto-discovery |
| `testDeprecatedSvcsAnnotationsFileSetter` | The deprecated singular `setSvcsAnnotationsFile()` still works and delegates to `svcsAnnotationsFiles` |
| `testMissingRequirementsFile` | `task.execute()` throws an exception whose message mentions `requirements.yml` when the dataset directory doesn't contain it |

## Lifecycle tests — `BuildLifecycleWiringTest`

TestKit (`GradleRunner`) over a throwaway consumer project, run with `--dry-run`: a circular
dependency is raised while Gradle builds the task graph, so the failure is reproduced
without compiling or resolving anything.

| Test | Validates |
|---|---|
| `consumerDeclaringBuildDependsOnAssembleRequirementsStillConfigures` | A consumer's own `build.dependsOn(assembleRequirements)` does not collide with the plugin's wiring — the regression in #88 |
| `assembleRequirementsIsPartOfBuildWithoutConsumerWiring` | The plugin alone still puts the task into `build` |
| `assembleRequirementsRunsAfterCheck` | `check` is ordered first, since the task reads test-result XML |

`tests/fixtures/test_project` cannot cover this: it declares no wiring of its own, so it only
ever exercises the case where the plugin is the sole author of these edges.

## Fixtures

### `src/test/resources/{yml,zip}/`

Tracked example files showing the shape of annotation YAML (`requirements_annotations.yml`,
`svcs_annotations.yml`, `combined_annotations.yml`, following the reqstool
`annotations.schema.json`) and of an assembled zip's contents (`zip/` — dataset YAML files,
combined `annotations.yml`, `surefire-reports/`, `failsafe-reports/`).

**These are not currently loaded by `RequirementsToolTaskTest`** (which builds its test data
inline) — treat them as reference examples of expected file formats, not as golden files
with an automated regeneration step.

### `tests/fixtures/test_project/` — manual end-to-end validation

A minimal real Gradle project (`mypackage`) with three source sets, each producing one
annotation that must survive the full pipeline:

| Source set | Directory | Produces |
|---|---|---|
| `main` | `src/main/java` | `@Requirements` on `Hello.hello()` → `REQ_001` |
| `test` | `src/test/java` | `@SVCs` on `HelloTest.testHello()` → `SVC_001` |
| `integrationTest` | `src/integration-test/java` | `@SVCs` on `HelloIT.testHelloIntegration()` → `SVC_002` |

Only source files, `build.gradle`, `settings.gradle`, and `docs/reqstool/*.yml` are tracked
in git; `build/` and `.gradle/` are local build output and are gitignored.

To validate (or to regenerate/inspect expected output after an intentional change to plugin
behavior), run from `tests/fixtures/test_project/`:

```bash
gradle build
ls build/reqstool/mypackage-0.1.0-reqstool.zip
unzip -l build/reqstool/mypackage-0.1.0-reqstool.zip
grep "SVC_" build/reqstool/annotations.yml   # expect both SVC_001 and SVC_002
```

Optionally, extract the zip and run `reqstool status local -p <extracted-dir>` (requires
`pip install reqstool`) — expect `REQ_001` implemented and `SVC_001`/`SVC_002` both covered.
See `tests/fixtures/test_project/README.md` for full expected output.
