package dev.snowz.ormlitemigrator;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import dev.snowz.ormlitemigrator.exception.ConnectionStringException;
import dev.snowz.ormlitemigrator.exception.NoFieldDefinedException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.sql.*;

import static dev.snowz.ormlitemigrator.DatabaseConnectionManager.withConnection;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SQLiteMigrationTest {

    private static final String DB_FILE = "test.db";
    private static final String CONNECTION_STRING = "jdbc:sqlite:" + DB_FILE;

    @Before
    public void setUp() {
        // Ensure clean state
        final File dbFile = new File(DB_FILE);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @After
    public void tearDown() {
        final File dbFile = new File(DB_FILE);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @DatabaseTable(tableName = "users")
    public static class User {
        @DatabaseField(generatedId = true)
        private int id;

        @DatabaseField
        private String name;
    }

    @DatabaseTable(tableName = "users")
    public static class UserV2 {
        @DatabaseField(generatedId = true)
        private int id;

        @DatabaseField
        private String name;

        @DatabaseField
        private String email;
    }

    @Test
    public void testMigration() throws SQLException, ConnectionStringException, ClassNotFoundException, NoFieldDefinedException, TableAnnotationNotFound {
        // 1. Initial Migration (Create Table)
        SchemaInterpreter schemaInterpreter = new SchemaInterpreter(withConnection(CONNECTION_STRING));
        schemaInterpreter.model(User.class);
        schemaInterpreter.migrate();

        // Verify table created
        assertTrue(tableExists("users"));
        assertTrue(columnExists("users", "name"));
        assertFalse(columnExists("users", "email"));

        // 2. Second Migration (Add Column)
        schemaInterpreter = new SchemaInterpreter(withConnection(CONNECTION_STRING));
        schemaInterpreter.model(UserV2.class);
        schemaInterpreter.migrate();

        // Verify column added
        assertTrue(tableExists("users"));
        assertTrue(columnExists("users", "name"));
        assertTrue(columnExists("users", "email"));
    }

    private boolean tableExists(final String tableName) throws SQLException {
        try (final Connection conn = DriverManager.getConnection(CONNECTION_STRING);
             final Statement stmt = conn.createStatement();
             final ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "'")) {
            return rs.next();
        }
    }

    private boolean columnExists(final String tableName, final String columnName) throws SQLException {
        try (final Connection conn = DriverManager.getConnection(CONNECTION_STRING);
             final Statement stmt = conn.createStatement();
             final ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (rs.getString("name").equals(columnName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
