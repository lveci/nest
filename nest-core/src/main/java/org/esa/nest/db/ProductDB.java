package org.esa.nest.db;

import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 *
 */
public class ProductDB extends DAO {

    private ProductTable productTable;
    private MetadataTable metadataTable;

    private static ProductDB _instance = null;

    public static final String DEFAULT_PRODUCT_DATABASE_NAME = "productDB";

    public static ProductDB instance() throws IOException {
        if(_instance == null)
            _instance = new ProductDB();
        return _instance;
    }

    public static void deleteInstance() {
        _instance = null;
    }

    private ProductDB() throws IOException {
        super(DEFAULT_PRODUCT_DATABASE_NAME);
    }

    @Override
    protected boolean createTables(final Connection dbConnection) throws SQLException {
        productTable = new ProductTable(dbConnection);
        productTable.createTable();
        metadataTable = new MetadataTable(dbConnection);
        metadataTable.createTable();
        return true;
    }

    @Override
    protected void validateTables(final Connection dbConnection) throws SQLException {
        if(productTable == null)
            productTable = new ProductTable(dbConnection);
        if(metadataTable == null)
            metadataTable = new MetadataTable(dbConnection);
        productTable.validateTable();
        metadataTable.validateTable();
    }

    protected void prepareStatements() throws SQLException {
        //final Connection connection = getDBConnection();
        productTable.prepareStatements();
        metadataTable.prepareStatements();
    }

    public boolean pathExistsInDB(final File path) throws SQLException {
        return productTable.pathExists(path);
    }

    public ProductEntry saveProduct(final Product product) throws SQLException {
        final ProductEntry newEntry = new ProductEntry(product);

        if(productTable.pathExists(newEntry.getFile())) {
            // update
        } else {
            addRecord(newEntry);
        }
        return newEntry;
    }

    private void addRecord(final ProductEntry record) throws SQLException {

        final ResultSet results = productTable.addRecord(record);
        if (results.next()) {
            final int id = results.getInt(1);
            record.setId(id);

            metadataTable.addRecord(record);
        }
    }

    public void removeProducts(final File baseDir) throws SQLException {
        final String queryStr = AbstractMetadata.PATH+" LIKE '"+baseDir.getAbsolutePath()+"%'";
        final ProductEntry[] list = queryProduct(queryStr);
        for(ProductEntry entry : list) {
            productTable.deleteRecord(entry.getId());
        }
    }

    public ProductEntry[] getProductEntryList() throws SQLException {
        return productTable.getProductEntryList();
    }

    public ProductEntry[] queryProduct(final String queryStr) throws SQLException {
        return productTable.query(queryStr);
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

    public String[] getProductTypes(final String[] missions) throws SQLException {
        return productTable.getProductTypes(missions);
    }
}
