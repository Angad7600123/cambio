# Releasing Cambio

An Android APK has to be signed before it will install, and every later update
has to be signed with the **same** key or Android refuses it. So the signing key
is the one piece of this project that cannot be recreated: lose it and existing
installs can never be updated again, only uninstalled and replaced.

For that reason the key lives with you and never enters this repository. Nothing
here reads a password from a file that is tracked, and `.gitignore` already
excludes `*.jks`, `*.keystore` and `local.properties`.

## One-off: create the signing key

Run this yourself. It prompts for a password — choose it, keep it, and do not
paste it into a chat, an issue or a commit.

```bash
keytool -genkeypair -v -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10000 -alias cambio -keystore cambio-release.jks
```

Keep the resulting `cambio-release.jks` **outside the repository** — a folder
like `~/keys/` — and back it up somewhere you will still have in five years.
`validity 10000` is about 27 years; Play requires the key to outlast the app.

## One-off: point the build at it

Add four lines to `local.properties` (untracked, in the repository root):

```properties
cambio.keystore.file=C:/Users/you/keys/cambio-release.jks
cambio.keystore.password=<the store password>
cambio.key.alias=cambio
cambio.key.password=<the key password>
```

Forward slashes work on Windows. If the alias and store share a password, both
lines still need to be present.

With none of these set, `./gradlew assembleRelease` still succeeds — it just
produces an unsigned APK. That is deliberate: a clone with no keystore has to
build, and a *missing* signature is far better than a silently wrong one.

## One-off: the same key, for CI

`.github/workflows/release.yml` builds the release when a `v*` tag is pushed. It
needs the same four values as repository secrets, under
**Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the `.jks` file, base64-encoded |
| `KEYSTORE_PASSWORD` | the store password |
| `KEY_ALIAS` | `cambio` |
| `KEY_PASSWORD` | the key password |

To produce the base64 on Windows (PowerShell):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\keys\cambio-release.jks")) | Set-Content keystore.b64
```

Paste the contents of `keystore.b64` into the secret, then delete the file. The
workflow decodes it to the runner's temp directory, outside the workspace, and
shreds it afterwards whether the build passed or failed.

## Cutting a release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`. `versionCode`
   must increase on every release; Android will not install a lower one over a
   higher one.
2. Commit that on `main` and let CI pass.
3. Tag and push:

   ```bash
   git tag -a v0.1.0 -m "Cambio v0.1.0"
   git push origin v0.1.0
   ```

4. The workflow runs the same checks `main` gets, builds the signed APK,
   verifies the signature, and opens a **draft** release with the APK attached.
   A tag pushed by accident therefore never becomes a public download.
5. Read the generated notes, edit them, and publish.

## Checking an APK before you trust it

```bash
"$ANDROID_HOME/build-tools/<version>/apksigner" verify --print-certs app-release.apk
```

The certificate digest it prints must match the one from the release before it.
If it does not, the APK was signed with a different key and no one who has the
old version can install it.
