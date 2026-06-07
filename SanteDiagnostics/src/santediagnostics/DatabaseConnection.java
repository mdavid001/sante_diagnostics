package santediagnostics;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
  The expected database name is "sante_lims".
  Insert your own database password before running 
  Ensure the database is created with the schema i will upload on the group chat
 **/
public class DatabaseConnection {

    private static final String URL = "jdbc:postgresql://localhost:5432/sante_lims?stringtype=unspecified";
    private static final String USER = "postgres";
    private static final String PASSWORD = "ada";

    public static Connection getConnect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
