Found it — we hit this exact error on August 14 ("Fixing NaN rendering error in React"), same file, same line 23. But that conversation ended after my first reply, so we never confirmed the root cause. That's probably why it's back.

**The line:**
```tsx
<div className="ft-sz-14px width-50">{data.priorityScore + data.arisk}</div>
```

One of those is `undefined`, so the sum is `NaN`, and React won't render that as a child.

**The fix I gave last time:**
```tsx
const total = Number(data?.priorityScore ?? 0) + Number(data?.arisk ?? 0);

<div className="ft-sz-14px width-50">
  {Number.isFinite(total) ? total : "—"}
</div>
```

**What we never resolved:** whether `arisk` is even the right key. I flagged it last time because the casing looks inconsistent next to `priorityScore` — if the API actually returns `aRisk` or `aRiskScore`, the `?? 0` fallback will render a silently wrong number instead of crashing, which is worse than the error you're seeing now.

So before you ship it: `console.log(data)` at the top of `CaseDetailsSummary` and check the actual field name. If the key is correct and it's just the first render before data loads, this version avoids the false zero:

```tsx
const { priorityScore, arisk } = data ?? {};
const total = priorityScore != null && arisk != null
  ? Number(priorityScore) + Number(arisk)
  : null;

<div className="ft-sz-14px width-50">{total ?? "—"}</div>
```

What does `console.log(data)` show?
