# Changelog

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.2.0] - 2026-11-17

### Added

- Spanish, German, Italian, Portuguese and Dutch translations; per-app language choice on Android 13+.
- Theme setting: system default, light or dark.
- Contact address and source code link in the settings.
- Google Play kit: store texts and graphics in 7 languages, field-by-field Play Console answers, scripts to regenerate the screenshots.

### Changed

- Result summary lines no longer break inside an item.

## [1.1.0] - 2026-11-17

### Added

- Result summary per family: handwriting and signatures, text boxes, highlights and shapes, comments, form fields, links.
- Original / Flattened switch on the preview, to see what viewers that hide floating layers display.
- Password prompt for locked PDFs; the flattened copy is saved without protection.
- Settings screen: choose which families to merge (form fields can stay fillable, links can be removed), file name suffix, privacy policy, open-source licenses, version.
- New design: rounded cards and buttons, light and dark themes, Material You colors on Android 12+.
- AAB built by the release workflow for Google Play; publishing checklist and privacy policy.

### Changed

- Unselected annotations stay in the document with their popups.
- Form fields with a missing appearance are drawn before merging, and the form is no longer modified when form fields are left fillable.

## [1.0.0] - 2026-11-17

### Added

- Vector flattening of every visible annotation: handwritten signatures, stamps, highlights, shapes, free text, notes.
- Drawings generated for annotations that have none, and for form fields flagged `NeedAppearances`.
- Form fields (AcroForm) merged into the pages, then the form removed; XFA data removed.
- Links kept; popups and hidden annotations removed.
- Import from the file picker, Android's "Open with" and the "Share" menu.
- Page-by-page processing with progress, capped memory and spill-over to temporary files.
- Page-by-page preview with Android's native PDF engine.
- Save as `<name>_flattened.pdf` and direct sharing of the result.
- English and French interface, light and dark themes.
- GitHub Actions CI (lint, tests, APKs) and signed releases published on tags.

[Unreleased]: https://github.com/galmiza/pdf-flatten/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/galmiza/pdf-flatten/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/galmiza/pdf-flatten/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/galmiza/pdf-flatten/releases/tag/v1.0.0
