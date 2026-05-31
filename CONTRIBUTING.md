# Contributing to Altio

Thank you for your interest in contributing to Altio! This document outlines our contribution process, policies, and guidelines to help you get started.

## Code of Conduct

We expect all contributors to follow our Code of Conduct to ensure a welcoming and inclusive community. Please refer to [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for more details (to be added).

## Contribution Process

### 1. Opening Issues
Before starting work on a major feature or bug fix, please search the issue tracker to see if there is an existing discussion. If not, open a new issue describing:

* The problem you are trying to solve or the feature you want to add.
* Your proposed solution or implementation plan.
* Any relevant background information, logs, or screenshots.

### 2. Branch Naming Conventions
When working on a contribution, please use the following branch naming structure:

* `feature/feature-name` — for new features.
* `bugfix/issue-description` — for bug fixes.
* `docs/docs-improvement` — for documentation updates.
* `refactor/refactor-name` — for code restructuring/refactoring.

### 3. Commit Message Conventions
We follow clear commit messaging guidelines. A good commit message structure:

```
Type: Short summary (50 characters or less)

More detailed explanatory text, if necessary. Wrap it to about 72
characters.
```

Types can be `feat`, `fix`, `docs`, `style`, `refactor`, `test`, or `chore`.

### 4. Creating Pull Requests (PRs)
When you are ready to submit your changes:

1. Ensure your code passes all tests and linting checks:
   ```bash
   ./gradlew spotlessApply
   ./gradlew test
   ```
2. Open a Pull Request against the `main` branch.
3. Provide a clear description of the changes, referencing any related issues.

---

## Contributor License Agreement (CLA)

Before we can merge your first Pull Request, you must sign the Altio Contributor License Agreement (CLA) via **CLA Assistant**.

### What is the CLA?
The CLA is a license grant. In plain terms:

* **You retain copyright** to your contributions.
* You grant the project maintainer (Pedro Veloso) a perpetual, worldwide, non-exclusive, royalty-free license to use, modify, distribute, and **relicense** your contributions.

### Why is this necessary?
The CLA is required to preserve the option of **future dual-licensing**. It ensures that the project maintainer can offer alternative commercial licenses to organizations that cannot comply with the AGPL-3.0 terms, which helps fund the ongoing development of the project.

Please review the full terms in the [CLA.md](CLA.md) file before signing.

---

## Generative AI Contribution Policy

Altio follows the **NLnet GenAI Policy** as our reference standard for AI-assisted contributions. If you use generative AI tools (such as coding assistants, LLMs, or code generation models) to help write your contribution, you must adhere to the following guidelines:

1. **Disclosure:** You must disclose the use of generative AI in your commit messages. Specify the assistant name and its version (e.g., `Co-authored-by: Gemini 3.5 Flash`, `AI-assisted: Cursor v0.45`).
2. **Verification:** You are fully responsible for the code you submit. You must review, test, and understand all AI-generated code to ensure it is correct, secure, and does not violate any third-party copyrights or licenses.
3. **No Plagiarism:** Ensure that the AI assistant has not generated code verbatim from repositories with incompatible licenses.
