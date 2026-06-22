# Contributing to reqstool-java-gradle-plugin

Thank you for your interest in contributing!

For DCO sign-off, commit conventions, and code review process, see the organization-wide [CONTRIBUTING.md](https://github.com/reqstool/.github/blob/main/CONTRIBUTING.md).

## Prerequisites

- Java 21+
- Gradle 9.3+ (use the wrapper)

## Setup

```bash
git clone https://github.com/reqstool/reqstool-java-gradle-plugin.git
cd reqstool-java-gradle-plugin
```

If using Claude Code, regenerate the `opsx` slash commands and OpenSpec skills
(`.claude/commands/opsx/`, `.claude/skills/openspec-*`) after cloning — they're
CLI-generated tool scaffolding, not committed to the repo:

```bash
openspec update   # or: openspec init --tools claude --force
```

## Build & Test

```bash
./gradlew build
```
