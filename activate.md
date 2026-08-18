Pulled our prior context — the ENTEMP model, the ADFS/BEARS auth flow, and the March 18 decision. Here's the breakdown.

## The constraint that shapes this whole ticket

**Nothing here can write to `ENTEMP`.** `EACTIVE` and `ELEVEL` belong to the nightly ICS/ETL (E5) feed — anything the app sets during the day gets overwritten at night. So the deactivation flag needs its own app-owned table. Also don't reuse `ELEVEL = -2` as the "deactivated" signal; that already means blocked/vacant position and you'd never be able to tell the two apart in support calls.

Second thing: because ADFS handles authentication, a deactivated user still *authenticates successfully*. This is not a login-form error — it's a blocking gate after the token comes back, at the point where the app builds their context.

## Backend

**Tables (new, app-owned):**

- `ENTITY_USER_STATUS` — `SEID` (PK), `STATUS` (ACTIVE/DISABLED/QUARANTINED), `DISABLED_DATE`, `DISABLED_REASON`, `LAST_LOGIN_TS`, `NOTIFIED_TS`, `REACTIVATED_DATE`, `REACTIVATED_BY`. EN-1156 writes it, EN-1157 reads it.
- `ENTITY_USER_STATUS_AUDIT` — every transition, with actor and timestamp. FISMA will want this and it's cheaper to build now than retrofit.
- `ENTITY_APP_CONFIG` (key/value) — support email, phone, Help Ticket URL, Case Management URL. Those links are environment-specific; hardcoding them in the React bundle means a redeploy every time one changes.

**Endpoints:**

- `GET /entity/api/user/status/{seid}` → `{status, disabledDate, daysInactive, supportContact{}, reactivationUrl}`. Apply `CacheControl.noStore()` — same stale-response problem we hit on the Change Access endpoints.
- Extend the existing `/config/{seid}` to return `accountStatus` so the SPA doesn't need a second round trip on boot.
- A `HandlerInterceptor` or filter that returns **403 with a structured body** (`{"errorCode":"ACCOUNT_DEACTIVATED", ...}`) on every other `/entity/api/**` call. Without this, someone can deep-link past the modal.
- `POST /entity/api/user/reactivation-request` — only if you're doing the in-app request option (see flags below).

**The subtle bug to avoid:** whatever records `LAST_LOGIN_TS` must run *after* the deactivation check, not before. Otherwise a deactivated user hitting the app resets their own inactivity counter and the 240-day quarantine clock never advances.

## Frontend

- `userStatusSlice` in Redux, populated from the config call at app bootstrap.
- A `<DeactivatedGate>` wrapper above the router — renders the blocking dialog instead of the app shell. Don't put it inside a route, or navigation escapes it.
- PrimeReact `<Dialog>` with `closable={false}`, `dismissableMask={false}`, no ESC handler. `role="alertdialog"` + focus trap — this is a 508 deliverable.
- Axios response interceptor catching `ACCOUNT_DEACTIVATED` → dispatch to the same slice, so a mid-session deactivation surfaces too.
- **Clear `entity_original_role_{seid}` and `entity_user_context_{seid}` on deactivation.** Stale localStorage is what bit us on the Change Role "sticking" complaints.

## Three things to settle before a dev starts

1. **The message text in the ticket is garbled** — "deactivated due to inactivity due to more than 120 inactivity." Someone needs to sign off on clean copy, and confirm whether the two message variants are meant to be two different states (disabled vs. quarantined) or just a draft artifact.
2. **Support contact method** — the ticket says "email, phone, or in-app request." In-app request means building the reactivation intake, which is really ticket 5's scope. If it's just links, EN-1157 stays small.
3. **What happens on day 241** — same modal, different message, or a hard block? The quarantine path removes the BEARS entitlement, so the instructions to the user are completely different ("re-request the entitlement," not "contact support").

Want me to turn this into a Word doc for the team, or write the actual `ENTITY_USER_STATUS` DDL and the interceptor?
