**Module View — Data Display Work (Aug 18)**

**Backend**

- Implemented level-aware row caps in `ModuleViewService` — group and RO levels get 100K in-app, above group routes to CSV, per the Aug 14 decision
- Confirmed the elevel ladder (0 National, 2 Area, 4 Territory, 6 Group, 8 RO) and verified the cap firing correctly at National
- Built the column-trimming mechanism in `QueryBuildUtil` as an additive overload, so Case, Activity, and Time views are unaffected
- Transcribed and count-verified the 87 Module columns from the HQ all-fields doc — matches the meeting figure exactly
- Traced both data paths end to end (cached grid and Query Builder) and mapped where the caps and trims need to land on each

**Frontend**

- Found and fixed the status dropdown defect — the data-fetch effect had an empty dependency array, so it only ran on mount and never re-fired when status changed
- Currently working a follow-on issue: partial grid render with duplicate requests, likely an effect re-firing more than once per change

**On timeline**

- The work is doable and the approach is sound — no architectural surprises so far
- The honest constraint is verification time. Each change touches a shared path, so every edit needs a full cycle: rebuild, restart, warm the cache, run the query, read the logs, confirm nothing regressed on the other three views
- Cold cache loads alone take 30+ seconds, and several fixes couldn't be confirmed on the first attempt because the test exercised a different code path than the one we'd edited
- We also don't yet have a group large enough to prove the 100K target — largest tested so far is ~3,100 rows against a requirement of 97,000
- So: steady progress, but the testing overhead per change is real and worth factoring into the estimate rather than absorbing quietly
