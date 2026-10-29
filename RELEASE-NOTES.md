# Sumi 0.1.3-preview — repair Preview

This Preview adds a mandatory non-debuggable distribution gate and signing-continuity verification. Keyboard test setup now checks the soft-keyboard setting and restores it after synthetic tests. Existing offline keyboard behavior and signing identity are retained.

Validation: 60 fresh host tests passed; release lint reported no errors and 236 warnings. Exact signed, non-debuggable APK updated from code 3 on an isolated API 37 emulator. Four focused tests passed: password literal input, no-learning input, no typed history/dictionary files, autocorrect/backspace undo. Keyboard settings were restored and verified. No microphone recording was performed.

Personal Preview; not production, Google Play, or sale-readiness qualification. No physical phone installation or foreground interaction occurred. Real Obtainium phone upgrades, long-duration behavior, live assistive technology and power/thermal behavior remain unqualified for these artifacts. Sumi and Rouse retain their existing debug-origin preview certificate for update continuity; the APKs are non-debuggable, but this does not qualify production signing.

Use the existing Obtainium repository: https://github.com/FourWags/sumi. Stable APK asset: `sumi.apk`. Required corresponding source and notices are included in `sumi-source.zip`. See CHECKS.txt and SHA256SUMS for exact identities.
