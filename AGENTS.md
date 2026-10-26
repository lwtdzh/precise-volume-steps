# Working on Precise Volume Steps

Read these repository skills before changing behavior:

- [Device compatibility](.trae/skills/precise-volume-compatibility/SKILL.md) —
  implementation history, Vivo package-prefix evidence, helper installation,
  process identity, screen-off limitations, and other-vendor investigations.
- [Regression testing](.trae/skills/precise-volume-regression/SKILL.md) —
  build commands, automated checks, physical-phone tests, installer handling,
  restoration, and all user feedback cases.

This directory is a standalone Git repository and Gradle project. Run commands
from here. Do not publish the enclosing privilege-assistant repository or alter
its unrelated modules. Root app sources are in `src/`; the helper is in
`compatibility-helper/`; optional test host sources are in `tests/host/`.

Keep changes small. Preserve the existing Shizuku path when investigating normal
mode. Never hardcode a phone's 150-unit range or the user's 50-step setting into
the core controller. Preserve runtime package IDs, service components, signature
permission and provider authority when changing branding.

Keep the human README focused on installing and using the app. Put engineering
history and procedures in the skills and their references. Use real Android
readback for the third panel row. Keep one bar, stable row positions, and no
intermediate coarse-volume frames during pending corrections.

A successful callback or matching target is not evidence of physical-button
delivery. Report synthetic, external HID and physical-button tests separately.
The earlier physical Vivo screen-off and reboot requirements remain unverified.
Do not claim they were solved by a package-name change.

For behavior changes, run the automated suites and the physical-device matrix
before claiming a vendor is supported. If no physical phone is available,
complete independent work and report that device verification is outstanding.
Retain original failed runs and successful reruns. Restore user settings and
other accessibility services after testing.

Commit messages use `type: description`. Publish only explicitly authorized
changes. Keep SSH keys, signing keys, device captures and temporary scripts out
of Git. The distributed development APKs use the existing local development
certificate; a different machine's default debug key cannot upgrade those APKs.
