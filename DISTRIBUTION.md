# Publishing FreeScale

Package: `com.anant.freescale`  
Display name: **FreeScale**

## 1. Create a local/dev keystore (machine testing)

Use a **separate** keystore for everyday `assemble*Release` / sideloading if you
want to keep the Play/CI release key offline. Do **not** commit any `.jks` or
`keystore.properties`.

```bash
keytool -genkeypair -v \
  -keystore freescale-local.jks \
  -alias freescale-local \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype JKS
```

```bash
cp keystore.properties.example keystore.properties
# Point storeFile at freescale-local.jks (or freescale-release.jks) and fill passwords
```

## 2. Play/CI release keystore

Generate once, store in a password manager + GitHub Actions secrets — not in git:

```bash
keytool -genkeypair -v \
  -keystore freescale-release.jks \
  -alias freescale \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype JKS
```

| Secret | Value |
|--------|-------|
| `RELEASE_KEYSTORE_BASE64` | `base64 -i freescale-release.jks \| pbcopy` |
| `RELEASE_STORE_PASSWORD` | Release store password |
| `RELEASE_KEY_ALIAS` | `freescale` |
| `RELEASE_KEY_PASSWORD` | Release key password |

```bash
# When the FreeScale GitHub remote exists:
gh secret set RELEASE_KEYSTORE_BASE64 < <(base64 -i freescale-release.jks)
gh secret set RELEASE_STORE_PASSWORD
gh secret set RELEASE_KEY_ALIAS -b freescale
gh secret set RELEASE_KEY_PASSWORD
```

CI writes a temporary `keystore.properties` on the runner from these secrets.
Local `keystore.properties` is never used by CI.

After rotating the release keystore, uninstall older installs once and install the
first APK signed with the new key.

## 3. Product flavors

| Flavor | Purpose |
|--------|---------|
| `github` | GitHub Releases / sideload / Play-style CI builds (`IS_FDROID=false`) |
| `fdroid` | F-Droid FOSS builds (`IS_FDROID=true`); fixed `versionCode`/`versionName` in Gradle |

Debug builds append `.debug` to the application id so they sit beside release installs.

## 4. Build a release APK / AAB

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# GitHub / sideload APK — signed with local keystore.properties if present
./gradlew :app:assembleGithubRelease

# Play Store bundle
./gradlew :app:bundleGithubRelease

# F-Droid flavor
./gradlew :app:assembleFdroidRelease
```

Outputs:
- APK: `app/build/outputs/apk/github/release/FreeScale-<versionName>.apk`
- AAB: `app/build/outputs/bundle/githubRelease/app-github-release.aab`

## 5. Version bumps

- **GitHub channel:** CI sets `-PappVersionCode` / `-PappVersionName` from `GITHUB_RUN_NUMBER`.
- **F-Droid channel:** bump the `fdroid` flavor block in `app/build.gradle.kts` (or run the
  F-Droid Release workflow, which does it for you).

## 6. GitHub “Latest” vs F-Droid

CI releases on `main` (`v*-buildN`, with `FreeScale-latest.apk`) own GitHub **Latest**.
The F-Droid workflow publishes `v*-fdroid` tags as **prerelease** with `make_latest: false`.
