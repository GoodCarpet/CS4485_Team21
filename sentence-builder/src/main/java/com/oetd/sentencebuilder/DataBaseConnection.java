package com.oetd.sentencebuilder;
import java.sql.*;


// Aniq Sunesara
// Class made to handle all Database related operations
public class DataBaseConnection
{
    public Connection dbconn;
    private String url;
    private String user;
    private String password;

    // constructor that sets up the connection to the SQL server
    public DataBaseConnection() throws SQLException {
        url = "jdbc:mysql://mysql-3316a945-sentence-builder-db.k.aivencloud.com:27131/sentence_builder?sslmode=require";
        user = "avnadmin";
        // be sure to add the DB password as an environment variable with the title "SB_DB_PASSWORD"
        password = System.getenv("SB_DB_PASSWORD");
        if(password == null){
            throw new IllegalStateException("SB_DB_PASSWORD came back null");
        }
        try {
            dbconn = DriverManager.getConnection(url, user, password);
            System.out.println("db connection success");

        } catch (SQLException e){
            System.out.println("db connection failed: " + e.getMessage());
        }
    }

    public ResultSet askQuery(String stmt){
        try {
            return  dbconn.prepareStatement(stmt).executeQuery();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int updateStatement(String stmt){
        try {
            return  dbconn.prepareStatement(stmt).executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

