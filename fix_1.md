Yes — this is the piece I needed, and it tells me exactly where the trim goes.

## What `getBaseSql` is actually doing

`getViewInfo` calls `DatabaseMetaData.getColumns()` and returns **every physical column on the view**, in DDL order. So `getBaseSql` is a longhand `SELECT *`. That's your 130 columns, and line 236 is the single point where they're chosen.

Two things I can see from here before we touch anything:

- **The metadata round-trip runs on every query.** `getColumns()` against Oracle's dictionary is not free, and the view's shape doesn't change between requests. Free win to cache.
- **Empty metadata produces broken SQL, not null.** If `getViewInfo` returns nothing, `columnNamesStr` is `""` and you emit `SELECT  FROM MVIEW…` → ORA-00936. The "returns null when metadata cannot be read" contract in `buildQbModuleViewSql` only covers the `getViewColumns` SQLException at 842, not this path.

---

## The blocker, stated precisely

`getViewInfo` returns **DB column names**. `ModuleViewColumns` holds **87 display labels**. There's no join between them in anything I've seen.

The bridge has to be `ViewColumnMeta` — the type at line 838 of `ModuleViewService`, populated by `QueryBuildUtil.getViewColumns(ds, mView)`. If that object carries both a label and a column name, everything works. If it doesn't, we need a different mapping source.

**Send me `ViewColumnMeta` (the class — fields/getters) and `getViewColumns`.** That's the last thing I need.

---

## Phase A — apply now, no dependencies

**Step 1 — `QueryBuildUtil.getViewInfo`, guard the empty case**

At the end of the lambda, before `return columnNames;`:

```java
if (columnNames.isEmpty()) {
    throw new IllegalStateException(
            "No column metadata found for view " + viewName.toUpperCase()
            + " - check the view exists and the connecting user can see it");
}
```

Better a clear exception than ORA-00936 from generated SQL.

**Step 2 — cache the metadata lookup**

You already have Caffeine. Wrap `getViewInfo` keyed on `viewName.toUpperCase()`. The view's columns are stable for the life of the process, so even a long TTL is safe. This removes a dictionary query from every single request.

**Step 3 — `ModuleViewService`, the row cap (lines 886–892)**

Replace the current block with:

```java
String sortString = forExport ? "" : QueryBuildUtil.getSortString(vcMetas, queryName);
String baseSql = QueryBuildUtil.getBaseSql(mView, jdbcTemplate, "M");
tmpSql.append(clauseSql);
if (!forExport) {
    tmpSql.append(sortString);
    tmpSql.append(String.format(" FETCH FIRST %d ROWS ONLY %n",
            gridLimitFor(queryName.getElevel())));
}
dynamicSql.append(baseSql);
dynamicSql.append(tmpSql);
```

plus the helper:

```java
private static final int GRID_LIMIT_GROUP_AND_BELOW = 100_001;
private static final int GRID_LIMIT_ABOVE_GROUP     = 10_001;

private int gridLimitFor(Integer elevel) {
    if (elevel == null) return GRID_LIMIT_ABOVE_GROUP;   // fail closed
    return isGroupOrBelow(elevel) ? GRID_LIMIT_GROUP_AND_BELOW
                                  : GRID_LIMIT_ABOVE_GROUP;
}
```

Still need your `elevel` ladder for `isGroupOrBelow`.

**Step 4 — align the `subList` in `QueryBuilderController`** to the same `gridLimitFor` value, and check `MAX_ROWS` at the JDBC layer isn't capping below it. On Case View that constant silently truncated below the SQL sentinel and made the whole thing undetectable.

**Gate A:** group-level query returns rows and the SQL log shows `FETCH FIRST 100001`. Above-group shows `10001`. Nothing else changed.

---

## Phase B — the trim, one commit

**Step 5 — additive overload in `QueryBuildUtil`**

Keep the 3-arg signature delegating, so Case/Activity/Time are untouched:

```java
public static String getBaseSql(String viewName, NamedParameterJdbcTemplate jdbcTemplate,
                                String viewType) {
    return getBaseSql(viewName, jdbcTemplate, viewType, null);
}

public static String getBaseSql(String viewName, NamedParameterJdbcTemplate jdbcTemplate,
                                String viewType, Collection<String> allowedColumns) {

    List<String> columnNames = getViewInfo(viewName, jdbcTemplate);

    if (allowedColumns != null && !allowedColumns.isEmpty()) {
        Set<String> allow = allowedColumns.stream()
                .map(s -> s.trim().toUpperCase())
                .collect(Collectors.toSet());

        List<String> filtered = columnNames.stream()
                .filter(c -> allow.contains(c.toUpperCase()))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            log.error("Column allowlist matched 0 of {} columns on view {} - "
                    + "falling back to full projection", columnNames.size(), viewName);
        } else {
            log.info("Projection trimmed {} -> {} columns for view {}",
                    columnNames.size(), filtered.size(), viewName);
            columnNames = filtered;
        }
    }
    // ...rest unchanged
}
```

Filtering rather than emitting the allowlist directly means a stale name in the list can't produce ORA-00904 — it just doesn't match. Uppercase both sides; Oracle metadata comes back uppercase.

The fallback-on-empty is deliberate but debatable: it keeps the app up if the mapping is wrong, at the cost of quietly restoring 130 columns. That's why it logs at ERROR. If you'd rather fail loudly on first deploy, throw instead — your call, but decide it consciously.

**Step 6 — `ModuleViewService` line 888, pass the allowlist**

Derive column names from the 87 labels via `vcMetas` (already in scope at 838), then:

```java
String baseSql = QueryBuildUtil.getBaseSql(mView, jdbcTemplate, "M", moduleColumns);
```

**Step 7 — trim `queryQbModuleViewData` (699–833) in the same commit.** It reads by column name across ~130 fields; the moment the projection narrows, every unmapped `rs.getX("COL")` throws. These two edits cannot ship separately.

**Step 8 — check `ModuleViewCsvFormatter`.** Line 888 feeds both grid and export, so the CSV narrows automatically — which is what Santosh's action item asks for. But if the formatter's header list names any of the 43 dropped columns, the export breaks. Worth confirming the client wants the CSV narrowed too, since some analysts may be relying on getting everything.

**Gate B:** same query, same row count, 87 columns, log line shows `130 -> 87`, CSV header count matches.

---

Send `ViewColumnMeta` and `getViewColumns` and I'll write Step 6 concretely. Phase A you can start on now.
