package dev.snowz.ormlitemigrator;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import org.junit.Test;

import java.sql.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MultiDbMigrationTest {

    @DatabaseTable(tableName = "test_users")
    public static class User {
        @DatabaseField(generatedId = true)
        private int id;

        @DatabaseField
        private String name;
    }

    @DatabaseTable(tableName = "test_users")
    public static class UserV2 {
        @DatabaseField(generatedId = true)
        private int id;

        @DatabaseField
        private String name;

        @DatabaseField
        private String email;
    }

    @Test
    public void testH2Migration() throws Throwable {
        final String connectionString = "jdbc:h2:mem:test_h2;DB_CLOSE_DELAY=-1";
        final Connection connection = DriverManager.getConnection(connectionString);
        final Database database = new Database(Database.Type.H2, Database.H2Driver, connection, connectionString);

        runMigrationTest(database, connection, "TEST_USERS"); // H2 defaults to uppercase
    }

    @Test
    public void testMySQLMigration() throws Throwable {
        // Using H2 in MySQL mode
        final String connectionString = "jdbc:h2:mem:test_mysql;MODE=MySQL;DB_CLOSE_DELAY=-1";
        final Connection connection = DriverManager.getConnection(connectionString);
        final Database database = new Database(Database.Type.MySQL, "org.h2.Driver", connection, connectionString);

        runMigrationTest(database, connection, "TEST_USERS"); // H2 in MySQL mode still seems to uppercase by default unless configured otherwise
    }

    @Test
    public void testMariaDBMigration() throws Throwable {
        // Using H2 in MySQL mode (MariaDB compatible)
        final String connectionString = "jdbc:h2:mem:test_mariadb;MODE=MySQL;DB_CLOSE_DELAY=-1";
        final Connection connection = DriverManager.getConnection(connectionString);
        final Database database = new Database(Database.Type.MariaDB, "org.h2.Driver", connection, connectionString);

        runMigrationTest(database, connection, "TEST_USERS");
    }

    @Test
    public void testPostgresMigration() throws Throwable {
        // Using H2 in PostgreSQL mode
        final String connectionString = "jdbc:h2:mem:test_pg;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        final Connection connection = DriverManager.getConnection(connectionString);
        final Database database = new Database(Database.Type.Postgres, "org.h2.Driver", connection, connectionString);

        // In Postgres mode, H2 might still uppercase unless we quote, but let's see.
        // SchemaInterpreter uses toLowerCase() for Postgres.
        // If H2 creates USERS, SchemaInterpreter looks for users.
        // We might need to adjust expectation or SchemaInterpreter for this test.
        // Let's try running it. If it fails finding the table, we know why.
        // Actually, H2 in Postgres mode with DATABASE_TO_LOWER=TRUE might help, but let's try standard first.

        runMigrationTest(database, connection, "test_users");
    }

    private void runMigrationTest(final Database database, final Connection connection, final String tableNameToCheck) throws Throwable {
        // 1. Initial Migration
        SchemaInterpreter schemaInterpreter = new SchemaInterpreter(database);
        schemaInterpreter.model(User.class);
        schemaInterpreter.migrate();

        assertTrue("Table should exist", tableExists(connection, tableNameToCheck));
        assertTrue("Column name should exist", columnExists(connection, tableNameToCheck, "NAME") || columnExists(connection, tableNameToCheck, "name"));
        assertFalse("Column email should not exist", columnExists(connection, tableNameToCheck, "EMAIL") || columnExists(connection, tableNameToCheck, "email"));

        // 2. Second Migration
        schemaInterpreter = new SchemaInterpreter(database);
        schemaInterpreter.model(UserV2.class);
        schemaInterpreter.migrate();

        assertTrue("Column email should exist", columnExists(connection, tableNameToCheck, "EMAIL") || columnExists(connection, tableNameToCheck, "email"));

        connection.close();
    }

    private boolean tableExists(final Connection conn, final String tableName) throws SQLException {
        try (final Statement stmt = conn.createStatement();
             final ResultSet rs = stmt.executeQuery("SELECT count(*) FROM information_schema.tables WHERE UPPER(table_name) = '" + tableName.toUpperCase() + "'")) {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    private boolean columnExists(final Connection conn, final String tableName, final String columnName) throws SQLException {
        try (final Statement stmt = conn.createStatement();
             final ResultSet rs = stmt.executeQuery("SELECT count(*) FROM information_schema.columns WHERE UPPER(table_name) = '" + tableName.toUpperCase() + "' AND UPPER(column_name) = '" + columnName.toUpperCase() + "'")) {
            if (rs.next()) {
                final boolean exists = rs.getInt(1) > 0;
                if (!exists) {
                    // Debugging
                    System.out.println("Column " + columnName + " not found in " + tableName + ". Available columns:");
                    try (final ResultSet rs2 = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE UPPER(table_name) = '" + tableName.toUpperCase() + "'")) {
                        while (rs2.next()) {
                            System.out.println("- " + rs2.getString(1));
                        }
                    }
                }
                return exists;
            }
        }
        return false;
    }
}
