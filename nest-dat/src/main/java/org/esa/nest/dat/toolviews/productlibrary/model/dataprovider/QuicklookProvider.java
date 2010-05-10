
package org.esa.nest.dat.toolviews.productlibrary.model.dataprovider;

import org.esa.nest.db.ProductEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Comparator;

public class QuicklookProvider implements DataProvider {

    private final static Comparator quickLookComparator = new QuickLookComparator();
    private TableColumn quickLookColumn;
    private static final int preferredWidth = 100;

    public QuicklookProvider() throws IOException {
    }

    /**
     * Returns the {@link java.util.Comparator} for the data provided by this <code>DataProvider</code>.
     *
     * @return the comparator.
     */
    public Comparator getComparator() {
        return quickLookComparator;
    }

    public void cleanUp(final ProductEntry entry) {
    /*    // nothing to do with propertyMap
        final File quickLookFile = getQuickLookFile(repository.getStorageDir(), entry.getProductFile().getName());
        if (quickLookFile != null && quickLookFile.exists()) {
            quickLookFile.delete();
        }   */
    }

    public TableColumn getTableColumn() {
        if (quickLookColumn == null) {
            quickLookColumn = new TableColumn();
            quickLookColumn.setHeaderValue("Quick Look");        /*I18N*/
            quickLookColumn.setPreferredWidth(preferredWidth);
            quickLookColumn.setResizable(true);
            quickLookColumn.setCellRenderer(new QuickLookRenderer(preferredWidth));
            quickLookColumn.setCellEditor(new QuickLookEditor());
        }
        return quickLookColumn;
    }

    private static class QuickLookRenderer extends DefaultTableCellRenderer {

        private final int rowHeight;
        private JLabel tableComponent;

        public QuickLookRenderer(final int height) {
            rowHeight = height + 3;
        }

        @Override
        public Component getTableCellRendererComponent(final JTable table,
                                                       final Object value,
                                                       final boolean isSelected,
                                                       final boolean hasFocus,
                                                       final int row,
                                                       final int column) {
            if (tableComponent == null) {
                tableComponent = (JLabel) super.getTableCellRendererComponent(table,
                                                                              value,
                                                                              isSelected,
                                                                              hasFocus,
                                                                              row,
                                                                              column);
                tableComponent.setText("");
                tableComponent.setVerticalAlignment(SwingConstants.CENTER);
                tableComponent.setHorizontalAlignment(SwingConstants.CENTER);
            }

            setBackground(table, isSelected);

            if (value == null) {
                tableComponent.setIcon(null);
                tableComponent.setText("");
                return tableComponent;
            }

            if (value instanceof ProductEntry) {
                final BufferedImage image = ((ProductEntry)value).getQuickLook();
                if(image == null) {
                    tableComponent.setIcon(null);
                    tableComponent.setText("Not available!");
                } else {
                    final TableColumn tableColumn = table.getColumnModel().getColumn(column);
                    int cellWidth = tableColumn.getWidth();
                    int cellHeight = tableColumn.getWidth();
                    if(image.getHeight() > image.getWidth())
                        cellWidth = -1;
                    else
                        cellHeight = -1;
                    tableComponent.setIcon(
                            new ImageIcon(image.getScaledInstance(cellWidth, cellHeight, BufferedImage.SCALE_FAST)));
                    tableComponent.setText("");
                    setTableRowHeight(table, row);
                }
            } else {
                tableComponent.setIcon(null);
                tableComponent.setText("Not available!");
            }
            return tableComponent;
        }

        private void setBackground(final JTable table, final boolean isSelected) {
            Color backGroundColor = table.getBackground();
            if (isSelected) {
                backGroundColor = table.getSelectionBackground();
            }
            tableComponent.setBorder(BorderFactory.createLineBorder(backGroundColor, 3));
            tableComponent.setBackground(backGroundColor);
        }

        private void setTableRowHeight(final JTable table, final int row) {
            if (table.getRowHeight(row) < rowHeight) {
                table.setRowHeight(row, rowHeight);
            }
        }
    }

    public static class QuickLookEditor extends AbstractCellEditor implements TableCellEditor {

        private final JScrollPane scrollPane;

        public QuickLookEditor() {

            scrollPane = new JScrollPane();
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scrollPane.getViewport().setOpaque(false);
        }

        public Component getTableCellEditorComponent(final JTable table,
                                                     final Object value,
                                                     final boolean isSelected,
                                                     final int row,
                                                     final int column) {
            if (!(value instanceof ProductEntry)) {
                return null;
            }
            final BufferedImage image = ((ProductEntry) value).getQuickLook();
            if (image == null) {
                return null;
            }
            scrollPane.setViewportView(
                    new JLabel(new ImageIcon(image.getScaledInstance(-1, -1, BufferedImage.SCALE_AREA_AVERAGING))));
            final Color backgroundColor = table.getSelectionBackground();
            scrollPane.setBackground(backgroundColor);
            scrollPane.setBorder(BorderFactory.createLineBorder(backgroundColor, 3));

            return scrollPane;
        }

        public Object getCellEditorValue() {
            return null;
        }
    }


    private static class QuickLookComparator implements Comparator {

        public int compare(final Object o1, final Object o2) {
            if (o1 == o2) {
                return 0;
            }
            if (o1 == null) {
                return -1;
            } else if (o2 == null) {
                return 1;
            }

            final BufferedImage image1 = ((ProductEntry) o1).getQuickLook();
            final BufferedImage image2 = ((ProductEntry) o2).getQuickLook();

            if (image1 == null) {
                return -1;
            } else if (image2 == null) {
                return 1;
            }

            final Integer height1 = image1.getHeight();
            final Integer height2 = image2.getHeight();

            return height1.compareTo(height2);
        }
    }
}
