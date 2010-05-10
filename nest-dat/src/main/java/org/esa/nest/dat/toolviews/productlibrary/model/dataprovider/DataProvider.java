package org.esa.nest.dat.toolviews.productlibrary.model.dataprovider;

import org.esa.nest.db.ProductEntry;

import javax.swing.table.TableColumn;
import java.util.Comparator;

/**

 */
public interface DataProvider {

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