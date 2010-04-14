package org.esa.nest.dat.actions.productLibrary.model.dataprovider;

import org.esa.nest.db.ProductEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.io.IOException;
import java.util.Comparator;

public class FileNameProvider implements DataProvider {

    private final Comparator _fileNameComparator = new FileNameComparator();
    private TableColumn _fileNameColumn;

    public boolean mustCreateData(final ProductEntry entry) {
        return false;
    }

    public void createData(final ProductEntry entry) throws IOException {
    }

    public Object getData(final ProductEntry entry) throws IOException {
        return entry.getName();
    }

    public Comparator getComparator() {
        return _fileNameComparator;
    }

    public void cleanUp(final ProductEntry entry) {
    }

    public TableColumn getTableColumn() {
        if(_fileNameColumn == null) {
            _fileNameColumn = new TableColumn();
            _fileNameColumn.setHeaderValue("File Name");
            _fileNameColumn.setPreferredWidth(150);
            _fileNameColumn.setResizable(true);
            _fileNameColumn.setCellRenderer(new FileNameCellRenderer());
        }
        return _fileNameColumn;
    }

    private static class FileNameCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(final JTable table,
                                                       final Object value,
                                                       final boolean isSelected,
                                                       final boolean hasFocus,
                                                       final int row, final int column) {
            final ProductEntry entry = (ProductEntry) value;
            if(entry != null) {
                final String text = entry.getName();

                final JLabel jlabel = (JLabel) super
                        .getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column);

                jlabel.setFont(jlabel.getFont().deriveFont(Font.BOLD));
                jlabel.setToolTipText(text);
                return jlabel;
            }
            return null;
        }
    }

    private static class FileNameComparator implements Comparator {

        public int compare(final Object o1, final Object o2) {
            if(o1 == o2) {
                return 0;
            }
            if (o1 == null) {
                return -1;
            } else if(o2 == null) {
                return 1;
            }

            final String s1 = (String) o1;
            final String s2 = (String) o2;

            return s1.compareTo(s2);
        }
    }
}