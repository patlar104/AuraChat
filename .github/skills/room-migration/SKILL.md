---
name: room-migration
description: 'Guide Room database schema migrations in AuraChat. Use when adding/removing columns, renaming tables, changing entity structure, or bumping database version. Covers version bumps, @Migration classes, fallback strategies, and schema export setup.'
argument-hint: 'Describe the schema change (e.g., "add summaryText column to conversations")'
---

# Room Migration — AuraChat

## When to Use
- Adding or removing a column on `ConversationEntity` or `MessageEntity`
- Renaming a table or column
- Adding a new `@Entity` or `@Index`
- Changing a `ForeignKey` constraint
- Any change that requires a `@Database(version = ...)` bump

---

## Step 1 — Enable Schema Export (if not already done)

`AuraChatDatabase` currently has `exportSchema = false`. **Enable it first** so Room generates JSON snapshots that make future migrations auditable.

**`AuraChatDatabase.kt`** — change the annotation:
```kotlin
@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 1,          // ← bump this in Step 2
    exportSchema = true   // ← change from false
)
```

**`app/build.gradle.kts`** — add the KSP argument so snapshots are written to `schemas/`:
```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

Commit the generated `schemas/com.personal.aurachat.data.local.AuraChatDatabase/<version>.json` alongside every migration.

---

## Step 2 — Bump the Version

In `AuraChatDatabase.kt`:
```kotlin
@Database(
    entities = [...],
    version = N,   // was N-1
    exportSchema = true
)
```

> **Rule**: version is a monotonically increasing integer. Never reuse or skip numbers.

---

## Step 3 — Write the Migration

Create a `Migrations.kt` file (or add to the existing one) in `data/local/`:

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Example: add a nullable column
        db.execSQL("ALTER TABLE conversations ADD COLUMN summaryText TEXT")
    }
}
```

See [migration-templates.md](./references/migration-templates.md) for common SQL patterns.

---

## Step 4 — Register the Migration

In `AppContainer.kt`, find the Room builder (the only place the DB is constructed) and add `.addMigrations(...)`:

```kotlin
Room.databaseBuilder(context, AuraChatDatabase::class.java, "aurachat.db")
    .addMigrations(MIGRATION_1_2)   // add new migration here
    .build()
```

---

## Step 5 — Choose a Fallback Strategy

| Scenario | Action |
|---|---|
| **Development / data is disposable** | Add `.fallbackToDestructiveMigration()` to the builder — wipes and recreates on version mismatch |
| **Production / user data must survive** | Do NOT use fallback; write a proper `Migration` for every version gap |
| **Missing intermediate migration** | Add all chained migrations: `MIGRATION_1_2`, `MIGRATION_2_3`, etc. Room chains them automatically |

> AuraChat is currently at version 1 (dev). If releasing publicly, remove `fallbackToDestructiveMigration` before going to production.

---

## Step 6 — Verify

```bash
./gradlew test                   # Unit tests — catch DAO/entity compile errors
./gradlew assembleDebug          # Full build — catches KSP/Room annotation errors
```

If you have `RoomDatabase.Builder` tests or a `MigrationTestHelper`, run those too.

---

## Step 7 — Commit Checklist

- [ ] `@Database(version = N)` bumped
- [ ] `Migration(N-1, N)` class written with correct SQL
- [ ] Migration registered in `AppContainer.kt`
- [ ] `schemas/<version>.json` committed (if exportSchema = true)
- [ ] `./gradlew test` and `./gradlew assembleDebug` pass
- [ ] No `fallbackToDestructiveMigration` in production builds
