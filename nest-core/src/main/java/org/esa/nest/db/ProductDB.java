package org.esa.nest.db;

import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

/**
 *
 */
public class ProductDB extends DAO {

    private ProductTable productTable;
    private MetadataTable metadataTable;
    private Connection dbConnection = null;

    private static ProductDB _instance = null;
    public static final String DEFAULT_PRODUCT_DATABASE_NAME = "productDB";
    public static final String PROD_TABLE = "APP.PRODUCTS";
    public static final String META_TABLE = "APP.METADATA";
    
    private static final String strGetProductsWhere =
            "SELECT * FROM APP.PRODUCTS, APP.METADATA WHERE APP.PRODUCTS.ID = APP.METADATA.ID AND ";

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
    protected boolean createTables(final Connection connection) throws SQLException {
        this.dbConnection = connection;
        productTable = new ProductTable(dbConnection);
        productTable.createTable();
        metadataTable = new MetadataTable(dbConnection);
        metadataTable.createTable();
        return true;
    }

    @Override
    protected void validateTables(final Connection connection) throws SQLException {
        this.dbConnection = connection;
        if(productTable == null)
            productTable = new ProductTable(dbConnection);
        if(metadataTable == null)
            metadataTable = new MetadataTable(dbConnection);
        productTable.validateTable();
        metadataTable.validateTable();
    }

    protected void prepareStatements() throws SQLException {
        productTable.prepareStatements();
        metadataTable.prepareStatements();
    }

    public boolean pathExistsInDB(final File path) throws SQLException {
        return productTable.pathExists(path);
    }

    public ProductEntry getProductEntry(final File path) throws SQLException {
        return productTable.getProductEntry(path);
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

    public void cleanUpRemovedProducts() throws SQLException {
        final DBQuery dbQuery = new DBQuery();
        final ProductEntry[] entries = dbQuery.queryDatabase(this);
        for(ProductEntry entry : entries) {
            if(!entry.getFile().exists()) {
                deleteProductEntry(entry);
            }
        }
    }

    public void deleteProductEntry(final ProductEntry entry) throws SQLException {
        productTable.deleteRecord(entry.getId());
        QuickLookGenerator.deleteQuickLook(entry.getId());
    }

    public void removeProducts(final File baseDir) throws SQLException {
        final String queryStr = AbstractMetadata.PATH+" LIKE '"+baseDir.getAbsolutePath()+"%'";
        final ProductEntry[] list = queryProduct(queryStr);
        for(ProductEntry entry : list) {
            deleteProductEntry(entry);
        }
    }

    public ProductEntry[] getProductEntryList() throws SQLException {
        return productTable.getProductEntryList();
    }

    public ProductEntry[] queryProduct(final String queryStr) throws SQLException {
        final ArrayList<ProductEntry> listEntries = new ArrayList<ProductEntry>();

        final Statement queryStatement = dbConnection.createStatement();
        final ResultSet results = queryStatement.executeQuery(strGetProductsWhere + queryStr);
        while(results.next()) {
            listEntries.add(new ProductEntry(results));
        }
        return listEntries.toArray(new ProductEntry[listEntries.size()]);
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

    public String[] getAllAcquisitionModes() throws SQLException {
        return productTable.getAllAcquisitionModes();
    }

    public String[] getAcquisitionModes(final String[] missions) throws SQLException {
        return productTable.getAcquisitionModes(missions);
    }

    public String[] getMetadataNames() {
        return metadataTable.getAllMetadataNames();
    }
}
