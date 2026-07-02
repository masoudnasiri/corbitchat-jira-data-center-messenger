# Sprint 12 — Productivity: Advanced Dashboard, Notification Preferences, Deep Links, Biometrics, Share Sheet

**Phase:** 3 — Board & Productivity · **Duration:** 2 weeks · **Goal:**
round out the productivity layer — richer dashboard, full notification
preferences, complete deep-linking, biometric unlock and share-sheet.

> Spec: PDF §4.2 (dashboard), §7.1 (notification prefs), §5 (UX), §6.4
> (security). Design: `docs/layout.md` (Deep Link Handling), `docs/states.md`,
> `docs/components.md` (Notification List Item, swipe). Closes Phase 3.

## Objective

Make the app a proper daily driver: advanced dashboard sections, full
notification control (per-channel, quiet hours, detail level, badges),
robust deep-linking across all schemes, biometric app-lock, and OS
share-sheet integration.

## In scope

- Advanced dashboard: follow-up quick actions, recent reports/activity
  feed, colleagues with presence + quick chat, per-card refresh.
- Notification preferences (PDF §7.1): enable/disable per channel
  (direct/group/project/Assistant/assignments/reminders); quiet hours;
  per-conversation/project mute; detail level (full text / key-only /
  generic); global + per-tab badges.
- Deep-linking: complete `corbitchat://` router for all documented paths
  incl. `?comment=` scroll-to and `?message=` scroll-to, cold-start +
  warm-start, with loading skeleton (`docs/layout.md`).
- Biometric unlock (Face/Touch ID, Android BiometricPrompt) gating app
  open + secure-store access.
- OS share-sheet outbound (share issue/message link) + inbound (already
  from Sprint 07) consolidation.

## Backend track (plugin BFF)

1. Extend `JimMobilePreference` for the full notification model
   (per-channel flags, quietHours, muteList, detailLevel) +
   `GET/PUT /rest/corbit-mobile/1.0/preferences` fields.
2. Respect preferences in the push sender (Sprint 05): suppress during
   quiet hours, honor per-channel/mute, trim payload to detail level
   (PDF §7.1, §6.4).
3. `GET /rest/corbit-mobile/1.0/notifications` feed (mark-seen, mark-all)
   backing the Notifications tab; unify with Assistant events.

## Mobile track (Flutter)

1. Settings → Notifications screen: channel toggles, quiet-hours picker,
   detail-level selector, per-conversation mute entry points.
2. Notifications tab: list (`docs/components.md` Notification List Item),
   unread dot, swipe to mark-read / mute (Phase 3 optional swipe), mark
   all read.
3. Deep-link router hardening for all schemes + scroll-to-target;
   graceful handling when target lacks permission.
4. Biometric lock (`local_auth`): lock on background/timeout; unlock
   before revealing secure data; fallback to PIN/passphrase.
5. Share-sheet: share issue/message deep links; ensure inbound shares
   route to conversation/issue targets.
6. Advanced dashboard sections + per-card refresh + badge wiring.

## Design references

- `docs/layout.md` — Deep Link Handling table, Dashboard Layout.
- `docs/components.md` — Notification List Item (swipe), Bottom Nav badges.
- `docs/states.md` — Sync Indicator, offline banner.

## API contract

```
GET/PUT /rest/corbit-mobile/1.0/preferences        (full notification model)
GET     /rest/corbit-mobile/1.0/notifications       feed
POST    /rest/corbit-mobile/1.0/notifications/read   { ids | all }
```

## Dependencies

- Sprint 05 (push + device), Sprint 08 (Assistant feed), Sprint 03
  (dashboard base), Sprint 01 (preferences base).

## Acceptance criteria (PDF §7.1, §9 Phase 3)

- [ ] Notification preferences (channels, quiet hours, detail level,
      mute) are honored by server-side push.
- [ ] Deep links open the correct screen from cold + warm start, incl.
      scroll-to comment/message; both locales.
- [ ] Biometric unlock gates app open; fallback works.
- [ ] Share-sheet outbound/inbound works.
- [ ] Global + per-tab badges accurate.

## Definition of Done

- Quiet hours / detail level verified end-to-end (server trims payload).
- Deep-link permission failures show no-permission, not a crash.
- RTL verified for settings, notifications, share flows.
