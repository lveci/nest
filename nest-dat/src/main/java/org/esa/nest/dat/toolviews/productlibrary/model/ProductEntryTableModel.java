package org.esa.nest.dat.toolviews.productlibrary.model;

import org.esa.beam.visat.VisatApp;
import org.esa.nest.dat.toolviews.productlibrary.model.dataprovider.DataProvider;
import org.esa.nest.dat.toolviews.productlibrary.model.dataprovider.IDProvider;
import org.esa.nest.dat.toolviews.productlibrary.model.dataprovider.PropertiesProvider;
import org.esa.nest.dat.toolviews.productlibrary.model.dataprovider.QuicklookProvider;
import org.esa.nest.db.ProductEntry;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.util.ArrayList;

public class ProductEntryTableModel extends AbstractTableModel {

    private ProductEntry[] productEntryList;
    final ArrayList<DataProvider> dataProviders = new ArrayList<DataProvider>(5);
    private final ArrayList<TableColumn> columnList = new ArrayList<TableColumn>();

    public ProductEntryTableModel(final ProductEntry[] productList) {
        this.productEntryList = productList;
        dataProviders.add(new IDProvider());
        dataProviders.add(new PropertiesProvider());
        try {
            dataProviders.add(new QuicklookProvider());
        } catch(Exception e) {
            e.printStackTrace();
            if(VisatApp.getApp() != null) {
                VisatApp.getApp().showErrorDialog(e.getMessage());
            }
        }
        for (final DataProvider provider : dataProviders) {
            final TableColumn tableColumn = provider.getTableColumn();
            tableColumn.setModelIndex(getColumnCount());
            columnList.add(tableColumn);
        }
    }

    public DataProvider getDataProvider(final int columnIndex) {
        if(columnIndex >= 0 && columnIndex < dataProviders.size()) {
            return dataProviders.get(columnIndex);
        }
        return null;
    }

    public TableColumnModel getColumnModel() {
        final TableColumnModel columnModel = new DefaultTableColumnModel();
        for (TableColumn aColumnList : columnList) {
            columnModel.addColumn(aColumnList);
        }
        return columnModel;
    }

    public int getRowCount() {
        return productEntryList != null ? productEntryList.length : 0;
    }

    public int getColumnCount() {
        return columnList.size();
    }

 /*   @Override
    public Class getColumnClass(final int columnIndex) {
        if (repository != null) {
            if (repository.getEntryCount() > 0) {
                final Object data = repository.getEntry(0).getData(columnIndex);
                if (data != null) {
                    return data.getClass();
                }
            }
        }
        return Object.class;
    }   */

    public Object getValueAt(final int rowIndex, final int columnIndex) {
        if (productEntryList != null) {
            final ProductEntry entry = productEntryList[rowIndex];
            if(entry != null)
                return entry;
        }
        return null;
    }

    @Override
    public String getColumnName(final int columnIndex) {
        if (columnIndex >= 0 && columnIndex < columnList.size()) {
            final TableColumn column = columnList.get(columnIndex);
            return column.getHeaderValue().toString();
        }
        return "";
    }

    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
        if (columnIndex >= columnList.size()) {
            return false;
        }
        final TableColumn column = columnList.get(columnIndex);
        return column.getCellEditor() != null;
    }

}