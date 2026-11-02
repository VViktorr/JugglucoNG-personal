# Native startup crash: privacy state initialization

The supplied current arm64 library (teraz.so) has Build ID
`77a43fba511655bc16cfb75930960dcd83762687`, identical to the provided crash log.
It includes symbols and debug information. The older working library has a
different Build ID (`0adface5a6ce192e8690af0758f4649ddd2aa2a3`). Both contain
code and debug sections; their different file sizes do not indicate a missing
native build.

Symbolication of the three application frames in the matching current library:

| PC | Location |
| --- | --- |
| 0x22b66c | privacy_log_message, logs.cpp:48 in that build; inlined std::string::find reading an identity-map entry |
| 0x22c72c | logwritev / LOGGERNO |
| 0x1ef6d0 | _GLOBAL__sub_I_percentile.cpp; persimages -> PersImages -> CircularArray constructor -> LOGAR |

The loader was executing native global constructors. The percentile translation
unit logged before logs.cpp's translation-unit global std::map was constructed.
Iterating that zero-initialized, unconstructed map led to a read at address 0x20.
This is an initialization-order defect in the added privacy logger, not a
MetricStyle/Live Update or Java UI failure. This conclusion applies to the
matched library and trace; it does not establish the contents of other APKs
reported solely through earlier BUILD-INFO files.

Fix: replace the namespace-scope mutex/map with a shared PrivacyState created
on first use through a function-local static pointer. C++ guarantees guarded
initialization. Keep the state alive for process lifetime so late global
logging destructors cannot encounter an already destroyed identity map.
Both registration and redaction obtain the same initialized state. Redaction
rules are retained. No sensor, parser, stored-data, ABI or CMake changes.

The existing focused privacy regression harness now invokes both redaction and
alias registration in a separate translation unit's global constructor, linked
before logs.cpp, to cover the previously untested pre-main path. The harness
was updated but not executed. No compilation, tests or phone validation were
performed for this fix, following the user's standing instruction.
