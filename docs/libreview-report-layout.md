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

The complete report uses the bundled static Liberation Sans regular and bold
fonts (Arial-compatible), including Latin Extended / Polish glyphs. Modern
pages no longer use Inter. Text is normalized to NFC before measuring,
wrapping, aligning or drawing. PDF text uses linear/subpixel positioning with
hinting disabled, avoiding whole-point glyph placement at small report sizes.

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

## Refinement from paired seven-day exports

The supplied eleven-page reference and Android export were rendered and compared
page by page. Further changes match measured typography (10.9/8.7 pt modern
patient header, 17.5 pt title, 8.7 pt plot labels and 1.64 pt clinical median),
two-level metric captions and compact unit suffixes. Shaded goal chips fit their
text. The time-in-range bar has a visible minimum height for each category,
including outlined zero categories; printed percentages remain authoritative.

Daily mini profiles share a single rounded container with internal day and
noon separators. The insight profile uses dotted outer percentiles and a
segmented day-part strip. Daily logs use 156.3 pt base day spacing, expanded for extra
carbohydrate rows and collision lanes for individual insulin entries, hourly guides, compact coloured low-value
cells and a legend. Ordinary high daily-log readings have coloured strokes;
the high fill begins at the very-high threshold. Weekly legacy traces retain
blue strokes and use a horizontal patient header; other legacy sheets have
rotated metadata. Monthly headings no longer overprint the period.
Snapshot plots gain a low threshold, clock guides and a coverage outline;
daily trends gain dense y labels, the target bracket and percentile keys.
The device card is narrow and rounded, with actual app/phone metadata.

No supplied patient identifiers, readings, proprietary clinical advice or
third-party branding are embedded. Data, aggregation, calibration and missing
scan/treatment policies are unchanged.

## Time-in-range table correction

The five stacked bands use 8.187 pt minimum height plus 0.977 pt per percent,
with 1.638 pt gaps. The 100% stack spans 164.086 to 309.273 pt. This reproduces
the measured reference stack at 0/16/84/0/0 without importing its data.
Leader lines meet category dividers at 186.46, 211.572, 261.788 and 286.90 pt.
Only the upper and lower category pairs have a vertical column divider;
the central range row is not bisected. Printed percentages and source
glucose calculations are unchanged.

## Injection timing

Weekly dose badges have a solid stem anchored to the journal entry's exact
local clock position, including minutes and seconds. Every valid insulin entry
is retained; nearby labels use separate lanes rather than being skipped.
Daily logs draw a pen marker at the same time on the glucose trace and place
individual dose badges along the insulin row. The insulin row no longer sums
all entries into an hourly cell. Hourly maximum/minimum and carbohydrate rows,
weekly totals and summary calculations retain their previous meaning.

A marker may attach to a CGM segment only when the timestamp is an existing
sample or lies between two samples within the same permitted plotting gap.
No CGM value is fabricated across missing data; in that case a baseline time
tick is shown. Markers identify recorded insulin without inferring its class,
meal relationship or a dosing recommendation.

## Verification limits

Policy tests cover calendar boundaries (including DST), seven-day pagination,
GMI units and averages on recorded days. Geometry fixtures were inspected using
the production frame coordinates and bundled fonts. These fixtures are not
Android-generated PDFs. Final device rendering and any remaining differences
in source data must be checked on an actual exported report.
