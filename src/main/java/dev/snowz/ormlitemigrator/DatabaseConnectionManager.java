package dev.snowz.ormlitemigrator;

import dev.snowz.ormlitemigrator.exception.ConnectionStringException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DatabaseConnectionManager {

    public static Database withConnection(final String connectionString) throws ConnectionStringException, ClassNotFoundException, SQLException {
        final String[] split = connectionString.split(":");

        if (split.length > 1) {
            if (split[0].equals("jdbc") && split[1].equals("mysql")) {
                Class.forName(Database.MySQLDriver);
                final Connection connection = DriverManager
                    .getConnection(connectionString);

                return new Database(Database.Type.MySQL, Database.MySQLDriver, connection, connectionString);
            } else if (split[0].equals("jdbc") && split[1].equals("sqlite")) {
                Class.forName(Database.SQLiteDriver);
                final Connection connection = DriverManager
                    .getConnection(connectionString);

                return new Database(Database.Type.SqlLite, Database.SQLiteDriver, connection, connectionString);
            } else if (split[0].equals("jdbc") && split[1].equals("postgresql")) {
                Class.forName(Database.PostgresDriver);
                final Connection connection = DriverManager
                    .getConnection(connectionString);

                return new Database(Database.Type.Postgres, Database.PostgresDriver, connection, connectionString);
            } else if (split[0].equals("jdbc") && split[1].equals("mariadb")) {
                Class.forName(Database.MariaDBDriver);
                final Connection connection = DriverManager
                    .getConnection(connectionString);

                return new Database(Database.Type.MariaDB, Database.MariaDBDriver, connection, connectionString);
            } else if (split[0].equals("jdbc") && split[1].equals("h2")) {
                Class.forName(Database.H2Driver);
                final Connection connection = DriverManager
                    .getConnection(connectionString);

                return new Database(Database.Type.H2, Database.H2Driver, connection, connectionString);
            }
        }

        throw new ConnectionStringException(connectionString + " is not valid");
    }


    static List<IncomingDatabaseFieldType> determineFieldsToBeCreated(final List<IncomingDatabaseFieldType> incomingDatabaseFields, final List<DatabaseFieldType> existingFields) {
        final List<IncomingDatabaseFieldType> fieldsToBeCreated = new ArrayList<>();
        for (final IncomingDatabaseFieldType field : incomingDatabaseFields) {
            if (field.getDatabaseField() != null) {
                if (!in(field, existingFields)) {
                    fieldsToBeCreated.add(field);
                }
            }
        }

        return fieldsToBeCreated;
    }

    private static boolean in(final IncomingDatabaseFieldType field, final List<DatabaseFieldType> existingFields) {
        return existingFields.stream().anyMatch(f -> f.getFieldName().equalsIgnoreCase(field.getFieldName()));
    }
}
