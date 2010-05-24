package org.esa.nest.db;

import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.util.SQLUtils;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;

/**
 *
 */
public class ProductTable implements TableInterface {

    private final Connection dbConnection;

    private PreparedStatement stmtSaveNewRecord;
    private PreparedStatement stmtUpdateExistingRecord;
    private PreparedStatement stmtGetProduct;
    private PreparedStatement stmtGetProductWithPath;
    private PreparedStatement stmtDeleteProduct;
    private PreparedStatement stmtAllMissions;
    private PreparedStatement stmtAllProductTypes;
    private PreparedStatement stmtAllAcquisitionModes;

    private static final String strCreateProductTable =
            "create table APP.PRODUCTS (" +
            "    ID          INTEGER NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)," +
            AbstractMetadata.PATH           +" VARCHAR(255), " +
            AbstractMetadata.PRODUCT        +" VARCHAR(255), " +
            AbstractMetadata.MISSION        +" VARCHAR(30), " +
            AbstractMetadata.PRODUCT_TYPE   +" VARCHAR(30), " +
            AbstractMetadata.ACQUISITION_MODE+" VARCHAR(30), " +
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
            AbstractMetadata.azimuth_spacing+" DOUBLE, " +
            AbstractMetadata.first_line_time+" DATE, " +
            ProductEntry.FILE_SIZE          +" DOUBLE, " +
            ProductEntry.LAST_MODIFIED      +" DOUBLE, " +
            ProductEntry.FILE_FORMAT        +" VARCHAR(30)" +
            ")";

    private static final String strGetProduct =
            "SELECT * FROM APP.PRODUCTS " +
            "WHERE ID = ?";

    private static final String strSaveProduct =
            "INSERT INTO APP.PRODUCTS ( " +
            AbstractMetadata.PATH           +", "+
            AbstractMetadata.PRODUCT        +", "+
            AbstractMetadata.MISSION        +", "+
            AbstractMetadata.PRODUCT_TYPE   +", "+
            AbstractMetadata.ACQUISITION_MODE+", "+
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
            AbstractMetadata.azimuth_spacing+", "+
            AbstractMetadata.first_line_time+", "+
            ProductEntry.FILE_SIZE          +", "+
            ProductEntry.LAST_MODIFIED      +", "+
            ProductEntry.FILE_FORMAT        +
            ") " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
    private static final String strAllAcquisitionModes = "SELECT DISTINCT "+AbstractMetadata.ACQUISITION_MODE+" FROM APP.PRODUCTS";

    public ProductTable(final Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    public void createTable() throws SQLException {
        final Statement statement = dbConnection.createStatement();
        statement.execute(strCreateProductTable);
    }

    public void validateTable() throws SQLException {
        // alter table if columns are missing    
    }

    public void prepareStatements() throws SQLException {
        stmtSaveNewRecord = dbConnection.prepareStatement(strSaveProduct, Statement.RETURN_GENERATED_KEYS);
        stmtUpdateExistingRecord = dbConnection.prepareStatement(strUpdateProduct);
        stmtGetProduct = dbConnection.prepareStatement(strGetProduct);
        stmtGetProductWithPath = dbConnection.prepareStatement(strGetProductWithPath);
        stmtDeleteProduct = dbConnection.prepareStatement(strDeleteProduct);

        stmtAllMissions = dbConnection.prepareStatement(strAllMissions);
        stmtAllProductTypes = dbConnection.prepareStatement(strAllProductTypes);
        stmtAllAcquisitionModes = dbConnection.prepareStatement(strAllAcquisitionModes);
    }

    public ResultSet addRecord(final ProductEntry record) throws SQLException {
        stmtSaveNewRecord.clearParameters();
        int i = 1;
        stmtSaveNewRecord.setString(i++, record.getFile().getAbsolutePath());
        stmtSaveNewRecord.setString(i++, record.getName());
        stmtSaveNewRecord.setString(i++, record.getMission());
        stmtSaveNewRecord.setString(i++, record.getProductType());
        stmtSaveNewRecord.setString(i++, record.getAcquisitionMode());
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
        stmtSaveNewRecord.setDate(i++, SQLUtils.toSQLDate(record.getFirstLineTime()));
        stmtSaveNewRecord.setDouble(i++, record.getFileSize());
        stmtSaveNewRecord.setDouble(i++, record.getLastModified());
        stmtSaveNewRecord.setString(i++, record.getFileFormat());

        final int rowCount = stmtSaveNewRecord.executeUpdate();
        return stmtSaveNewRecord.getGeneratedKeys();
    }

    /* public void editRecord(final ProductEntry record) throws SQLException {
        stmtUpdateExistingRecord.clearParameters();

        stmtUpdateExistingRecord.setString(1, record.getFile());
        stmtUpdateExistingRecord.setInt(12, record.getId());
        stmtUpdateExistingRecord.executeUpdate();
    } */
    
    public void deleteRecord(final int id) throws SQLException {
        stmtDeleteProduct.clearParameters();
        stmtDeleteProduct.setInt(1, id);
        stmtDeleteProduct.executeUpdate();
    }

    public boolean pathExists(final File path) throws SQLException {
        stmtGetProductWithPath.clearParameters();
        stmtGetProductWithPath.setString(1, path.getAbsolutePath());
        final ResultSet results = stmtGetProductWithPath.executeQuery();
        return results.next();
    }

    public ProductEntry[] getProductEntryList() throws SQLException {
        final ArrayList<ProductEntry> listEntries = new ArrayList<ProductEntry>();

        final Statement queryStatement = dbConnection.createStatement();
        final ResultSet results = queryStatement.executeQuery(strGetListEntries);
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
     * @param missions the selected missions
     * @return list of product types
     * @throws SQLException .
     */
    public String[] getProductTypes(final String[] missions) throws SQLException {
        if(missions == null || missions.length == 0)
            return new String[] {};
        String strMissionProductTypes = "SELECT DISTINCT "+AbstractMetadata.PRODUCT_TYPE+" FROM APP.PRODUCTS WHERE ";
        strMissionProductTypes += SQLUtils.getOrList(AbstractMetadata.MISSION, missions);

        final ArrayList<String> listEntries = new ArrayList<String>();
        final Statement queryStatement = dbConnection.createStatement();
        final ResultSet results = queryStatement.executeQuery(strMissionProductTypes);
        while(results.next()) {
            listEntries.add(results.getString(1));
        }
        return listEntries.toArray(new String[listEntries.size()]);
    }

    /**
     * Get All acquisition modes
     * @return list of acquisition modes
     * @throws SQLException .
     */
    public String[] getAllAcquisitionModes() throws SQLException {
        final ArrayList<String> listEntries = new ArrayList<String>();
        final ResultSet results = stmtAllAcquisitionModes.executeQuery();
        while(results.next()) {
            listEntries.add(results.getString(1));
        }
        return listEntries.toArray(new String[listEntries.size()]);
    }

    /**
     * Get All acquisition modes for specified mission
     * @param missions the selected missions
     * @return list of acquisition modes
     * @throws SQLException .
     */
    public String[] getAcquisitionModes(final String[] missions) throws SQLException {
        if(missions == null || missions.length == 0)
            return new String[] {};
        String strMissionAcquisitionModes = "SELECT DISTINCT "+AbstractMetadata.ACQUISITION_MODE+" FROM APP.PRODUCTS WHERE ";
        strMissionAcquisitionModes += SQLUtils.getOrList(AbstractMetadata.MISSION, missions);

        final ArrayList<String> listEntries = new ArrayList<String>();
        final Statement queryStatement = dbConnection.createStatement();
        final ResultSet results = queryStatement.executeQuery(strMissionAcquisitionModes);
        while(results.next()) {
            listEntries.add(results.getString(1));
        }
        return listEntries.toArray(new String[listEntries.size()]);
    }
}