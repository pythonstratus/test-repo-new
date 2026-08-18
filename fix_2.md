That's the ladder. Mapping it to the meeting's vocabulary:

| elevel | Code comment | Meeting term |
|---|---|---|
| 0 | national | National |
| 2 | area | Area |
| 4 | territory | Territory |
| 6 | local/town | **Group** |
| 8 | exact | **Revenue Officer** |

The arithmetic confirms it: case 6 gives `roId*100 … +99`, i.e. 100 slots under one node — ROs under a group. Case 8 is a single `roId` — one RO. That matches "display at both RO and group levels" exactly. Worth a one-line confirmation from Sarah since "local/town" isn't the word anyone used in the meeting, but I'm confident.

---

## The 5 errors

Three are mine — I gave you `gridLimitFor` without `isGroupOrBelow`, and a `log` call in a class that has no logger.

### 1 & 2 — `ModuleViewService` (888 and 914)

**Line 888, `moduleColumns`** — that's a Phase B line and Phase B isn't ready (I still need `ViewColumnMeta`). Revert it to the 3-arg call for now:

```java
String baseSql = QueryBuildUtil.getBaseSql(mView, jdbcTemplate, "M");
```

The overload stays in `QueryBuildUtil` — it's additive and harmless until something passes a list.

**Line 914, `isGroupOrBelow`** — add it next to `gridLimitFor`:

```java
/**
 * Elevel ladder, per QueryBuildUtil.getLevelRange:
 *   0 national, 2 area, 4 territory, 6 group, 8 individual RO.
 * Per the 08/14 decision, group and below display in-app; above group is CSV.
 * Explicit allowlist rather than >= 6, so an unrecognised level fails closed
 * to the small cap instead of granting a 100K grid by accident.
 */
private boolean isGroupOrBelow(int elevel) {
    return switch (elevel) {
        case 6, 8 -> true;
        default   -> false;
    };
}
```

**Imports for `ModuleViewService`: none.** Everything here is `java.lang`.

### 3 — `QueryBuildUtil:245`, `Set`

```java
import java.util.Set;
```

`Collectors` is already imported (line 238 uses `Collectors.joining`), and you've added `Collection`. Nothing else needed.

### 4 & 5 — `QueryBuildUtil:254,257`, `log`

`QueryBuildUtil` has no logger. Check the top of `ModuleViewService` and mirror whatever it does:

**If it uses Lombok** (`@Slf4j` above the class):
```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QueryBuildUtil {
```

**If it declares one manually**, or you'd rather not depend on Lombok here:
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryBuildUtil {
    private static final Logger log = LoggerFactory.getLogger(QueryBuildUtil.class);
```

Either works — `@Slf4j` generates a `private static final Logger` regardless of whether the methods are static.

---

## Order of operations

1. Revert 888 to the 3-arg call
2. Add `isGroupOrBelow` to `ModuleViewService`
3. Add `import java.util.Set;` and a logger to `QueryBuildUtil`
4. `mvn clean compile` → green
5. Run a group-level query, confirm the SQL log shows `FETCH FIRST 100001`; run National, confirm `10001`

That's Phase A complete and independently testable. Phase B unblocks the moment I see `ViewColumnMeta` and `getViewColumns` — those are the two that tell me whether the 87 labels can reach DB column names.
