`hiddenColumns` contains two entries with `field: 'closedCaseEntityMethod'`. The Add Field dropdown maps over it and uses `field` as the React key, so the duplicate collides.

## Quick fix — line 1845

Make the key unique so React stops complaining:

```tsx
{hiddenColumns.sort((a, b) => (a.header ?? '').localeCompare(b.header ?? '')).map((hiddenColumn, i) => {
    return <option key={`${hiddenColumn.field}-${i}`} value={hiddenColumn.field}>{hiddenColumn.header}</option>
})}
```

That silences the error, but you'll still have two identical-looking entries in the dropdown, and picking either resolves to the same field.

## Better fix — dedupe instead

```tsx
{Array.from(new Map(hiddenColumns.map(c => [c.field, c])).values())
    .sort((a, b) => (a.header ?? '').localeCompare(b.header ?? ''))
    .map((hiddenColumn) => (
        <option key={hiddenColumn.field} value={hiddenColumn.field}>{hiddenColumn.header}</option>
    ))}
```

One entry per field, keys stay clean, no duplicate option for the user to pick.

## Worth checking

The duplicate only appears on Return to Queue, so the column definitions for that status branch likely list `closedCaseEntityMethod` twice — probably once with a status-specific header. Search the file for `closedCaseEntityMethod` and see whether both entries are intentional. If one is a copy-paste artifact, deleting it at the source is cleaner than deduping at render time.

Also: line 1844 sorts `hiddenColumns` in place. `Array.prototype.sort` mutates, so if `hiddenColumns` is derived from `columns` or `viewFieldMaster`, you're reordering shared state on every render. The `Array.from(new Map(...))` version above creates a new array first, which fixes that as a side effect.


Yes, 1844–1846. But note there's a **second copy** at 1852–1854 — the `isLoading` disabled variant. Same bug, so replace both.

**Lines 1844–1846** (inside `{!isLoading &&`):

```tsx
{Array.from(new Map(hiddenColumns.map(c => [c.field, c])).values())
    .sort((a, b) => (a.header ?? '').localeCompare(b.header ?? ''))
    .map((hiddenColumn) => (
        <option key={hiddenColumn.field} value={hiddenColumn.field}>{hiddenColumn.header}</option>
    ))}
```

**Lines 1852–1854** (inside `{isLoading &&`) — identical replacement.

Since it's duplicated, consider hoisting it above the `return` in `header`:

```tsx
const uniqueHiddenColumns = Array.from(new Map(hiddenColumns.map(c => [c.field, c])).values())
    .sort((a, b) => (a.header ?? '').localeCompare(b.header ?? ''));
```

Then both spots become:

```tsx
{uniqueHiddenColumns.map((hiddenColumn) => (
    <option key={hiddenColumn.field} value={hiddenColumn.field}>{hiddenColumn.header}</option>
))}
```

One source of truth, and the dedupe+sort runs once per render instead of twice.

`Map` keeps the **first** occurrence of each field, so if the two `closedCaseEntityMethod` entries have different headers, you'll get whichever appears first in `hiddenColumns`. Worth a glance at the source definitions to confirm that's the one you want.


Good catch, but I don't think we have to give up default sorts. PrimeReact's DataTable has a built-in pre-sort — you pass sortField + sortOrder on the table (or multiSortMeta for multi-column) and it renders already sorted on first paint. No custom logic, no extra call.

On the rename risk: the columns are already bound to the DB field names through Column field="...", so a rename breaks the grid regardless of whether a default sort exists. It's the same coupling we already have. Two things keep it safe:

1. Put the default-sort flag on the field definition record itself so it travels with the field, instead of storing field names in a separate config that can drift.
2. Validate sortField against the current column list before passing it in, and fall back to unsorted if it doesn't match. A rename then degrades quietly instead of erroring.

So I'd say include the default sort in this ticket. Only caveat: if any column needs a custom sortFunction, that doesn't get applied on the initial pre-sort render, only after a header click — fine for plain string/date/number fields.
