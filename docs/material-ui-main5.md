# Material UI consistency on main5

## Reference

Use the Advanced section in phone alarm settings as the visual reference, rather
than introducing a new theme. The reference uses `StyledSwitch`, `AppSlider`,
and `AlertOptionButtonGroup` (backed by `ConnectedButtonGroup`).

## Shared rules

| Control | Rule |
| --- | --- |
| Selected ordinary option | `primary` background with `onPrimary` content |
| Unselected ordinary option | Transparent background, `outline` border, `onSurface` content |
| Choice shape | Shared pill shape in selected, unselected and pressed states |
| Compact chips | `SettingsChoiceChip` shares the shape, palette and border; optional selection icons remain at call sites |
| Switch | `StyledSwitch`, including its checked icon and disabled colors |
| Setting row | Neutral grouped card; 56 dp minimum, 16 dp horizontal and 8 dp vertical content padding; wrapping content may increase height |
| Master setting | The same setting row and switch, rather than a second checked-card palette |
| Slider | `AppSlider`, retaining caller-supplied range, steps and persistence behavior |
| Group heading | `labelMedium`, with 8 dp to the choice control, as in Advanced alarms |

`SettingsChoice`, alarms, widgets, palette selection, insulin pen selection,
calibration weighting, Clone/Mirror/Nightscout options, history export,
glucose-meter options and statistics range controls use these shared rules.
Small option lists retain connected groups; wrapping lists retain compact chips.
Radio buttons and checkboxes retain their single/multiple-selection semantics.
Clinical journal-type colors and warning/destructive colors are semantic and
are retained. Phone styling is not imposed on Wear OS controls.

## Dependency update

- Compose BOM alpha: `2026.10.00` to `2026.10.01`.
- Wear Compose Material3, Foundation and Navigation: `1.6.2` to `1.7.1`.
- No Kotlin, AGP, sensor algorithm, NFC/BLE, alert configuration or stored
  preference migration is introduced by the appearance change.

## Validation

Build the phone ARM64 and Wear ARM32/ARM64 release variants using the existing
GitHub Actions workflow. It verifies signatures and pinned sensor libraries
before publishing a release. A successful compile does not verify appearance
on a device. Review the same settings in light/dark themes and large system
font sizes, including selected, unselected and disabled states.
