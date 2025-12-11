package dev.snowz.ormlitemigrator;

import com.j256.ormlite.table.DatabaseTable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Table {
    private final Database database;
    private final String tableName;
    private final DatabaseTable databaseTable;
    private final List<IncomingDatabaseFieldType> databaseField;

    public Table(final Database database, final String tableName, final DatabaseTable databaseTable, final List<IncomingDatabaseFieldType> databaseField) {
        this.database = database;
        this.tableName = tableName;
        this.databaseTable = databaseTable;
        this.databaseField = databaseField;
    }

    public List<IncomingDatabaseFieldType> getDatabaseField() {
        return databaseField;
    }

    public DatabaseTable getDatabaseTable() {
        return databaseTable;
    }

    public String getTableName() {
        return tableName;
    }

    public String create() {
        final List<String> collect = this.databaseField.stream().map(f -> f.generateStatement(database.getType())).collect(Collectors.toList());

        final List<IncomingDatabaseFieldType> primaryKeys = this.databaseField.stream().filter(i -> {
            if (i.getDatabaseField() != null) {
                return i.getDatabaseField().id() || i.getDatabaseField().generatedId();
            }
            return false;
        }).collect(Collectors.toList());

        String primaryKey = "";

        if (!primaryKeys.isEmpty()) {
            final List<String> pk = primaryKeys.stream().map(IncomingDatabaseFieldType::getFieldName).collect(Collectors.toList());
            primaryKey = ", PRIMARY KEY(" + String.join(",", pk) + ")";

        }

        return "CREATE TABLE IF NOT EXISTS " + this.tableName + " (" +
            String.join(",", collect) + " " + primaryKey +
            ");";
    }

    public String createIndexes() {
        String script = "";
        //Filter index with one field
        script = script + createIndexWithOneField();

        //unique indexes with one field
        script = script + createUniqueIndex();

        //composite indexes
        script = script + createCompositeIndex();

        //composite unqiue indexes
        script = script + createCompositeUniqueIndexes();

        return script;

    }

    private String createCompositeUniqueIndexes() {
        final Map<String, List<IncomingDatabaseFieldType>> compositeUniqueIndexes = this.databaseField.
            stream().
            filter(i -> i.getDatabaseField() != null && !i.getDatabaseField().uniqueIndexName().isEmpty()).
            collect(Collectors.groupingBy(o -> o.getDatabaseField().uniqueIndexName()));

        return compositeUniqueIndexes.keySet().stream().map(p -> {
            final List<IncomingDatabaseFieldType> compositeDatabaseFieldTypes = compositeUniqueIndexes.get(p);

            final List<String> finalUniqueIndexList = compositeDatabaseFieldTypes.stream().
                map(IncomingDatabaseFieldType::getFieldName).collect(Collectors.toList());

            return "CREATE UNIQUE INDEX " + p + " ON " + this.tableName + "(" + String.join(",", finalUniqueIndexList) + ");";
        }).collect(Collectors.joining());
    }

    private String createCompositeIndex() {
        final Map<String, List<IncomingDatabaseFieldType>> collect = this.databaseField.
            stream().
            filter(i -> i.getDatabaseField() != null && !i.getDatabaseField().indexName().isEmpty()).
            collect(Collectors.groupingBy(o -> o.getDatabaseField().indexName()));

        return collect.keySet().stream().map(k -> {
            final List<IncomingDatabaseFieldType> incomingDatabaseFieldTypes = collect.get(k);

            final List<String> finalList = incomingDatabaseFieldTypes.stream().
                map(IncomingDatabaseFieldType::getFieldName).collect(Collectors.toList());

            return "CREATE INDEX " + k + " ON " + this.tableName + "(" + String.join(",", finalList) + ");";
        }).collect(Collectors.joining());
    }

    private String createUniqueIndex() {
        return this.databaseField.stream().
            filter(i -> i.getDatabaseField() != null).
            filter(i -> i.getDatabaseField().unique()).
            map(i -> "CREATE UNIQUE INDEX " + i.getFieldName() + "_idx ON " + this.tableName + "(" + i.getFieldName() + ");").
            collect(Collectors.joining());
    }

    private String createIndexWithOneField() {
        return this.databaseField.stream().
            filter(i -> i.getDatabaseField() != null).
            filter(i -> i.getDatabaseField().index()).
            map(i -> "CREATE INDEX " + i.getFieldName() + "_idx ON " + this.tableName + "(" + i.getFieldName() + ");").
            collect(Collectors.joining());
    }

    public Database getDatabase() {
        return database;
    }
}
