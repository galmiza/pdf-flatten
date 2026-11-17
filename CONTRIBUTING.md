# Contributing

Thanks for your interest! The project boils down to one idea: **flatten a PDF, offline, without losing anything**. Contributions that serve this idea are welcome; those that add screens, settings, dependencies or network access will probably be declined.

## Reporting a problem

Open an issue with:

- the phone model and Android version;
- the app used to sign or annotate the PDF;
- what you expected and what you got (a screenshot of the preview helps);
- if you can share it **without personal data**, a PDF that reproduces the problem. Never attach a confidential document.

## Submitting a change

1. Create a branch from `main`.
2. Keep the change focused, with a unit test in `app/src/test` if it touches `PdfFlattener`.
3. Check locally, like the CI does:

   ```sh
   ./gradlew lintDebug testDebugUnitTest assembleDebug
   ```

   Lint treats warnings as errors.
4. Try it on a device or emulator with `python3 tools/make_sample_pdf.py` (see the README).
5. Add a line under *Unreleased* in `CHANGELOG.md`.
6. Open a pull request that explains **why** the change is needed.

## Conventions

- Kotlin, official code style (`kotlin.code.style=official`), 4-space indentation.
- Framework SDK first: no AndroidX or new library without a strong reason, discussed in an issue.
- No extra permission, no network access.
- UI strings go in `res/values/strings.xml` (English) and in every translation: `values-fr`, `values-es`, `values-de`, `values-it`, `values-pt`, `values-nl`. When a translation is unsure, open the pull request anyway and say so: a native speaker can review it.
- Comments explain why, not what.

## Releasing (maintainers)

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Move the *Unreleased* entries of `CHANGELOG.md` under the new version number.
3. Create and push the tag: `git tag v1.1.0 && git push origin v1.1.0`. The *Release* workflow publishes the signed APK.

By contributing, you agree that your contribution is published under the [MIT license](LICENSE).
