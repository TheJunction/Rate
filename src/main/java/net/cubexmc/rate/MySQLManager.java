package net.cubexmc.rate;

import code.husky.mysql.MySQL;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Created by David on 7/9/2015.
 *
 * @author David
 */
public class MySQLManager {
    private final Main main;
    private MySQL db;

    public MySQLManager(Main h) {
        this.main = h;
    }

    public void setupDB() throws SQLException, ClassNotFoundException {
        this.db = new MySQL(this.main, "cubexmc.net", "3306", "CubeXMC", "david", "DavidShen");
        this.db.openConnection();
    }

    public void closeDB() throws SQLException {
        this.db.closeConnection();
    }

    public void checkConnection() {
        try {
            if (!this.db.checkConnection()) this.db.openConnection();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Connection getDB() {
        return this.db.getConnection();
    }
}
