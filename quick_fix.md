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
