package org.esa.nest.db;

import org.esa.nest.datamodel.AbstractMetadata;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.io.File;

/**
 *
 */
public class ProductTable implements TableInterface {

    private PreparedStatement stmtSaveNewRecord;
    private PreparedStatement stmtUpdateExistingRecord;
    private PreparedStatement stmtGetAddress;
    private PreparedStatement stmtGetProductWithPath;
    private PreparedStatement stmtDeleteAddress;
    private PreparedStatement stmtAllMissions;
    private PreparedStatement stmtAllProductTypes;
    private PreparedStatement stmtMissionProductTypes;

    private static final String strCreateProductTable =
            "create table APP.PRODUCTS (" +
            "    ID          INTEGER NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)," +
            AbstractMetadata.PATH           +" VARCHAR(255), " +
            AbstractMetadata.PRODUCT        +" VARCHAR(255), " +
            AbstractMetadata.MISSION        +" VARCHAR(30), " +
            AbstractMetadata.PRODUCT_TYPE   +" VARCHAR(30), " +
            AbstractMetadata.PASS           +" VARCHAR(30), " +
            AbstractMetadata.first_near_lat +" DOUBLE, " +
            AbstractMetadata.first_near_long+" DOUBLE, " +
            AbstractMetadata.first_far_lat  +" DOUBLE, " +
            AbstractMetadata.first_far_long +" DOUBLE, " +
            AbstractMetadata.last_near_lat  +" DOUBLE, " +
            AbstractMetadata.last_near_long +" DOUBLE, " +
            AbstractMetadata.last_far_lat   +" DOUBLE, " +
            AbstractMetadata.last_far_long  +" DOUBLE, " +
            AbstractMetadata.range_spacing  +" DOUBLE, " +
            AbstractMetadata.azimuth_spacing+" DOUBLE" +
            ")";

    private static final String strGetProduct =
            "SELECT * FROM APP.PRODUCTS " +
            "WHERE ID = ?";

    private static final String strGetProductsWhere =
            "SELECT * FROM APP.PRODUCTS WHERE ";

    private static final String strSaveProduct =
            "INSERT INTO APP.PRODUCTS ( " +
            AbstractMetadata.PATH           +", "+
            AbstractMetadata.PRODUCT        +", "+
            AbstractMetadata.MISSION        +", "+
            AbstractMetadata.PRODUCT_TYPE   +", "+
            AbstractMetadata.PASS           +", "+
            AbstractMetadata.first_near_lat +", "+
            AbstractMetadata.first_near_long+", "+
            AbstractMetadata.first_far_lat  +", "+
            AbstractMetadata.first_far_long +", "+
            AbstractMetadata.last_near_lat  +", "+
            AbstractMetadata.last_near_long +", "+
            AbstractMetadata.last_far_lat   +", "+
            AbstractMetadata.last_far_long  +", "+
            AbstractMetadata.range_spacing  +", "+
            AbstractMetadata.azimuth_spacing+
            ") " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String strGetListEntries =
            "SELECT * FROM APP.PRODUCTS ORDER BY "+AbstractMetadata.MISSION+" ASC";

    private static final String strGetProductWithPath =
            "SELECT ID FROM APP.PRODUCTS WHERE "+AbstractMetadata.PATH+" = ?";

    private static final String strUpdateProduct =
            "UPDATE APP.PRODUCTS SET " +
            AbstractMetadata.PATH+" = ?, " +
            AbstractMetadata.MISSION+" = ?, " +
            AbstractMetadata.PRODUCT_TYPE+" = ? " +
            "WHERE ID = ?";

    private static final String strDeleteProduct =
            "DELETE FROM APP.PRODUCTS WHERE ID = ?";

    private static final String strAllMissions = "SELECT DISTINCT "+AbstractMetadata.MISSION+" FROM APP.PRODUCTS";
    private static final String strAllProductTypes = "SELECT DISTINCT "+AbstractMetadata.PRODUCT_TYPE+" FROM APP.PRODUCTS";
    private static final String strMissionProductTypes = "SELECT DISTINCT "+AbstractMetadata.PRODUCT_TYPE+" FROM APP.PRODUCTS" +
                                                     " WHERE "+AbstractMetadata.MISSION+" = ?";

    public void createTable(final Connection dbConnection) throws SQLException {
        final Statement statement = dbConnection.createStatement();
        statement.execute(strCreateProductTable);
    }

    public void validateTable(final Connection dbConnection) throws SQLException {
        // alter table if columns are missing    
    }

    public void prepareStatements(final Connection dbConnection) throws SQLException {
        stmtSaveNewRecord = dbConnection.prepareStatement(strSaveProduct, Statement.RETURN_GENERATED_KEYS);
        stmtUpdateExistingRecord = dbConnection.prepareStatement(strUpdateProduct);
        stmtGetAddress = dbConnection.prepareStatement(strGetProduct);
        stmtGetProductWithPath = dbConnection.prepareStatement(strGetProductWithPath);
        stmtDeleteAddress = dbConnection.prepareStatement(strDeleteProduct);

        stmtAllMissions = dbConnection.prepareStatement(strAllMissions);
        stmtAllProductTypes = dbConnection.prepareStatement(strAllProductTypes);
        stmtMissionProductTypes = dbConnection.prepareStatement(strMissionProductTypes);
    }

    public ResultSet addRecord(final ProductEntry record) throws SQLException {
        stmtSaveNewRecord.clearParameters();
        int i = 1;
        stmtSaveNewRecord.setString(i++, record.getFile().getAbsolutePath());
        stmtSaveNewRecord.setString(i++, record.getName());
        stmtSaveNewRecord.setString(i++, record.getMission());
        stmtSaveNewRecord.setString(i++, record.getProductType());
        stmtSaveNewRecord.setString(i++, record.getPass());
        stmtSaveNewRecord.setDouble(i++, record.getFirstNearGeoPos().getLat());
        stmtSaveNewRecord.setDouble(i++, record.getFirstNearGeoPos().getLon());
        stmtSaveNewRecord.setDouble(i++, record.getFirstFarGeoPos().getLat());
        stmtSaveNewRecord.setDouble(i++, record.getFirstFarGeoPos().getLon());
        stmtSaveNewRecord.setDouble(i++, record.getLastNearGeoPos().getLat());
        stmtSaveNewRecord.setDouble(i++, record.getLastNearGeoPos().getLon());
        stmtSaveNewRecord.setDouble(i++, record.getLastFarGeoPos().getLat());
        stmtSaveNewRecord.setDouble(i++, record.getLastFarGeoPos().getLon());
        stmtSaveNewRecord.setDouble(i++, record.getRangeSpacing());
        stmtSaveNewRecord.setDouble(i++, record.getAzimuthSpacing());
        
        final int rowCount = stmtSaveNewRecord.executeUpdate();
        return stmtSaveNewRecord.getGeneratedKeys();
    }

    public boolean pathExists(final File path) throws SQLException {
        stmtGetProductWithPath.clearParameters();
        stmtGetProductWithPath.setString(1, path.getAbsolutePath());
        final ResultSet results = stmtGetProductWithPath.executeQuery();
        return results.next();
    }

    public ProductEntry[] getProductEntryList(final Connection dbConnection) throws SQLException {
        final ArrayList<ProductEntry> listEntries = new ArrayList<ProductEntry>();

        final Statement queryStatement = dbConnection.createStatement();
        final ResultSet results = queryStatement.executeQuery(strGetListEntries);
        while(results.next()) {
            listEntries.add(new ProductEntry(results));
        }
        return listEntries.toArray(new ProductEntry[listEntries.size()]);
    }

    public ProductEntry[] query(final Connection dbConnection, final String queryStr) throws SQLException {
        final ArrayList<ProductEntry> listEntries = new ArrayList<ProductEntry>();

        final Statement queryStatement = dbConnection.createStatement();
        final ResultSet results = queryStatement.executeQuery(strGetProductsWhere + queryStr);
        while(results.next()) {
            listEntries.add(new ProductEntry(results));
        }
        return listEntries.toArray(new ProductEntry[listEntries.size()]);
    }

    public String[] getAllMissions() throws SQLException {
        final ArrayList<String> listEntries = new ArrayList<String>();
        final ResultSet results = stmtAllMissions.executeQuery();
        while(results.next()) {
            listEntries.add(results.getString(1));
        }
        return listEntries.toArray(new String[listEntries.size()]);
    }

    /**
     * Get All product types
     * @return list of product types
     * @throws SQLException .
     */
    public String[] getAllProductTypes() throws SQLException {
        final ArrayList<String> listEntries = new ArrayList<String>();
        final ResultSet results = stmtAllProductTypes.executeQuery();
        while(results.next()) {
            listEntries.add(results.getString(1));
        }
        return listEntries.toArray(new String[listEntries.size()]);
    }

    /**
     * Get All product types for specified mission
     * @param mission the mission
     * @return list of product types
     * @throws SQLException .
     */
    public String[] getProductTypes(final String mission) throws SQLException {
        final ArrayList<String> listEntries = new ArrayList<String>();
        stmtMissionProductTypes.clearParameters();
        stmtMissionProductTypes.setString(1, mission);
        final ResultSet results = stmtMissionProductTypes.executeQuery();
        while(results.next()) {
            listEntries.add(results.getString(1));
        }
        return listEntries.toArray(new String[listEntries.size()]);
    }
}