package org.esa.nest.db;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.Statement;

/**
 *
 */
public class ProductDao extends DAO {

    private PreparedStatement stmtSaveNewRecord;
    private PreparedStatement stmtUpdateExistingRecord;
    private PreparedStatement stmtGetAddress;
    private PreparedStatement stmtDeleteAddress;

    private static final String strCreateProductTable =
            "create table APP.PRODUCTS (" +
            "    ID          INTEGER NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)," +
            "    PATH        VARCHAR(255), " +
            "    MISSION     VARCHAR(30), " +
            "    PRODUCTTYPE VARCHAR(30) " +
            ")";

    private static final String strGetProduct =
            "SELECT * FROM APP.PRODUCTS " +
            "WHERE ID = ?";

    private static final String strSaveProduct =
            "INSERT INTO APP.PRODUCTS " +
            "   (PATH, MISSION, PRODUCTTYPE) " +
            "VALUES (?, ?, ?)";

    private static final String strGetListEntries =
            "SELECT ID, LASTNAME, FIRSTNAME, MIDDLENAME FROM APP.ADDRESS "  +
            "ORDER BY LASTNAME ASC";

    private static final String strUpdateProduct =
            "UPDATE APP.PRODUCTS " +
            "SET PATH = ?, " +
            "    MISSION = ?, " +
            "    PRODUCTTYPE = ? " +
            "WHERE ID = ?";

    private static final String strDeleteProduct =
            "DELETE FROM APP.PRODUCTS " +
            "WHERE ID = ?";

    public ProductDao() throws IOException {
        this("ProductDatabase");
    }
    
    public ProductDao(final String name) throws IOException {
        super(name);
    }
    
    protected boolean createTables(final Connection dbConnection) {
        boolean bCreatedTables = false;
        try {
            final Statement statement = dbConnection.createStatement();
            statement.execute(strCreateProductTable);
            bCreatedTables = true;
        } catch (SQLException ex) {
            ex.printStackTrace();
            lastSQLException = ex;
        }
        return bCreatedTables;
    }

    protected void prepareStatements() throws SQLException {
        stmtSaveNewRecord = getDBConnection().prepareStatement(strSaveProduct, Statement.RETURN_GENERATED_KEYS);
        stmtUpdateExistingRecord = getDBConnection().prepareStatement(strUpdateProduct);
        stmtGetAddress = getDBConnection().prepareStatement(strGetProduct);
        stmtDeleteAddress = getDBConnection().prepareStatement(strDeleteProduct);
    }
    
    public int saveRecord(final ProductEntry record) throws SQLException {
        int id = -1;
        stmtSaveNewRecord.clearParameters();

        stmtSaveNewRecord.setString(1, record.getPath());
        stmtSaveNewRecord.setString(2, record.getMission());
        stmtSaveNewRecord.setString(3, record.getProductType());
        final int rowCount = stmtSaveNewRecord.executeUpdate();
        final ResultSet results = stmtSaveNewRecord.getGeneratedKeys();
        if (results.next()) {
            id = results.getInt(1);
            record.setId(id);
        }
        return id;
    }
    
    public void editRecord(final ProductEntry record) throws SQLException {
        stmtUpdateExistingRecord.clearParameters();

        stmtUpdateExistingRecord.setString(1, record.getPath());
        stmtUpdateExistingRecord.setInt(12, record.getId());
        stmtUpdateExistingRecord.executeUpdate();
    }
    
    public void deleteRecord(final int id) throws SQLException {
        stmtDeleteAddress.clearParameters();
        stmtDeleteAddress.setInt(1, id);
        stmtDeleteAddress.executeUpdate();
    }
    
    public void deleteRecord(final ProductEntry record) throws SQLException {
        deleteRecord(record.getId());
    }
    
   /* public List<ListEntry> getListEntries() {
        List<ListEntry> listEntries = new ArrayList<ListEntry>();
        Statement queryStatement = null;
        ResultSet results = null;
        
        try {
            queryStatement = dbConnection.createStatement();
            results = queryStatement.executeQuery(strGetListEntries);
            while(results.next()) {
                int id = results.getInt(1);
                String lName = results.getString(2);
                String fName = results.getString(3);
                String mName = results.getString(4);
                
                ListEntry entry = new ListEntry(lName, fName, mName, id);
                listEntries.add(entry);
            }
            
        } catch (SQLException sqle) {
            sqle.printStackTrace();
            
        }
        
        return listEntries;
    }    */
    
    public ProductEntry getProductEntry(final int index) {
        ProductEntry entry = null;
        try {
            stmtGetAddress.clearParameters();
            stmtGetAddress.setInt(1, index);
            final ResultSet result = stmtGetAddress.executeQuery();
            if (result.next()) {
                final String path = result.getString("PATH");
                final int id = result.getInt("ID");
                entry = new ProductEntry(path, id);
            }
        } catch(SQLException sqle) {
            sqle.printStackTrace();
        }
        return entry;
    }

}
