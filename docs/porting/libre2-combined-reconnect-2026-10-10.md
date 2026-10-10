# Libre 2 combined reconnect policy

Implemented on `libre2-hybridOK`, based on the Libre 2 lifecycle in
`ctqvva/JugglucoNG` commit `3d5f6b1a75833fd79780fe9d772934e2f553b7a3`
and the local Hybrid recovery in parent `6105b93315f6d37b2a6f5c0520121be6437dee48`.

## Connection lifecycle

- Both reconnect entry points use the same serialized Libre 2 lifecycle, including
  the delayed overload used by the local multi-sensor reconnect sweep.
- A reconnect retires the old GATT and clears protocol state before creating a
  replacement. `autoConnect` continues to use the existing application setting.
- An outstanding disconnect has one 2-second deadline; repeated requests do not
  extend that deadline. Missing callbacks and protocol errors use the same path.
- Pending connection requests are coalesced. A generation check rejects canceled
  tasks even when the executor already dispatched them.
- Connection callbacks require the current GATT. Services, descriptor writes,
  characteristic writes/notifications and RSSI additionally reject a paused or
  retiring transport. Posted authentication retries carry a GATT and generation.
- `STATE_CONNECTED` requires `GATT_SUCCESS`; a failed service-discovery request
  initiates bounded disconnect recovery.

## Timeouts and escalation

| Stage | Deadline | Recovery |
| --- | --- | --- |
| `connectGatt` without connection result | 120 seconds | Ordinary reconnect first; hard recovery after a second timeout on phone |
| Connected, without a complete sensor packet | 120 seconds from successful connection | Same escalation |
| Streaming, without another complete packet | 120 seconds from the last complete packet | Same escalation |
| Disconnect without completion callback | 2 seconds | Retire old GATT, then reconnect with the requested delay |

Descriptor activity, RSSI and partial fragments never extend the handshake/data
deadline. A complete packet clears the timeout counter. This measures packet
delivery, not whether the algorithm produced a valid glucose value.

The existing phone recovery for persistent status 8/19/147 is retained: after
120 seconds without a complete packet clearing that failure window, a subsequent
matching disconnect escalates to hard recovery.

Hard recovery retains the local native Bluetooth-authentication reset, transport
retirement, remembered-address device reconstruction, 6-second cooldown and
200-millisecond connection delay. It clears the timeout counter so the next
failure cycle begins with an ordinary reconnect. Phone-only hard recovery and
packet watchdog remain disabled on Wear; the connect-attempt deadline applies to
Libre 2 on both platforms.

Pause, close and native pointer reset cancel recovery tasks. Generations prevent
a canceled cooldown from resuming after user pause. Clone/ownership guards apply
before scheduling, execution, escalation and cooldown resume.

## Validation and remaining device checks

No compilation or runtime tests were run, as requested. Static review checked
balanced Java delimiters, timer/generation guards and both reconnect overloads.
The complete-packet processing block (including `processTooth` and bounded
wake-lock cleanup), shared glucose result handling and outbound pipeline were
compared byte-for-byte with the parent and are unchanged.

Device checks still required: disconnect with/without callback; silent connect;
stalled authentication with continuing RSSI/partial packets; repeated timeouts;
pause/removal/NFC reset during the cooldown; ownership handoff and multiple
sensors; both existing autoConnect settings. Use `LIBRE2_DIAG` stage and GATT
identity to correlate retries and verify that retired sessions cannot advance
the replacement's handshake or cancel its deadlines.
