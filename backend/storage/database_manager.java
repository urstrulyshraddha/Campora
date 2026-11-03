package backend.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class database_manager {
    // Local fallback defaults
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/campora_db?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASS = "1234";

    public static Connection getConnection() throws SQLException {
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USER");
        String dbPass = System.getenv("DB_PASS");

        if (dbUrl == null || dbUrl.trim().isEmpty()) {
            dbUrl = DEFAULT_URL;
            dbUser = DEFAULT_USER;
            dbPass = DEFAULT_PASS;
        } else {
            dbUrl = dbUrl.trim();
            // Automatically normalize if mysql:// was passed instead of jdbc:mysql://
            if (dbUrl.startsWith("mysql://")) {
                dbUrl = "jdbc:" + dbUrl;
            }
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[JDBC Warning] Driver not found: " + e.getMessage());
        }

        return DriverManager.getConnection(dbUrl, dbUser, dbPass);
    }
}
