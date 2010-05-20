package org.esa.nest.db;

import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.nest.datamodel.AbstractMetadata;

import java.sql.*;
import java.util.ArrayList;

/**
 *
 */
public class MetadataTable implements TableInterface {

    private final Connection dbConnection;
    private final static ArrayList<String> metadataNamesList = new ArrayList<String>();

    private PreparedStatement stmtSaveNewRecord;

    private final static MetadataElement emptyMetadata = AbstractMetadata.addAbstractedMetadataHeader(null);
    private static String createTableStr;
    private static String saveProductStr;
    static {
        createTableStrings();
    }

    private static final String strCreateProductTable =
            "create table APP.METADATA (" +
            "    ID          INTEGER NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)";

    private static final String strSaveProduct =
            "INSERT INTO APP.METADATA ";

    public MetadataTable(final Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    public void createTable() throws SQLException {
        final Statement statement = dbConnection.createStatement();
        statement.execute(createTableStr);
    }

    public void validateTable() throws SQLException {

    }

    private static void createTableStrings() {
        createTableStr = strCreateProductTable;
        String namesStr = "";
        String valueStr = "";

        final MetadataAttribute[] attribList = emptyMetadata.getAttributes();
        for(MetadataAttribute attrib : attribList) {
            final String name = attrib.getName();
            metadataNamesList.add(name);
            createTableStr += ", "+ name +" "+ getDataType(attrib.getDataType());
            namesStr += name +",";
            valueStr += "?,";
        }
        createTableStr += ")";
        namesStr = namesStr.substring(0, namesStr.length()-1);
        valueStr = valueStr.substring(0, valueStr.length()-1);

        saveProductStr = strSaveProduct + "("+ namesStr +")"+ "VALUES ("+valueStr+")";
    }

    private static String getDataType(final int dataType) {
        if(dataType == ProductData.TYPE_FLOAT32)
            return "FLOAT";
        else if(dataType == ProductData.TYPE_FLOAT64)
            return "DOUBLE";
        else if(dataType == ProductData.TYPE_UTC)
            return "VARCHAR(255)"; //"TIMESTAMP";
        else if(dataType < ProductData.TYPE_FLOAT32)
            return "INTEGER";
        return "VARCHAR(555)";
    }

    public void prepareStatements() throws SQLException {
        stmtSaveNewRecord = dbConnection.prepareStatement(saveProductStr, Statement.RETURN_GENERATED_KEYS);
    }

    public ResultSet addRecord(final ProductEntry record) throws SQLException {
        stmtSaveNewRecord.clearParameters();
        //System.out.println(record.getFile());

        final MetadataElement absRoot = record.getMetadata();
        final MetadataAttribute[] attribList = emptyMetadata.getAttributes();
        int i=1;
        for(MetadataAttribute attrib : attribList) {
            final String name = attrib.getName();
            //System.out.println(name);
            final int dataType = attrib.getDataType();
            if(dataType == ProductData.TYPE_FLOAT32)
                stmtSaveNewRecord.setFloat(i, (float)absRoot.getAttributeDouble(name));
            else if(dataType == ProductData.TYPE_FLOAT64)
                stmtSaveNewRecord.setDouble(i, absRoot.getAttributeDouble(name));
            else if(dataType == ProductData.TYPE_UTC)
                //stmtSaveNewRecord.setDate(i, new Date((long)absRoot.getAttributeUTC(name).getMJD()));
                stmtSaveNewRecord.setString(i, absRoot.getAttributeUTC(name).getElemString());
            else if(dataType < ProductData.TYPE_FLOAT32)
                stmtSaveNewRecord.setInt(i, absRoot.getAttributeInt(name));
            else
                stmtSaveNewRecord.setString(i, absRoot.getAttributeString(name));
            ++i;
        }
        final int rowCount = stmtSaveNewRecord.executeUpdate();
        return stmtSaveNewRecord.getGeneratedKeys();
    }

    public String[] getAllMetadataNames() {
        return metadataNamesList.toArray(new String[metadataNamesList.size()]);
    }
}