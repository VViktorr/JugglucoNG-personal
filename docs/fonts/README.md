# Optional application fonts — source and licensing manifest

This manifest tracks the requested fonts for the JugglucoNG main5 global font selector. It does not itself grant a license, bundle font binaries, or enable a font in the UI.

**Do not add a font binary until its exact upstream release, copyright notice and applicable license have been checked and retained.** Public ROM source code or a font overlay is not proof that the font binary is freely redistributable.

| Family | Preferred authoritative source | Licensing / packaging notes |
| --- | --- | --- |
| Comfortaa | https://github.com/google/fonts/tree/main/ofl/comfortaa | SIL Open Font License 1.1; retain OFL.txt and copyright notices. |
| Ubuntu | https://github.com/google/fonts/tree/main/ufl/ubuntu | Ubuntu Font Licence; retain the licence and notices. |
| Roboto Condensed | https://github.com/google/fonts/tree/main/ofl/robotocondensed | SIL OFL 1.1 for this release; retain OFL.txt. |
| Oswald | https://github.com/google/fonts/tree/main/ofl/oswald | SIL OFL 1.1; retain OFL.txt. |
| Rubik | https://github.com/google/fonts/tree/main/ofl/rubik | SIL OFL 1.1; retain OFL.txt. |
| Poppins | https://github.com/google/fonts/tree/main/ofl/poppins | SIL OFL 1.1; retain OFL.txt. |
| Plus Jakarta Sans | https://github.com/google/fonts/tree/main/ofl/plusjakartasans | SIL OFL 1.1; retain OFL.txt. |
| Noto Sans | https://github.com/google/fonts/tree/main/ofl/notosans | SIL OFL 1.1; retain OFL.txt. |
| Urbanist | https://github.com/google/fonts/tree/main/ofl/urbanist | SIL OFL 1.1; retain OFL.txt. |
| Inter | https://github.com/google/fonts/tree/main/ofl/inter | SIL OFL 1.1; retain OFL.txt. Existing report-specific Inter resources do not automatically cover app UI usage. |
| DM Sans | https://github.com/google/fonts/tree/main/ofl/dmsans | SIL OFL 1.1; retain OFL.txt. |
| Roboto Flex | https://github.com/google/fonts/tree/main/ofl/robotoflex | SIL OFL 1.1; retain OFL.txt. |
| HarmonyOS Sans | Huawei official font licensing terms; verify the exact distribution | Separate Huawei terms, **not OFL**. Verify that the specific version permits embedding and distribution with this Android app; preserve full applicable license and notices before bundling. |

Existing choices: system font and IBM Plex Sans Variable. Preserve the current default until a user opts in.

Not included: Samsung One and MiSans (redistribution permissions not established for the intended APK).

## Integration checklist (not yet implemented)

- Bundle verified TTF/OTF binaries and their **unmodified full** license texts in the repository, and expose third-party notices in the app.
- Add one persisted app-wide font preference with fallback to current system/IBM Plex typography.
- Apply it to Compose Material 3 typography, legacy Android Views, text drawn to Canvas/bitmaps, widgets, floating overlays and the Wear OS UI, using the same preference where synchronized.
- Respect Android-owned notification text and MetricStyle restrictions; do not claim all system surfaces can display bundled fonts.
- Verify Polish glyphs, numeral legibility, fallback behavior, app size and font weight/width support.
- Do not alter glucose readings, glucose thresholds, trend arrows, or BLE/sensor code.

This file is a source/licensing checklist, **not** the full legal texts. Each font's actual upstream license must accompany its binaries.
