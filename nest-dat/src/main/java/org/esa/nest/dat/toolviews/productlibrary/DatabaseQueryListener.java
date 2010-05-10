package org.esa.nest.dat.toolviews.productlibrary;

/**

 */
public interface DatabaseQueryListener {

    void notifyNewProductEntryListAvailable();

    void notifyNewMapSelectionAvailable();
}