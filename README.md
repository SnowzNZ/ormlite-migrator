# ORMLite Migrator

A lightweight schema migration helper for [ORMLite](http://ormlite.com) projects. Point it at your annotated model classes, and it will diff your live database schema against the `@DatabaseTable`/`@DatabaseField` metadata, generate the SQL needed to close the gap, and optionally run it for you. It is especially handy for JVM services that prefer incremental schema evolution without a full migration DSL.

## Features
- Supports MySQL, PostgreSQL, SQLite, MariaDB, and H2 via standard JDBC URLs.
- Reads ORMLite annotations to create tables, add columns, and emit indexes/unique constraints.
- Works in "dry run" (`generate`) or "apply" (`migrate`) modes.
- Ships with SLF4J logging so you can trace generated SQL.

## Requirements
- JDK 8+
- ORMLite core + JDBC modules (already declared in this library's `api` scope)
- JDBC driver for your target database (e.g., `org.xerial:sqlite-jdbc`, `mysql:mysql-connector-java`, etc.)

## Installation
Bring the artifact in via Gradle or Maven from Maven Central (or your local cache while you iterate).

<details>
<summary>Gradle (Kotlin DSL)</summary>

```kotlin
dependencies {
    implementation("one.trueorigin:ormlite-migrator:1.0.5-SNAPSHOT")
}
```
</details>

<details>
<summary>Maven</summary>

```xml
<dependency>
    <groupId>one.trueorigin</groupId>
    <artifactId>ormlite-migrator</artifactId>
    <version>1.0.5-SNAPSHOT</version>
</dependency>
```
</details>

> **Note:** Until a non-SNAPSHOT is published, make sure your `repositories` include Maven Central *and* your local Maven cache (`mavenLocal()`) if you built the artifact yourself.

## Quick Start
1. **Annotate your entities** using ORMLite's `@DatabaseTable` and `@DatabaseField`.
2. **Open a connection** with `DatabaseConnectionManager.withConnection("jdbc:...")`.
3. **Queue the models** on a `SchemaInterpreter` via `model(...)` calls.
4. **Generate SQL** with `generate()` or execute it with `migrate()`.

```java
@DatabaseTable(tableName = "users")
class User {
    @DatabaseField(generatedId = true) private int id;
    @DatabaseField(canBeNull = false) private String name;
    @DatabaseField(index = true) private String email;
}

final Database db = DatabaseConnectionManager.withConnection("jdbc:sqlite:app.db");
new SchemaInterpreter(db)
    .model(User.class)
    .migrate();
```

### Multiple Models / Multi-Stage Migrations
Chain additional `model()` calls to queue multiple classes, or run the interpreter again after updating a model. The interpreter compares each model against the live schema and only emits `CREATE TABLE`, `ALTER TABLE ... ADD COLUMN`, and relevant index statements when needed.

```java
final SchemaInterpreter interpreter = new SchemaInterpreter(DatabaseConnectionManager.withConnection(connStr));
interpreter.model(User.class)
            .model(Project.class);

final String sql = interpreter.generate(); // Inspect or log first
interpreter.migrate();                     // Execute if you are satisfied
```

## Working With Connection Strings
`DatabaseConnectionManager.withConnection(...)` inspects the JDBC prefix to load the correct driver:

| Prefix | Driver Constant | `Database.Type` |
|--------|-----------------|-----------------|
| `jdbc:mysql:` | `com.mysql.cj.jdbc.Driver` | `MySQL` |
| `jdbc:sqlite:` | `org.sqlite.JDBC` | `SqlLite` |
| `jdbc:postgresql:` | `org.postgresql.Driver` | `Postgres` |
| `jdbc:mariadb:` | `org.mariadb.jdbc.Driver` | `MariaDB` |
| `jdbc:h2:` | `org.h2.Driver` | `H2` |

If the string does not match a supported prefix, a `ConnectionStringException` is thrown. For connection pools, create your own `Database` instance with an existing `Connection` and pass it to `SchemaInterpreter`.

## Indexes & Constraints
- Use `@DatabaseField(index = true)` or `unique = true` for single-column indexes.
- Provide `indexName` or `uniqueIndexName` to create composite (multi-column) indexes.
- Primary keys are inferred from fields where `id` or `generatedId` is true.
- Index discovery is driver-specific. SQLite indexes are introspected fully; MySQL reports via `SHOW INDEXES`. PostgreSQL/H2 currently skip index diffing (safe no-ops), so consider managing advanced indexes manually if you rely on those databases.

## Error Handling & Logging
- Missing `@DatabaseTable` annotations raise `TableAnnotationNotFound`.
- Entities without `@DatabaseField` members raise `NoFieldDefinedException`.
- SQLExceptions are logged and swallowed during `migrate()` execution, so check your logs (`org.slf4j` logger `SchemaInterpreter`).
- Use `generate()` in CI to fail fast, or wrap `migrate()` invocations with your own transaction/rollback logic if required.

## Testing The Migrator Locally
A realistic way to validate your integration is to run the bundled tests, which cover SQLite migrations end-to-end and multi-database scenarios.

```powershell
./gradlew test
```

The `SQLiteMigrationTest` (`src/test/java/dev/snowz/ormlitemigrator/SQLiteMigrationTest.java`) demonstrates creating a table, adding a column in a follow-up migration, and verifying the schema with PRAGMA queries.

## Troubleshooting
- **"... is not valid"**: your JDBC string does not match any supported prefix.
- **Columns not created**: ensure the field has `@DatabaseField` *and* the database user has `ALTER TABLE` privileges.
- **Indexes missing on PostgreSQL/H2**: composite index diffing is a no-op today. Apply custom SQL or extend `SchemaInterpreter#getIndexes`.

## Roadmap Ideas
- Full index diffing on PostgreSQL and H2.
- Column type change detection.
- Hooks for seeding data post-migration.

Contributions and bug reports are welcome—open an issue or PR with reproduction steps and target database details.

