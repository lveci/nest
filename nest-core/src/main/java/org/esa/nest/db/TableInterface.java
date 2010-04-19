package org.esa.nest.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/**

 */
public interface TableInterface {

    public void createTable() throws SQLException;

    public void validateTable() throws SQLException;

    public void prepareStatements() throws SQLException;

    public ResultSet addRecord(final ProductEntry record) throws SQLException;
}
