
package org.esa.nest.dat.actions.importbrowser.model.dataprovider;

import org.esa.nest.dat.actions.importbrowser.model.Repository;
import org.esa.nest.dat.actions.importbrowser.model.RepositoryEntry;

import javax.swing.table.TableColumn;
import java.io.IOException;
import java.util.Comparator;

/**
 * This interface shall be implemented to provide new data to a {@link RepositoryEntry}.
 * The data is shown in a table within the <code>ProductGrabber</code>.
 * <p/>
 * <p> To add a <code>DataProvider</code> to the <code>ProductGrabber</code> use the following example code:
 * <p/>
 * <code>
 * ProductGrabberVPI.getInstance().getRepositoryManager().addDataProvider(new SampleDataProvider());
 * </code>
 * </p>
 * </p>
 */
public interface DataProvider {

    /**
     * Implementation should check if the data this <code>DataProvider</code> provides must be created, or if it is
     * already stored.
     *
     * @param entry      the entry for which the data shall be provided.
     * @param repository the repsoitory containing the entry.
     *
     * @return true, if the data must be created, otherwise false.
     */
    boolean mustCreateData(RepositoryEntry entry, Repository repository);

    /**
     * Implementation should create the data this <code>DataProvider</code> provides.
     * Also the created should be stored for performance reasons.
     * Created data can be stored into a {@link org.esa.beam.util.PropertyMap PropertyMap} retrieved by calling
     * {@link org.esa.beam.visat.plugins.pgrab.model.Repository#getPropertyMap() Repository.getPropertyMap()}
     * or in a directory retrieved from
     * {@link org.esa.beam.visat.plugins.pgrab.model.Repository#getStorageDir() Repository.getStorageDir()}.
     *
     * @param entry      the entry for which the data shall be provided.
     * @param repository the repository containing the entry. // todo - (from nf)  for what? entry knows it repository!
     *
     * @throws IOException if an error occures during creating the data.
     */
    void createData(RepositoryEntry entry, Repository repository) throws IOException;

    /**
     * Returns the data which is provided by this implementation.
     *
     * @param entry      the entry for which the data shall be provided.
     * @param repository the repository containing the entry. // todo - (from nf)  for what? entry knows it repository!
     *
     * @return the provided data.
     *
     * @throws IOException if an error occures during providing the data.
     */
    Object getData(RepositoryEntry entry, Repository repository) throws IOException;

    /**
     * Returns the {@link Comparator} for the data provided by this <code>DataProvider</code>.
     *
     * @return the comparator.
     */
    Comparator getComparator();

    /**
     * Implementation should delete all stored data.
     *
     * @param entry      the entry for which the data was provided.
     * @param repository the repository contained the entry.    // todo - (from nf)  for what? entry knows it repository!
     */
    void cleanUp(RepositoryEntry entry, Repository repository);

    /**
     * Returns a {@link TableColumn} which defines the UI representation of the provided data within a
     * {@link javax.swing.JTable Table}.
     *
     * @return the {@link TableColumn}.
     */
    TableColumn getTableColumn();

}
