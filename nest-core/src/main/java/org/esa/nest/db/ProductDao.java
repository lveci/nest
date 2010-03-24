package org.esa.nest.db;

import org.esa.beam.framework.datamodel.Product;

import java.io.IOException;
import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;

/**
 *
 */
public class ProductDao extends DAO {

    private PreparedStatement stmtSaveNewRecord;
    private PreparedStatement stmtUpdateExistingRecord;
    private PreparedStatement stmtGetAddress;
    private PreparedStatement stmtGetProductWithPath;
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
            "SELECT ID, PATH, MISSION, PRODUCTTYPE FROM APP.PRODUCTS "  +
            "ORDER BY MISSION ASC";

    private static final String strGetProductWithPath =
            "SELECT ID FROM APP.PRODUCTS " +
            "WHERE PATH = ?";

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
        try {
            stmtSaveNewRecord = getDBConnection().prepareStatement(strSaveProduct, Statement.RETURN_GENERATED_KEYS);
            stmtUpdateExistingRecord = getDBConnection().prepareStatement(strUpdateProduct);
            stmtGetAddress = getDBConnection().prepareStatement(strGetProduct);
            stmtGetProductWithPath = getDBConnection().prepareStatement(strGetProductWithPath);
            stmtDeleteAddress = getDBConnection().prepareStatement(strDeleteProduct);
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw ex;
        }
    }

    public boolean pathExistsInDB(final File file) throws SQLException {
        stmtGetProductWithPath.clearParameters();
        stmtGetProductWithPath.setString(1, file.getAbsolutePath());
        final ResultSet results = stmtGetProductWithPath.executeQuery();
        return results.next();
    }

    public void saveProduct(final Product product) throws SQLException {
        final ProductEntry newEntry = new ProductEntry(product);

        stmtGetProductWithPath.clearParameters();
        stmtGetProductWithPath.setString(1, newEntry.getPath());
        final ResultSet results = stmtGetProductWithPath.executeQuery();
        if(results.next()) {
            // update
        } else {
            addRecord(newEntry);
        }
    }

    private void addRecord(final ProductEntry record) throws SQLException {

        stmtSaveNewRecord.clearParameters();
        stmtSaveNewRecord.setString(1, record.getPath());
        stmtSaveNewRecord.setString(2, record.getMission());
        stmtSaveNewRecord.setString(3, record.getProductType());
        final int rowCount = stmtSaveNewRecord.executeUpdate();
        final ResultSet results = stmtSaveNewRecord.getGeneratedKeys();
        if (results.next()) {
            final int id = results.getInt(1);
            record.setId(id);
        }
    }
    
   /* public void editRecord(final ProductEntry record) throws SQLException {
        stmtUpdateExistingRecord.clearParameters();

        stmtUpdateExistingRecord.setString(1, record.getPath());
        stmtUpdateExistingRecord.setInt(12, record.getId());
        stmtUpdateExistingRecord.executeUpdate();
    } */  
    
    public void deleteRecord(final int id) throws SQLException {
        stmtDeleteAddress.clearParameters();
        stmtDeleteAddress.setInt(1, id);
        stmtDeleteAddress.executeUpdate();
    }
    
    public void deleteRecord(final ProductEntry record) throws SQLException {
        deleteRecord(record.getId());
    }
    
    public ProductEntry[] getProductEntryList() {
        final ArrayList<ProductEntry> listEntries = new ArrayList<ProductEntry>();

        try {
            final Statement queryStatement = getDBConnection().createStatement();
            final ResultSet results = queryStatement.executeQuery(strGetListEntries);
            while(results.next()) {
                final int id = results.getInt(1);
                final String path = results.getString(2);
                final String mission = results.getString(3);
                final String productType = results.getString(4);
                
                final ProductEntry entry = new ProductEntry(id, path, mission, productType);
                listEntries.add(entry);
            }
            
        } catch (SQLException ex) {
            ex.printStackTrace();
            lastSQLException = ex;
        }
        return listEntries.toArray(new ProductEntry[listEntries.size()]);
    }
    
    public ProductEntry getProductEntry(final int index) {
        ProductEntry entry = null;
        try {
            stmtGetAddress.clearParameters();
            stmtGetAddress.setInt(1, index);
            final ResultSet result = stmtGetAddress.executeQuery();
            if (result.next()) {
                entry = new ProductEntry(result.getInt("ID"), result.getString("PATH"));
            }
        } catch(SQLException sqle) {
            sqle.printStackTrace();
        }
        return entry;
    }

}
