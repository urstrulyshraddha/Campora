package backend.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class database_manager {
    // Standard MySQL JDBC connection URL for localhost on default port 3306
    private static final String DB_URL = "jdbc:mysql://localhost:3306/campora_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = ""; // Empty password set during --initialize-insecure

    static {
        try {
            // Explicitly load the MySQL JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[Database Error] MySQL Driver not found in classpath: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}
