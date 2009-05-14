package org.esa.nest.dat.actions.importbrowser.ui;

import org.esa.beam.util.Guardian;
import org.esa.nest.dat.actions.importbrowser.model.Repository;
import org.esa.nest.dat.actions.importbrowser.model.RepositoryEntry;
import org.esa.nest.dat.actions.importbrowser.model.dataprovider.DataProvider;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.util.ArrayList;

public class RepositoryTableModel extends AbstractTableModel {

    private final Repository repository;
    private final ArrayList<TableColumn> columnList;

    public RepositoryTableModel(final Repository repository) {
        Guardian.assertNotNull("repository", repository);
        this.repository = repository;
        this.repository.addListener(new RepositoryHandler());
        columnList = new ArrayList<TableColumn>();
        final DataProvider[] dataProviders = repository.getDataProviders();
        for (final DataProvider provider : dataProviders) {
            final TableColumn tableColumn = provider.getTableColumn();
            tableColumn.setModelIndex(getColumnCount());
            columnList.add(tableColumn);
        }
    }

    public TableColumnModel getColumnModel() {
        final TableColumnModel columnModel = new DefaultTableColumnModel();
        for (TableColumn aColumnList : columnList) {
            columnModel.addColumn(aColumnList);
        }
        return columnModel;
    }

    public int getRowCount() {
        return repository != null ? repository.getEntryCount() : 0;
    }

    public int getColumnCount() {
        return columnList.size();
    }

    @Override
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
    }

    public Object getValueAt(final int rowIndex, final int columnIndex) {
        if (repository != null) {
            final RepositoryEntry entry = repository.getEntry(rowIndex);
            if(entry != null)
                return entry.getData(columnIndex);
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

    private class RepositoryHandler implements Repository.RepositoryListener {

        public void handleEntryAdded(final RepositoryEntry entry, final int index) {
            fireTableRowsInserted(index, index);
        }

        public void handleEntryRemoved(final RepositoryEntry entry, final int index) {
            fireTableRowsDeleted(index, index);
        }
    }

}
