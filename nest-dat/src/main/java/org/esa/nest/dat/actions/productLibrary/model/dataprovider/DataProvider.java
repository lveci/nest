package org.esa.nest.dat.actions.productLibrary.model.dataprovider;

import org.esa.nest.db.ProductEntry;

import javax.swing.table.TableColumn;
import java.io.IOException;
import java.util.Comparator;

/**

 */
public interface DataProvider {

    /**
     * Implementation should check if the data this <code>DataProvider</code> provides must be created, or if it is
     * already stored.
     *
     * @param entry      the entry for which the data shall be provided.
     *
     * @return true, if the data must be created, otherwise false.
     */
    boolean mustCreateData(final ProductEntry entry);

    /**

     */
    void createData(final ProductEntry entry) throws IOException;

    /**
     * Returns the data which is provided by this implementation.
     *
     * @param entry      the entry for which the data shall be provided.
     *
     * @return the provided data.
     *
     * @throws java.io.IOException if an error occures during providing the data.
     */
    Object getData(final ProductEntry entry) throws IOException;

    /**
     * Returns the {@link java.util.Comparator} for the data provided by this <code>DataProvider</code>.
     *
     * @return the comparator.
     */
    Comparator getComparator();

    /**
     * Implementation should delete all stored data.
     *
     * @param entry      the entry for which the data was provided.
     */
    void cleanUp(final ProductEntry entry);

    /**
     * Returns a {@link javax.swing.table.TableColumn} which defines the UI representation of the provided data within a
     * {@link javax.swing.JTable Table}.
     *
     * @return the {@link javax.swing.table.TableColumn}.
     */
    TableColumn getTableColumn();

}