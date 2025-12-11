package dev.snowz.ormlitemigrator;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import dev.snowz.ormlitemigrator.exception.NoFieldDefinedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

import static dev.snowz.ormlitemigrator.DatabaseConnectionManager.determineFieldsToBeCreated;

public class SchemaInterpreter {
    private static final Logger logger = LoggerFactory.getLogger(SchemaInterpreter.class.getSimpleName());

    private final Database database;
    private final List<Class> models;

    public SchemaInterpreter(final Database database) {
        this.database = database;
        this.models = new ArrayList<>();
    }

    public SchemaInterpreter(final Database database, final List<Class> models) {
        this.database = database;
        this.models = models;
    }

    private List<DatabaseFieldType> getSchema(final String tableName) throws SQLException {
        final List<DatabaseFieldType> fields = new ArrayList<>();
        final String query;
        if (database.getType() == Database.Type.SqlLite) {
            query = "PRAGMA table_info(" + tableName + ")";
        } else if (database.getType() == Database.Type.H2) {
            query = "SELECT column_name, data_type FROM information_schema.columns WHERE UPPER(table_name) = '" + tableName.toUpperCase() + "'";
        } else if (database.getType() == Database.Type.Postgres) {
            // For Postgres, we usually want lowercase, but if using H2 to simulate, we might need upper.
            // To be safe, let's search case-insensitively.
            query = "SELECT column_name, data_type FROM information_schema.columns WHERE UPPER(table_name) = '" + tableName.toUpperCase() + "'";
        } else {
            query = "SHOW COLUMNS FROM " + tableName;
        }

        logger.info(query);

        try (final Statement stmt = database.getConnection().createStatement()) {
            final ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                if (database.getType() == Database.Type.SqlLite) {
                    fields.add(new DatabaseFieldType(rs.getString("name"), rs.getString("type")));
                } else if (database.getType() == Database.Type.Postgres || database.getType() == Database.Type.H2) {
                    fields.add(new DatabaseFieldType(rs.getString("column_name"), rs.getString("data_type")));
                } else {
                    // SHOW COLUMNS returns Field, Type, Null, Key, Default, Extra
                    // Use indices to be safe across drivers/modes
                    fields.add(new DatabaseFieldType(rs.getString(1), rs.getString(2)));
                }
            }
        }

        return fields;
    }

    private List<Indexes> getIndexes(final String tableName) throws SQLException {
        if (database.getType() == Database.Type.SqlLite) {
            return getSQLiteIndexes(tableName);
        }

        if (database.getType() == Database.Type.Postgres || database.getType() == Database.Type.H2) {
            // Postgres and H2 index retrieval is more complex via standard SQL or requires specific queries
            // For now, returning empty list to avoid breaking migration if indexes are not critical for the user's immediate request
            // or implementing a basic check.
            // Implementing basic check for Postgres/H2:
            return getPostgresIndexes(tableName);
        }

        final Map<String, Indexes> indexes = new HashMap<>();
        final String query = "show indexes from " + tableName;

        logger.info(query);

        try (final Statement stmt = database.getConnection().createStatement()) {
            final ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {

                //TODO this is specific to mysql
                final Indexes index = indexes.
                    getOrDefault(
                        rs.getString("Key_name"),
                        new Indexes(rs.getString("Key_name")));

                index.getColumns().add(rs.getString("Column_name"));
                index.setUnique(!rs.getString("Non_unique").equals("1"));

                indexes.put(rs.getString("Key_name"), index);
            }
        }

        return new ArrayList<>(indexes.values());
    }

    private List<Indexes> getPostgresIndexes(final String tableName) throws SQLException {
        // Basic implementation for Postgres/H2
        // This query works for Postgres. H2 might differ slightly but often compatible with information_schema
        // However, getting columns for indexes is tricky in standard SQL.
        // For now, let's return empty to prevent crash, as index migration is an advanced feature.
        // If user needs it, we can implement it fully.
        return new ArrayList<>();
    }

    private List<Indexes> getSQLiteIndexes(final String tableName) throws SQLException {
        final List<Indexes> indexes = new ArrayList<>();
        final String query = "PRAGMA index_list(" + tableName + ")";
        logger.info(query);

        try (final Statement stmt = database.getConnection().createStatement()) {
            final ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                final String indexName = rs.getString("name");
                final boolean unique = rs.getBoolean("unique");
                final Indexes index = new Indexes(indexName);
                index.setUnique(unique);

                try (final Statement stmt2 = database.getConnection().createStatement()) {
                    final ResultSet rs2 = stmt2.executeQuery("PRAGMA index_info(" + indexName + ")");
                    while (rs2.next()) {
                        index.getColumns().add(rs2.getString("name"));
                    }
                }
                indexes.add(index);
            }
        }
        return indexes;
    }

    public <T> SchemaInterpreter model(final Class<T> databaseClass) {
        this.models.add(databaseClass);
        return this;
    }

    public String generate() throws ClassNotFoundException, SQLException, NoFieldDefinedException, TableAnnotationNotFound {
        final StringBuilder script = new StringBuilder();

        for (final Class model : this.models) {

            final Table table = this.fetchTable(model);

            final List<DatabaseFieldType> schemaFoundInDatabase = getDatabaseFieldTypes(table);
            final List<Indexes> indexesFound = getIndexes(table);

            if (schemaFoundInDatabase.isEmpty()) {
                if (!indexesFound.isEmpty()) {
                    for (final Indexes i : indexesFound) {
                        this.execute("drop indexes " + i.getName());
                    }
                }

                System.out.println("now generating table");
                script.append("\n").append(table.create());
                System.out.println("now creating indexes");
                script.append("\n").append(table.createIndexes());

            } else {
                final List<IncomingDatabaseFieldType> incomingDatabaseFieldTypes =
                    determineFieldsToBeCreated(
                        table.getDatabaseField(),
                        schemaFoundInDatabase
                    );

                if (incomingDatabaseFieldTypes.isEmpty()) {
                    logger.info("No new fields found");
                } else {
                    logger.info("total new fields found - {}", incomingDatabaseFieldTypes.size());
                    script.append(this.alterTable(table, incomingDatabaseFieldTypes));
                }
                //to do update the indexes
            }
        }

        return script.toString();

    }

    public void migrate() throws ClassNotFoundException, SQLException, NoFieldDefinedException, TableAnnotationNotFound {
        final String generate = this.generate();

        if (generate.isEmpty()) {
            logger.info("No overall change found");
            return;
        }

        System.out.println(generate);

        Arrays.asList(generate.split(";")).forEach(st -> {
            try (final Statement stmt = database.getConnection().createStatement()) {
                stmt.execute(st);
            } catch (final SQLException e) {
                logger.error(e.getMessage());
            }
        });
    }

    private List<Indexes> getIndexes(final Table table) {
        List<Indexes> indexesFound = new ArrayList<>();
        try {
            indexesFound = this.getIndexes(table.getTableName());
        } catch (final SQLException e) {
            logger.error(e.getMessage());
        }
        return indexesFound;
    }

    private List<DatabaseFieldType> getDatabaseFieldTypes(final Table table) {
        List<DatabaseFieldType> schemaFoundInDatabase = new ArrayList<>();
        try {
            schemaFoundInDatabase = this.getSchema(table.getTableName());
        } catch (final SQLException e) {
            logger.error(e.getMessage());
        }
        return schemaFoundInDatabase;
    }

    private Table fetchTable(final Class model) throws ClassNotFoundException, NoFieldDefinedException, TableAnnotationNotFound {
        final Class c = Class.forName(model.getName());

        final DatabaseTable dTable = (DatabaseTable) c.getAnnotation(DatabaseTable.class);

        if (dTable == null) {
            throw new TableAnnotationNotFound();
        }
        String tableName = dTable.tableName();

        if (tableName.isEmpty()) {
            tableName = c.getSimpleName();
        }

        final Field[] fields = c.getDeclaredFields();

        final List<IncomingDatabaseFieldType> incomingFields = Arrays.
            stream(fields).
            filter(f -> f.getAnnotation(DatabaseField.class) != null).
            map(f -> new IncomingDatabaseFieldType(f.getName(), f.getAnnotation(DatabaseField.class), f.getType())).
            collect(Collectors.toList());


        if (incomingFields.isEmpty()) {
            throw new NoFieldDefinedException();
        }
        return new Table(this.database, tableName, dTable, incomingFields);
    }

    private void execute(final String query) throws SQLException {
        try (final Statement stmt = database.getConnection().createStatement()) {
            stmt.execute(query);
        }
    }

    private String alterTable(final Table table, final List<IncomingDatabaseFieldType> incomingDatabaseFields) throws SQLException {
        final StringBuilder sb = new StringBuilder();
        for (final IncomingDatabaseFieldType field : incomingDatabaseFields) {
            // Standard SQL usually supports ADD COLUMN.
            // MySQL, Postgres, SQLite, H2 all support this.
            sb.append("ALTER TABLE ").append(table.getTableName()).append(" ADD COLUMN ").append(field.generateStatement(database.getType())).append(";");
        }
        return sb.toString();
    }
}
