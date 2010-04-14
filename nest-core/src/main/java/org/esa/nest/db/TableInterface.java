package org.esa.nest.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.ResultSet;

/**

 */
public interface TableInterface {

    public void createTable(final Connection dbConnection) throws SQLException;

    public void validateTable(final Connection dbConnection) throws SQLException;

    public void prepareStatements(final Connection dbConnection) throws SQLException;

    public ResultSet addRecord(final ProductEntry record) throws SQLException;
}
