# Libreviev style PDF

The style uses Letter pages (612 x 792 points). The AGP/insights pages use
rounded clinical cards; the remaining groups use the compact report-specific
layouts. Coordinates are in `LibreViewReportLayout`. The application remains
identified as JugglucoNG; no third-party logo or patient data is bundled.

## Period and data policy

- A clinical report includes today and the previous N-1 calendar dates, starting
  at local midnight and ending at export time. DST is resolved with ZoneId.
- Clinical AGP bands are 54/70/180/250 mg/dL. This policy is confined to this
  PDF style and does not change application targets or alarm settings. The device
  page reports the original app settings. Other PDF styles keep their settings.
- The monthly calendar may include earlier recorded days of the same month.
  This context is kept separate from the selected-period summary and daily pages.
- GMI uses the existing mean-based calculation. Its SI display follows
  Bergenstal et al., Diabetes Care 2018, doi:10.2337/dc18-1581.
- Recorded daily insulin/carbohydrate means divide by days with valid entries,
  not by days on which no treatment was recorded. The report labels this rule
  and counts missing days; this does not establish that a recorded day is complete.
- Scan/view events and proprietary LibreView interpretation are not available.
  Missing values remain blank/dashes. Actual CGM readings and app calibration
  remain the source of the glucose curves and calculations.

## Fonts

Inter is used for the modern cards; Liberation Sans provides Arial-compatible
metrics for the legacy layouts. Fonts are bundled, with system fallbacks, so an
OEM's system font does not determine report typography.

- Inter: `google/fonts`, `ofl/inter/Inter[opsz,wght].ttf`, git blob
  `047c92f6e2212473dc436020afed689527076d44`. Static instances at optical size
  14 and weights 400/700 were generated with fontTools.varLib.instancer.
  Source: https://github.com/google/fonts/tree/main/ofl/inter
- Liberation Sans: unmodified regular/bold files from Liberation Fonts 2.1.5.
  Source: https://github.com/liberationfonts/liberation-fonts/releases/tag/2.1.5
- Both SIL OFL license texts accompany the fonts in the APK under
  `assets/report_fonts/`.

## Chart rendering

Modern AGP and insight medians are green within the target band, orange above
and red below. The same smoothed path is clipped at the two target lines,
including isolated median points; no data interpolation is changed. The median
is drawn after the grid so its stroke remains visible. Legacy blue percentile
profiles retain their original palette.

Percentile shading retains the two nested smooth bands and threshold colours.
Rounded daily tiles and the time-in-range bar clip their contents to their
rounded outlines. Period headers, goal chips and target badges use rounded
fills as well as rounded borders.

## Verification limits

Policy tests cover calendar boundaries (including DST), seven-day pagination,
GMI units and averages on recorded days. Geometry fixtures were inspected using
the production frame coordinates and bundled fonts. These fixtures are not
Android-generated PDFs. Final device rendering and any remaining differences
in source data must be checked on an actual exported report.
