# Publishing on Google Play

A checklist for this app. Google's rules change: check the [Play Console Help](https://support.google.com/googleplay/android-developer) before each step.

## 1. Developer account

- Register at [play.google.com/console](https://play.google.com/console): one-time fee of $25 and identity verification (an organization also needs a D-U-N-S number).
- **New personal accounts** must run a **closed test with at least 12 testers, opted in for 14 days in a row**, before they can publish to production. Plan for it: invite friends through a Google Group or an email list.

## 2. Signing and the bundle

- Play requires an **Android App Bundle (AAB)** and **Play App Signing**: Google keeps the app signing key, you sign uploads with your own *upload key*.
- Create the upload key once and keep it, with its passwords, out of the repository:

  ```sh
  keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
  ```

- Build the bundle signed with it, either locally (properties in `~/.gradle/gradle.properties`, see the README) with `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`, or by pushing a `v*` tag once the `KEYSTORE_*` secrets are set: the release workflow attaches the `.aab` to the GitHub release.
- Bump `versionCode` for every upload.

## 3. App content (Play Console → Policy → App content)

| Declaration | Answer for this app |
| --- | --- |
| Privacy policy | **Required even without data collection.** Once the repository is public: https://github.com/galmiza/pdf-flatten/blob/main/PRIVACY.md |
| Data safety | No data collected, no data shared; no encryption-in-transit question since nothing is transmitted |
| Ads | No ads |
| App access | All functionality available without an account |
| Content rating | Fill in the IARC questionnaire: utility app, no user content shared → rated for everyone |
| Target audience | 18+ (or 13+); avoid "children", which brings the Families policy |
| Government / financial / health features | None |

The app requests **no permission**, so there are no sensitive-permission declarations.

## 4. Store listing

Ready in [`fastlane/metadata/android/`](../fastlane/metadata/android) for 7 languages: title, short and full descriptions, release notes, 512 × 512 icon, 1024 × 500 feature graphic and five 1080 × 1920 phone screenshots (Play rejects screenshots whose long side is more than twice the short side, so the raw 1080 × 2400 captures are framed with a caption). Field-by-field answers, including the questionnaires: [PLAY_LISTING.md](PLAY_LISTING.md).

## 5. Technical requirements already met

- **Target API level**: Play requires targeting an API level released within the last year; the app targets 37.
- **16 KB memory pages**: the app has no native code.
- **No restricted permissions, no background work, no network.**

## 6. Release

1. Upload the AAB to the **closed testing** track, add the testers, wait the 14 days (new personal accounts).
2. Apply for production access from the dashboard, then promote the release to **Production**.
3. Review usually takes from a few hours to a few days.

## Outside Google Play

Google is rolling out **developer verification for apps installed on certified Android devices**, including sideloaded APKs (GitHub releases, direct download). Developers verified through the Play Console are covered; for APK-only distribution, check the current status at [developer.android.com/developer-verification](https://developer.android.com/developer-verification).
