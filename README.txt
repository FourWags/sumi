SUMI — 0.1.6-preview

Sumi is an offline English-US Android keyboard (IME), for Android 10 and newer.
Application ID: dev.fourwags.sumi.inputmethod.latin
This independent installation has fresh settings. Installing it does not remove,
read or migrate another keyboard's settings, dictionaries or imported media.

Features
- Native spelling correction, conservative autocorrect and Backspace undo.
- Regular and enlarged QWERTY, optional number row, editing and clipboard panels.
- Adjustable letter previews, protected-field restrictions, matching themes/icons,
  and three selectable Shiba tools-button designs.
- Scalable suggestion-bar text and tap areas, separate from keyboard height.
- Selectable dictation silence pause: 2 seconds (default), 4 seconds or 8 seconds.
- On-device English voice typing where Android and installed offline support allow
  it; user-started microphone permission only, no cloud fallback or downloads.
- Private on-phone GIF and PNG/JPEG/WebP sticker import via the local document
  picker. Shiba tools > My media > Import & manage, or Settings > Personal media.
  Source files remain untouched. Imported copies are credential-protected and
  excluded from backup/device transfer, source packages and publication.
  These insert as images/stickers in compatible editors, not new Unicode emoji.

Privacy
No INTERNET permission, analytics, advertising or typed-text persistence.
Personal media is runtime-only: never put imports, credentials, device snapshots
or raw diagnostic logs into source or releases. Keep originals: uninstall/data
clear removes the keyboard's imported copies. Password/private/no-learning and
other protected fields retain their restrictions. Personal dictionary use remains
under the user's control. No grammar rewriting.

Building
Use bash build.sh from this canonical project folder. Source ./env.sh for local tools.
A source export also supports caller-supplied JAVA_HOME and ANDROID_HOME; the
private local toolchain is not included in source ZIPs.
Project-local JDK21, Android SDK37/NDK30 and CMake4.1.2 support paths containing
spaces without compatibility links. NativeSources.cmake uses the exact upstream
production source list; verify with python3 tools/check_native_sources.py.
Run python3 tools/check_public_media.py before packaging any public source.
Gradle source is app/, tools/, dictionaries/, gradle/ and root build files.
Exclude build caches, local.properties, signing material and local project records.

Verification
Read the exact candidate's CHECKS.txt and SHA256SUMS. Host or emulator results do
not establish physical-device, live accessibility, battery or production quality.
ARM32 runtime qualification is excluded. The preview retains the established local
preview signing certificate and is debuggable; it is not store/production signing.
Do not grant microphone permission or record audio during automated tests.

Credits
This keyboard retains OpenBoard/AOSP-derived native keyboard code and all upstream
copyright/license notices. See LICENSE, LICENSE-Apache-2.0.txt and
THIRD_PARTY_NOTICES.txt. Sumi identity, theme integration and Shiba artwork are
project-specific additions. Original personal photographs are never release assets.

Audit repair notes (29 October 2026)
The four focused protected-input/autocorrection tests pass on the disposable API37
emulator when the documented runner preflight sets show_ime_with_hard_keyboard=1.
Virtual hardware can report a hardware keyboard even for a phone AVD. Use
run_device_tests.py, which captures and restores this setting, instead of invoking
the instrumentation directly. A missing keyboard view is not a typing assertion.
Physical app updates must use Obtainium. The device-test runner only verifies
already-installed matching packages on phones; the legacy replacement/rollback
helper now refuses physical devices before any mutation.
Production packaging must use tools/package_release.sh with the existing approved
signing configuration. A non-debuggable artifact and matching signer checks do not
by themselves establish store, physical-device or signing-key security qualification.
