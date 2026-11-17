# Google Play: what to enter where

Everything the Play Console asks for, in the order it asks. Texts and images for the store listing are in [`fastlane/metadata/android/`](../fastlane/metadata/android), one folder per language.

## Create the app

| Field | Value |
| --- | --- |
| App name | PDF Flattener |
| Default language | English (United States) – en-US |
| App or game | App |
| Free or paid | Free (this cannot be changed to paid later) |
| Declarations | Accept the Developer Program Policies and US export laws |

## Store settings

| Field | Value |
| --- | --- |
| Category | Productivity |
| Tags | PDF, Documents, Productivity tools (pick from Google's list; up to 5) |
| Contact email | galmiza@gmail.com |
| Website | https://github.com/galmiza/pdf-flatten |
| Phone | leave empty |
| External marketing | Allowed (default) |

## Main store listing (per language)

| Field | Limit | File |
| --- | --- | --- |
| App name | 30 | `title.txt` |
| Short description | 80 | `short_description.txt` |
| Full description | 4,000 | `full_description.txt` |
| App icon | 512 × 512 PNG | `en-US/images/icon.png` |
| Feature graphic | 1024 × 500 | `images/featureGraphic.jpg` |
| Phone screenshots | 2–8 | `images/phoneScreenshots/1.jpg` … `5.jpg` (1080 × 1920) |
| Release notes | 500 | `changelogs/<versionCode>.txt` |

Languages ready: en-US (default), fr-FR, es-ES, de-DE, it-IT, pt-BR, nl-NL. In the console, *Store presence → Main store listing → Manage translations → Add your own translations*, then fill each language from its folder. Tablet screenshots are optional and not provided.

With [fastlane](https://docs.fastlane.tools/actions/upload_to_play_store/) installed and a Play API key, `fastlane supply --skip_upload_apk --skip_upload_aab` uploads all of it at once.

## App content (Policy → App content)

| Section | Answer |
| --- | --- |
| Privacy policy | https://github.com/galmiza/pdf-flatten/blob/main/PRIVACY.md (public once the repository is pushed and public) |
| Ads | No, my app does not contain ads |
| App access | All functionality is available without any access restrictions |
| Content rating | See below → expected result: **Everyone / PEGI 3 / USK 0** |
| Target audience | **13–15, 16–17 and 18 and over** (see below) |
| News app | No |
| Data safety | See below → **No data collected, no data shared** |
| Government app | No |
| Financial features | My app doesn't provide any financial features |
| Health apps | My app does not have any health features |
| Advertising ID | No, the app does not use an advertising ID |

### Content rating questionnaire (IARC)

- Email: galmiza@gmail.com
- Category: **All other app types** (utility, productivity, communication, or other)
- Violence, fear, sexuality, language, controlled substances, crude humor: **No** to all
- Does the app allow users to interact or exchange content with other users? **No** (sharing goes through Android's share sheet to other apps; the app itself has no communication feature)
- Does the app share the user's current physical location with other users? **No**
- Does the app allow users to purchase digital goods? **No**
- Gambling, simulated gambling: **No**
- Is it a web browser or search engine? **No**

### Target audience and content

- Age groups: **13–15, 16–17, 18 and over**. Do not select an age group under 13: that would bring the app under the Families policy (extra review and requirements) for no benefit.
- Could the store listing unintentionally appeal to children? **No**

### Data safety

- Does your app collect or share any of the required user data types? **No**
- (Encryption in transit and deletion questions then do not apply.)

Why it is true: the app has no Internet permission, no analytics or crash-reporting SDK, and only stores its settings on the device. Documents are processed locally and only leave through the save dialog or the share sheet, at the user's request.

## Release

| Step | Where |
| --- | --- |
| Upload key and AAB | see [PUBLISHING.md](PUBLISHING.md#2-signing-and-the-bundle) |
| Closed test, 12 testers for 14 days (new personal accounts) | Testing → Closed testing |
| Countries | Production → Countries / regions: all |
| Release name | the `versionName`, e.g. 1.2.0 |
| Release notes | `changelogs/<versionCode>.txt`, per language |
