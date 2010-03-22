package org.esa.nest.db;

import org.esa.nest.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Connection;
import java.util.Properties;

/**
 *  Base Data Access Object
 */
public abstract class DAO {

    private Connection dbConnection;
    private boolean isConnected;
    private final Properties dbProperties;
    private final String dbName;
    protected SQLException lastSQLException = null;

    public DAO(final String name) throws IOException {
        this.dbName = name;

        setDBSystemDir();
        final File dbPropFile = ResourceUtils.findConfigFile(dbName+".properties");
        if(dbPropFile == null) {
            throw new IOException(dbName+".properties does not exist");
        }
        dbProperties = ResourceUtils.loadProperties(dbPropFile.getAbsolutePath());
        loadDatabaseDriver(dbProperties.getProperty("derby.driver"));
        if(!dbExists()) {
            if(!createDatabase()) {
                throw new IOException("Unable to create tables\n"+getLastSQLException().getMessage());
            }
        }
    }

    private boolean dbExists() {
        boolean bExists = false;
        final String dbLocation = getDatabaseLocation();
        final File dbFileDir = new File(dbLocation);
        if (dbFileDir.exists()) {
            bExists = true;
        }
        return bExists;
    }

    private void setDBSystemDir() {
        // decide on the db system directory
        final File userAppDir = ResourceUtils.getApplicationUserDir(true);
        final String systemDir = userAppDir.getAbsolutePath() +File.separator+ dbName;
        System.setProperty("derby.system.home", systemDir);

        // create the db system directory
        final File fileSystemDir = new File(systemDir);
        fileSystemDir.mkdir();
    }

    private void loadDatabaseDriver(final String driverName) {
        // load Derby driver
        try {
            Class.forName(driverName);
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
        }
    }

    protected abstract boolean createTables(final Connection dbConnection);

    protected abstract void prepareStatements() throws SQLException;

    private boolean createDatabase() {
        boolean bCreated = false;
        dbProperties.put("create", "true");

        try {
            final Connection dbConnection = DriverManager.getConnection(getDatabaseUrl(), dbProperties);
            bCreated = createTables(dbConnection);
        } catch (SQLException ex) {
            ex.printStackTrace();
            lastSQLException = ex;
        }
        dbProperties.remove("create");
        return bCreated;
    }

    public boolean connect() {
        final String dbUrl = getDatabaseUrl();
        try {
            dbConnection = DriverManager.getConnection(dbUrl, dbProperties);
            prepareStatements();

            isConnected = dbConnection != null;
        } catch (SQLException ex) {
            isConnected = false;
            lastSQLException = ex;
        }
        return isConnected;
    }

    public void disconnect() {
        if(isConnected) {
            dbProperties.put("shutdown", "true");
            try {
                DriverManager.getConnection(getDatabaseUrl(), dbProperties);
            } catch (SQLException ex) {
                lastSQLException = ex;
            }
            isConnected = false;
        }
    }

    public SQLException getLastSQLException() {
        return lastSQLException;
    }

    public Connection getDBConnection() {
        return dbConnection;
    }

    public String getDatabaseLocation() {
        return System.getProperty("derby.system.home") + File.separator + dbName;
    }

    public String getDatabaseUrl() {
        return dbProperties.getProperty("derby.url") + dbName;
    }

}