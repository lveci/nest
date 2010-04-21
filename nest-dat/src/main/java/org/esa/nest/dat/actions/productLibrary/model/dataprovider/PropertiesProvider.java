package org.esa.nest.dat.actions.productLibrary.model.dataprovider;

import org.esa.beam.framework.datamodel.ProductData.UTC;
import org.esa.nest.db.ProductEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.io.File;
import java.util.Comparator;

/**
 * Description of ProductPropertiesProvider
 *
 */
public class PropertiesProvider implements DataProvider {

    private final String[] propertyLables = new String[]{
            "Name:",
            "Type:",
            "Pass:",
            "File Size:",
            "Image Size:",
            "Pixel Size:",
            "Start time:"
            //"Last Modified:",
    };

    private static final String NOT_AVAILABLE = "not available";

    private final Comparator productPropertiesComparator = new ProductPropertiesComparator();

    private TableColumn propertiesColumn;

    public Comparator getComparator() {
        return productPropertiesComparator;
    }

    public void cleanUp(final ProductEntry entry) {
    }

    public TableColumn getTableColumn() {
        if (propertiesColumn == null) {
            propertiesColumn = new TableColumn();
            propertiesColumn.setResizable(true);
            propertiesColumn.setPreferredWidth(150);
            propertiesColumn.setHeaderValue("Product Properties");
            propertiesColumn.setCellRenderer(new ProductPropertiesRenderer());
        }
        return propertiesColumn;
    }

    private static String formatStartTime(final UTC sceneRasterStartTime) {
        String timeString = NOT_AVAILABLE;
        if (sceneRasterStartTime != null) {
            timeString = sceneRasterStartTime.getElemString();
        }
        return timeString;
    }

    private class ProductPropertiesRenderer extends JTable implements TableCellRenderer {

        private static final int ROW_HEIGHT = 100;
        private final JPanel centeringPanel = new JPanel(new BorderLayout());
        private final Font valueFont;

        public ProductPropertiesRenderer() {
            final DefaultTableModel dataModel = new DefaultTableModel();
            dataModel.setColumnCount(2);
            dataModel.setRowCount(propertyLables.length);

            for (int i = 0; i < propertyLables.length; i++) {
                dataModel.setValueAt(propertyLables[i], i, 0);
                dataModel.setValueAt("", i, 1);
            }

            setModel(dataModel);
            valueFont = getFont().deriveFont(Font.BOLD);
            getColumnModel().getColumn(1).setCellRenderer(new PropertyValueCellRenderer(valueFont));

            //this.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            getTableHeader().setVisible(false);
            setShowHorizontalLines(false);
            setShowVerticalLines(false);
        }

        public Component getTableCellRendererComponent(final JTable table,
                                                       final Object value,
                                                       final boolean isSelected,
                                                       final boolean hasFocus,
                                                       final int row, final int column) {
            String[] values = null;
            String toolTip = "";
            if (value instanceof ProductEntry) {
                final ProductEntry entry = (ProductEntry) value;

                final String pixelSpacing = entry.getRangeSpacing() +"x"+ entry.getAzimuthSpacing() +" m";
                final File file = entry.getFile();
                final String fileSize = (file.length() / (1024 * 1024)) +" Mb";

                values = new String[]{
                        entry.getName(),
                        entry.getProductType(),
                        entry.getPass(),
                        fileSize,
                        "",
                        pixelSpacing
                };
                for (int i = 0; i < values.length; i++) {
                    setValueAt(values[i], i, 1);
                }
                toolTip = file.getAbsolutePath();
            } else if (value == null) {
                for (int i = 0; i < propertyLables.length; i++) {
                    setValueAt(null, i, 1);
                }
            }

            final Color backgroundColor;
            final Color foregroundColor;
            if (isSelected) {
                backgroundColor = table.getSelectionBackground();
                foregroundColor = table.getSelectionForeground();
            } else {
                backgroundColor = table.getBackground();
                foregroundColor = table.getForeground();
            }
            setForeground(foregroundColor);
            setBackground(backgroundColor);
            centeringPanel.setForeground(foregroundColor);
            centeringPanel.setBackground(backgroundColor);
            centeringPanel.setBorder(BorderFactory.createLineBorder(backgroundColor, 3));
            centeringPanel.add(this, BorderLayout.CENTER);

            centeringPanel.setToolTipText(toolTip);
            adjustCellSize(table, row, column, values);
            return centeringPanel;
        }

        private void adjustCellSize(JTable table, int row, int column, String[] values) {
            setRowHeight(table, row, ROW_HEIGHT);

            final int lablesLength = getMaxStringLength(propertyLables, getFontMetrics(getFont()));
            int columnIndex = 0;
            increasePreferredColumnWidth(getColumnModel().getColumn(columnIndex), lablesLength);

            int valuesLength = 50;
            if (values != null) {
                valuesLength = getMaxStringLength(values, getFontMetrics(valueFont));
                increasePreferredColumnWidth(getColumnModel().getColumn(1), valuesLength);
            }
            int preferredWidth = lablesLength + valuesLength;
            preferredWidth = (int) (preferredWidth + (preferredWidth * 0.01f));
            final TableColumn valueColumn = table.getColumnModel().getColumn(column);
            final int valueColWidth = Math.max(valueColumn.getWidth(), preferredWidth);
            increasePreferredColumnWidth(valueColumn, valueColWidth);
        }

         private void increasePreferredColumnWidth(TableColumn column, int length) {
             if (column.getPreferredWidth() < length) {
                column.setPreferredWidth(length);
            }
        }

        private void setRowHeight(final JTable table, final int row, final int rowHeight) {
            final int currentRowHeight = table.getRowHeight(row);
            if (currentRowHeight < rowHeight) {
                table.setRowHeight(rowHeight);
            }
        }

        private int getMaxStringLength(final String[] strings, final FontMetrics fontMetrics) {
            int maxWidth = Integer.MIN_VALUE;
            for (String string : strings) {
                if (string == null) {
                    string = String.valueOf(string);
                }
                final int width = SwingUtilities.computeStringWidth(fontMetrics, string);
                maxWidth = Math.max(width, maxWidth);
            }
            return maxWidth;
        }

        private class PropertyValueCellRenderer extends DefaultTableCellRenderer {

            private final Font _font;

            public PropertyValueCellRenderer(final Font font) {
                _font = font;
            }

            @Override
            public Component getTableCellRendererComponent(final JTable table, final Object value,
                                                           final boolean isSelected, final boolean hasFocus,
                                                           final int row, final int column) {
                final JLabel jLabel = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
                                                                                   row, column);
                jLabel.setHorizontalAlignment(JLabel.LEFT);
                jLabel.setFont(_font);
                return jLabel;
            }
        }
    }

    private static class ProductPropertiesComparator implements Comparator {

        public int compare(final Object o1, final Object o2) {
            if (o1 == o2) {
                return 0;
            }
            if (o1 == null) {
                return -1;
            } else if (o2 == null) {
                return 1;
            }

            final ProductEntry s1 = (ProductEntry) o1;
            final ProductEntry s2 = (ProductEntry) o2;

            return s1.getName().compareTo(s2.getName());
        }
    }
}