package org.esa.nest.db;

import org.esa.beam.framework.datamodel.Product;

import java.io.IOException;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;

/**
 *
 */
public class ProductDB extends DAO {

    private ProductTable productTable;
    private MetadataTable metadataTable;

    private static ProductDB _instance = null;

    public static ProductDB instance() throws IOException {
        if(_instance == null)
            _instance = new ProductDB();
        return _instance;
    }

    private ProductDB() throws IOException {
        super("productDB");
    }

    @Override
    protected boolean createTables(final Connection dbConnection) throws SQLException {
        productTable = new ProductTable();
        productTable.createTable(dbConnection);
        metadataTable = new MetadataTable();
        metadataTable.createTable(dbConnection);
        return true;
    }

    @Override
    protected void validateTables(final Connection dbConnection) throws SQLException {
        if(productTable == null)
            productTable = new ProductTable();
        if(metadataTable == null)
            metadataTable = new MetadataTable();
        productTable.validateTable(dbConnection);
        metadataTable.validateTable(dbConnection);
    }

    protected void prepareStatements() throws SQLException {
        final Connection connection = getDBConnection();
        productTable.prepareStatements(connection);
        metadataTable.prepareStatements(connection);
    }

    public boolean pathExistsInDB(final File path) throws SQLException {
        return productTable.pathExists(path);
    }

    public void saveProduct(final Product product) throws SQLException {
        final ProductEntry newEntry = new ProductEntry(product);

        if(productTable.pathExists(newEntry.getFile())) {
            // update
        } else {
            addRecord(newEntry);
        }
        newEntry.dispose();
    }

    private void addRecord(final ProductEntry record) throws SQLException {

        final ResultSet results = productTable.addRecord(record);
        if (results.next()) {
            final int id = results.getInt(1);
            record.setId(id);

            metadataTable.addRecord(record);
        }
    }
    
   /* public void editRecord(final ProductEntry record) throws SQLException {
        stmtUpdateExistingRecord.clearParameters();

        stmtUpdateExistingRecord.setString(1, record.getFile());
        stmtUpdateExistingRecord.setInt(12, record.getId());
        stmtUpdateExistingRecord.executeUpdate();
    } */  
    
   /* public void deleteRecord(final int id) throws SQLException {
        stmtDeleteAddress.clearParameters();
        stmtDeleteAddress.setInt(1, id);
        stmtDeleteAddress.executeUpdate();
    }
    
    public void deleteRecord(final ProductEntry record) throws SQLException {
        deleteRecord(record.getId());
    }     */
    
    public ProductEntry[] getProductEntryList() throws SQLException {
        return productTable.getProductEntryList(getDBConnection());
    }

    public ProductEntry[] queryProduct(final String queryStr) throws SQLException {
        return productTable.query(getDBConnection(), queryStr);
    }
    
  /*  public ProductEntry getProductEntry(final int index) {
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
    }      */

    public String[] getAllMissions() throws SQLException {
        return productTable.getAllMissions();
    }

    public String[] getAllProductTypes() throws SQLException {
        return productTable.getAllProductTypes();
    }

    public String[] getProductTypes(final String mission) throws SQLException {
        return productTable.getProductTypes(mission);
    }
}
