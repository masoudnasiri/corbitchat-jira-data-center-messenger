# CorbitChat — Marketplace Publishing Checklist

Step-by-step path from the current build to a **paid-via-Atlassian Data
Center** listing.

## 1. Vendor account

- [ ] Create/verify the vendor profile at https://marketplace.atlassian.com
      (vendor name **CorbitLogic**, support email, website).
- [ ] Accept the current **Atlassian Marketplace Vendor Agreement**.
- [ ] Set up payout details (required for paid apps).

## 2. Productize the build

- [x] **Version**: `1.0.0` in `pom.xml` (no SNAPSHOT — Marketplace rejects
      SNAPSHOT versions).
- [x] **Plugin metadata** in `atlassian-plugin.xml` `<plugin-info>`:
  - [x] `<vendor name="CorbitLogic" url="https://corbitlogic.com"/>`
  - [x] `<param name="atlassian-data-center-status">compatible</param>`
        and `<param name="atlassian-data-center-compatible">true</param>`
        (required for DC listings).
- [x] **License checks (required for paid-via-Atlassian)**: implemented via
      `PluginLicenseManager` + central `JimLicenseService`; backend REST
      endpoints return HTTP 402 `LICENSE_INVALID` when the license is
      missing/expired, and the chat UI degrades to read-only with a banner.
- [ ] Confirm compatibility range (e.g. Jira 9.12 – 9.17) by testing against
      the lowest version you want to declare.
- [ ] Final QA pass of the regression checklist in `AGENTS.md`.

## 3. Data Center approval (required for DC apps)

Atlassian reviews all Data Center listings. Prepare:

- [ ] **DC App Approval application** (developer.atlassian.com → Data Center
      app approval).
- [ ] **Performance & scale testing report**: run the app on a 2-node DC
      cluster with a large dataset (Atlassian provides the "DC App Performance
      Toolkit"); document throughput/response-time deltas with the app
      enabled vs disabled.
- [ ] Cluster-safety review notes (already by design):
  - All persistent state in the DB (Active Objects) or shared home.
  - Presence is node-local and degrades gracefully (documented limitation:
    "active now" may differ per node; consider cluster cache in a future
    release).
  - No node-local locks; polling REST traffic is lightweight and cache-free.
- [ ] **Security self-assessment** questionnaire (use
      `security-and-privacy.md` as the source).
- [ ] Optional but recommended: join the **Marketplace Bug Bounty** program
      (improves revenue share and customer trust).

## 4. Listing assets

- [ ] Logo 144×144, banner 1120×548, 5–7 screenshots 1840×1040
      (plan in `marketplace-listing.md`).
- [ ] Hosted documentation: publish `user-guide.md` and `admin-guide.md`
      (e.g. corbitlogic.com/docs/corbitchat or a public Confluence space).
- [ ] Hosted privacy/security page from `security-and-privacy.md`.
- [ ] EULA: Atlassian standard EULA or your own (URL).
- [ ] Support channel: portal or email + stated SLA (e.g. response within 1
      business day).

## 5. Create the listing

- [ ] Marketplace → "Create app listing" → upload the release JAR.
- [ ] App key must match the plugin key:
      `com.corbitlogic.corbitchat.jira.dc`
      (never change it between versions).
- [ ] Paste copy from `marketplace-listing.md` (name, tagline, highlights,
      description, keywords).
- [ ] Set pricing (per-user-tier table in `marketplace-listing.md`) and select
      **Paid via Atlassian**.
- [ ] Attach release notes from `release-notes.md`.
- [ ] Submit for approval.

## 6. After approval

- [ ] Test-purchase an evaluation license on a staging instance.
- [ ] Set up a public roadmap / issue tracker for customers.
- [ ] Plan the release cadence: Marketplace versions are immutable — every fix
      is a new version with release notes.

## Known gaps to disclose (or fix before submission)

| Item | Status |
|---|---|
| Paid licensing checks | **Not yet implemented** — required for paid listing (step 2) |
| "Active now" presence across cluster nodes | Node-local; cosmetic only |
| iOS Safari push | Platform limitation (Home-Screen PWA only) — document, can't fix |
| i18n | UI strings are English-only; i18n file exists, translations can be added later |
