[![Commit Activity](https://img.shields.io/github/commit-activity/m/reqstool/reqstool-java-gradle-plugin?label=commits&style=for-the-badge)](https://github.com/reqstool/reqstool-java-gradle-plugin/pulse)
[![GitHub Issues](https://img.shields.io/github/issues/reqstool/reqstool-java-gradle-plugin?style=for-the-badge&logo=github)](https://github.com/reqstool/reqstool-java-gradle-plugin/issues)
[![License](https://img.shields.io/github/license/reqstool/reqstool-java-gradle-plugin?style=for-the-badge&logo=opensourceinitiative)](https://opensource.org/license/mit/)
[![Build](https://img.shields.io/github/actions/workflow/status/reqstool/reqstool-java-gradle-plugin/build.yml?style=for-the-badge&logo=github)](https://github.com/reqstool/reqstool-java-gradle-plugin/actions/workflows/build.yml)
[![Documentation](https://img.shields.io/badge/Documentation-blue?style=for-the-badge&link=docs)](https://reqstool.github.io)

# Reqstool Gradle Plugin

Gradle build plugin for [reqstool](https://github.com/reqstool/reqstool-client) that assembles requirements traceability artifacts.

## Overview

Collects `@Requirements` and `@SVCs` annotations from compiled Java code, combines them with test results, and packages everything into a ZIP artifact for analysis by the reqstool CLI. Supports Java 21+.

## Installation

Add the plugin to your `build.gradle`:

```groovy
plugins {
    id 'io.github.reqstool.gradle-plugin' version '0.1.0'
}

requirementsTool {
    datasetPath = file('docs/reqstool')
}

tasks.named('build') {
    finalizedBy tasks.named('assembleRequirements')
}

tasks.named('assembleRequirements') {
    dependsOn tasks.named('test')
}
```

## Usage

```bash
gradle clean build
```

The plugin generates a ZIP artifact in `build/reqstool/` containing requirements, annotations, and test results.

## Documentation

Full documentation can be found [here](https://reqstool.github.io).

## Contributing

See the organization-wide [CONTRIBUTING.md](https://github.com/reqstool/.github/blob/main/CONTRIBUTING.md).

## License

MIT License.
