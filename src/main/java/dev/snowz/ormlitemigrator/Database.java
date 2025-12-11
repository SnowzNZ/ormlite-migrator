package dev.snowz.ormlitemigrator;

import java.sql.Connection;

public class Database {

    public static final String MySQLDriver = "com.mysql.cj.jdbc.Driver";

    public static final String SQLiteDriver = "org.sqlite.JDBC";
    public static final String PostgresDriver = "org.postgresql.Driver";
    public static final String MariaDBDriver = "org.mariadb.jdbc.Driver";
    public static final String H2Driver = "org.h2.Driver";

    public String getConnectionString() {
        return connectionString;
    }

    enum Type {
        MySQL, Postgres, SqlLite, MariaDB, H2
    }

    public Type getType() {
        return type;
    }

    public String getDriver() {
        return driver;
    }

    public Connection getConnection() {
        return connection;
    }

    private final Type type;
    private final String driver;
    private final Connection connection;
    private final String connectionString;

    public Database(final Type type, final String driver, final Connection connection, final String connectionString) {
        this.type = type;
        this.driver = driver;
        this.connection = connection;
        this.connectionString = connectionString;
    }

    public SchemaInterpreter getSchemaManager() {
        return new SchemaInterpreter(this);
    }
}
