# Android language resources

The translation database is Android's native resource dictionary. App text lives
in `src/main/res/values*/strings.xml`; the main app and bundled helper share only
the helper's translated label in `localization/res/values*/strings.xml`.
Resources remain XML data compiled by Android; there is no runtime database,
locale switch statement, or custom resource loader.

`values/` is the complete English fallback. `resources.properties` declares
`unqualifiedResLocale=en`. Gradle's `androidResources.generateLocaleConfig=true`
generates the Android 13+ app-language list from resource directories. Do not
add a manually maintained `locale_config.xml`, hardcoded supported-language list,
or activity locale overrides. Android 9–12 follow system languages normally.

`values-b+zh+Hans` contains Simplified Chinese. `values-b+zh+Hant` contains complete
Traditional Chinese, using Taiwan terminology. Hong Kong and Macau override
regional terms in `values-b+zh+Hant+HK` and `values-b+zh+Hant+MO`; Taiwan is listed
explicitly through `values-b+zh+Hant+TW`. Android resolves `zh-HK`, `zh-MO`, and
`zh-TW` to the corresponding script and region even when the input tag omits
`Hant`. `values-ja` contains Japanese.

## Add a language

For French, add `src/main/res/values-fr/strings.xml` with translations for the
English resource keys, and add the helper label entry to
`localization/res/values-fr/strings.xml`. For a script or region use Android BCP 47
qualifiers, such as `values-b+sr+Latn`. Regional files may contain only overrides;
new base languages should translate every app key. No Java, manifest, or Gradle
changes are needed. Rebuild the APK to distribute the new resources.

Preserve numbered placeholders, types and percent escapes: `%1$d`, `%1$s`,
`%1$.2f`, and `%%`. Keep literal commands, package IDs, service components,
authorities and permissions unchanged. Translate short labels separately from
long help text; the volume overlay remains 260 dp wide with three stable rows.
Configured step counts are always 5–150.

Root and Shizuku setup processes encode report rows using resource IDs from
their own APK. `SetupReport` renders those rows in the main app's current locale.
Do not return fixed-language setup text from those processes. Raw exception and
device diagnostics remain technical data. Notification channel name, description
and current notification are refreshed on configuration changes.

## Verify

Run the release build, unit suites and lint in `commands.md`. Translation changes
must preserve all default resource keys and format arguments.

On a disposable emulator, install the newly built main, helper and test host,
then run:

```sh
adb -s EMULATOR_SERIAL shell am instrument -w \
  roro.stellar.volumesteps.testhost/.LocalizationInstrumentation
```

Require every returned `pass` and `INSTRUMENTATION_CODE: 0`. This fixture checks
Android's generated language list, script/region inference, US/UK English,
Japanese, Chinese regions, an unsupported language's English fallback, formatted
resources, localized setup reports and actual panel measurement/readback. It
does not alter device locales, app settings or volume, attach an overlay, inject
keys, or start a privileged backend.

For a real app language change on Android 13+, snapshot the current selection:

```sh
adb -s EMULATOR_SERIAL shell cmd locale get-app-locales roro.stellar.volumesteps
adb -s EMULATOR_SERIAL shell cmd locale set-app-locales \
  roro.stellar.volumesteps --locales ja
```

Open the app and check the home page, permission guide, notification and overlay.
Restore the original app locale afterward; an empty `--locales ''` restores
following the system. Existing emulator UI fixtures that search Chinese text
need a Simplified Chinese system or app locale. `pm clear` resets the app locale,
so use a Simplified Chinese system locale for those destructive fixtures.

Physical-button, screen-off and reboot tests remain distinct from translation
tests. Passing this fixture does not establish additional vendor support.

## Verified build, 2026-11-03

Main 1.5.1 language test APK:
`d9041271a603264ea10ac17a2e01c8544111537398dc57b7353c1cac4e3384a7`.
Bundled helper 1.4:
`765d772bc1472e7ad0a8d4b4a790d0299ee8c284aa7657d76ea9da0383b3d24b`.
Both use the same existing development certificate as the previous 1.5.1 APK.

- Release compilation and lint completed. Main 56 unit tests and helper 3 unit
  tests passed. Lint retained private-API and unused-resource warnings; no
  translation or format warnings remained.
- The localization fixture passed all 138 checks on an AOSP ARM64 emulator.
- Six actual system app-language selections passed 18 UI/notification checks:
  English, Japanese, Hong Kong, Macau, Taiwan and Simplified Chinese. Notifications
  updated while the app was in the background, before reopening the home page.
  Language restoration and unchanged native volume added two passing checks.
- The first live notification assertion failed because the emulator had
  notifications disabled and the assertion expected a screen-off warning even
  though AOSP's normal assistant path was available. The corrected fixture
  temporarily allowed notifications and checked the actual running title.
  Original failure evidence was retained separately from the successful rerun.
- The original app locale, disabled correction state and notification permission
  were restored. No personal-phone installation or physical-key test was run.
  No additional vendor, screen-off or reboot behavior is established by this APK.
