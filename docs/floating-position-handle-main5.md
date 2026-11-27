# Floating glucose positioning handle

Reset position recentres the freeform overlay and shows a separate, labelled drag handle (Grab and drag / Złap i przeciągnij) approximately 126 dp below the reading. Near the bottom edge it moves above the reading. Its window is separate so the gap does not intercept touches. Movement uses raw screen coordinates with fractional remainders; the reading retains its original window dimensions and screen-relative placement rules. Release or cancellation persists the position; the handle disappears after 1.5 seconds (30 seconds if untouched). Reset can show it again, including when already centred. Existing direct dragging remains available.

The transient reset event is shared between repository instances without adding persistent settings. Dynamic Island suppresses the handle. Service destruction removes the extra window. Label size follows the font scale and its window width accommodates the measured text.

Reviewed code paths for reset, movement, position persistence, timeout, configuration changes and cleanup. No compilation or tests were run, per user instruction. Device behaviour remains to be verified.
