# Room Migration SQL Templates

Common patterns for AuraChat's two tables: `conversations` and `messages`.

---

## Add a Nullable Column

```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN summaryText TEXT")
        // SQLite ALTER TABLE only supports adding columns, and only as nullable
        // or with a DEFAULT value — it cannot rename or drop columns directly
    }
}
```

## Add a Column with a Default Value

```kotlin
db.execSQL("ALTER TABLE messages ADD COLUMN isBookmarked INTEGER NOT NULL DEFAULT 0")
```

## Rename a Column (SQLite < 3.25 workaround — required for Min SDK 24)

SQLite on older Android does not support `RENAME COLUMN`. Use table-copy strategy:

```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create new table with desired schema
        db.execSQL("""
            CREATE TABLE messages_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                conversationId INTEGER NOT NULL,
                role TEXT NOT NULL,
                body TEXT NOT NULL,           -- renamed from 'content'
                createdAtEpochMs INTEGER NOT NULL,
                deliveryState TEXT NOT NULL,
                errorType TEXT,
                FOREIGN KEY(conversationId) REFERENCES conversations(id) ON DELETE CASCADE
            )
        """.trimIndent())
        // 2. Copy data, mapping old column name to new
        db.execSQL("""
            INSERT INTO messages_new (id, conversationId, role, body, createdAtEpochMs, deliveryState, errorType)
            SELECT id, conversationId, role, content, createdAtEpochMs, deliveryState, errorType
            FROM messages
        """.trimIndent())
        // 3. Drop old table and rename
        db.execSQL("DROP TABLE messages")
        db.execSQL("ALTER TABLE messages_new RENAME TO messages")
        // 4. Recreate indices
        db.execSQL("CREATE INDEX index_messages_conversationId_createdAtEpochMs ON messages(conversationId, createdAtEpochMs)")
        db.execSQL("CREATE INDEX index_messages_conversationId_role_deliveryState ON messages(conversationId, role, deliveryState)")
    }
}
```

## Add a New Table

```kotlin
db.execSQL("""
    CREATE TABLE IF NOT EXISTS attachments (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        messageId INTEGER NOT NULL,
        mimeType TEXT NOT NULL,
        uri TEXT NOT NULL,
        FOREIGN KEY(messageId) REFERENCES messages(id) ON DELETE CASCADE
    )
""".trimIndent())
db.execSQL("CREATE INDEX index_attachments_messageId ON attachments(messageId)")
```

## Drop a Table

```kotlin
db.execSQL("DROP TABLE IF EXISTS legacy_table")
```

## Add an Index

```kotlin
db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_updatedAt ON conversations(updatedAtEpochMs)")
```

---

## AuraChat Current Schema (version 1)

### conversations

| Column           | Type                     | Notes |
| ---------------- | ------------------------ | ----- |
| id               | INTEGER PK AUTOINCREMENT |       |
| title            | TEXT NOT NULL            |       |
| createdAtEpochMs | INTEGER NOT NULL         |       |
| updatedAtEpochMs | INTEGER NOT NULL         |       |

### messages

| Column           | Type                     | Notes                                |
| ---------------- | ------------------------ | ------------------------------------ |
| id               | INTEGER PK AUTOINCREMENT |                                      |
| conversationId   | INTEGER NOT NULL         | FK → conversations.id CASCADE DELETE |
| role             | TEXT NOT NULL            |                                      |
| content          | TEXT NOT NULL            |                                      |
| createdAtEpochMs | INTEGER NOT NULL         |                                      |
| deliveryState    | TEXT NOT NULL            | `SENT` or `FAILED`                   |
| errorType        | TEXT                     | nullable                             |

**Indices on `messages`:**

- `(conversationId, createdAtEpochMs)`
- `(conversationId, role, deliveryState)`
