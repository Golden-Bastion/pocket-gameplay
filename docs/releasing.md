# Android releases

Pocket Gameplay publishes installable Android APKs as GitHub Releases. Normal pull-request and main-branch builds continue to publish short-lived debug APK workflow artifacts; they do not create releases.

## Versioning and tags

The Android package version is stored in `gradle.properties`:

- `appVersionName` is the public `MAJOR.MINOR.PATCH` version.
- `appVersionCode` is the Android integer version code used by normal debug builds.

Before publishing a version, update both `appVersionName` and `appVersionCode` on `main`, commit those changes, and create a tag that matches them. Set `appVersionCode` to the value calculated by the formula below. Release tags must use `vMAJOR.MINOR.PATCH`, for example `v1.0.0`. The tag must point to a commit contained in `main`, and the version in the tagged `gradle.properties` must match the tag.

Release version codes are calculated from the tag as:

```text
MAJOR * 1,000,000 + MINOR * 1,000 + PATCH
```

This keeps version codes increasing when MINOR and PATCH are each between 0 and 999. The resulting code must be within Android's supported positive version-code range. The release workflow checks that both Gradle properties match the tag before signing the APK.

To publish automatically after preparing the version on `main`:

```sh
git tag v1.0.0
git push origin v1.0.0
```

The Android CI/CD workflow tests the tagged source, builds a signed release APK, and publishes it with generated release notes and a SHA-256 checksum.

## Manual workflow dispatch

Open **Actions → Android CI/CD → Run workflow**.

Choose:

- **build** to test and build a selected branch/ref and upload a short-lived debug APK artifact.
- **release** to rebuild and publish an existing release tag. Supply the exact tag, such as `v1.0.0`.

Start manual releases from the `main` branch workflow. They use the same tag, main-branch ancestry, version, and signing checks as tag-push releases. They publish a GitHub Release for the selected existing tag; they do not create a new version tag.

## One-time release signing setup

Release APKs must always use the same private signing key. This is what allows Android to install a later release as an update to an earlier release. Do not use an ephemeral CI-generated key, the per-runner Android debug key, or an unsigned APK for official releases.

Generate a dedicated keystore locally and keep a secure offline backup:

```sh
keytool -genkeypair -v \
  -keystore pocket-gameplay-release.jks \
  -alias pocket-gameplay \
  -keyalg RSA -keysize 2048 -validity 10000
```

Never commit the keystore to the repository. Add the following repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded contents of the keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Alias selected when creating the key |
| `ANDROID_KEY_PASSWORD` | Password for that key |

On Linux or macOS, create the base64 value with:

```sh
base64 < pocket-gameplay-release.jks | tr -d '\n'
```

On PowerShell, use:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("pocket-gameplay-release.jks"))
```

Copy the resulting base64 text directly into the GitHub secret. Do not paste the keystore or its passwords into issues, pull requests, workflow logs, or source files. The workflow checks for all four secrets and fails before building if any are missing.

**Keep the keystore and passwords permanently backed up.** Losing the signing key prevents future APKs from updating an already-installed official release. If the app has only been installed from CI debug artifacts, the first official release may require a one-time uninstall and reinstall because debug and release APKs use different signing identities; uninstalling removes Pocket Gameplay's private library and saves.

## Release assets

Each GitHub Release contains:

- `app-release.apk` — signed installable Android package.
- `app-release.apk.sha256` — SHA-256 checksum for verifying the APK download.

The normal debug APK artifact remains available for development and device testing, but it is not an official release or an update path for release-signed installations.
