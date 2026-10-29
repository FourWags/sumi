# Sumi source checkout

This repository contains text source, tests, configuration and license notices. APKs, compiled outputs, binary assets, credentials, personal content, private diagnostics and unrelated projects are excluded. It records the tested audit repair; it is not a new app release.

## Restore build assets

The exact fonts, images, dictionaries, media fixtures and Gradle wrapper needed by this source are listed in `source-build-assets.json`. They remain outside Git. The matching source archive is the `sumi-source.zip` asset of the existing 0.1.2-preview release. Do not replace that release asset with newer source.

Supply that archive, then run from the source checkout:

```sh
python3 tools/restore_build_assets.py --archive /path/to/sumi-source.zip
```

The helper checks the whole archive SHA-256, checks every asset, refuses symlinks and preserves any differing existing file. It makes no network request. Interrupted restoration can be retried. The manifest records 1065 assets and the source archive hash `bb829d0533dc28a28ac2c1ef0ecfc07d2ea4b711982ff866d475686b39b022a3`.

Configure your Android SDK/JDK locally using the existing project build instructions. Keep `local.properties` and signing material outside Git. Existing authoritative working folders already have these assets; the helper verifies and leaves matching files unchanged.

## Source and licensing boundaries

Existing source licenses, third-party notices and font licenses are preserved. No new license grant is implied. GitHub workflow files are excluded so importing source cannot trigger an APK release. The source branch does not change an existing release index, release tag or APK.

The audit validation applies to the recorded application source and restored asset hashes. Repository setup files and machine-neutral documentation were separately inspected; there is no new physical-device or production qualification.
