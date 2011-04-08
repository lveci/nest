/*
 * Copyright (C) 2011 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.gpf;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.BasicApp;
import org.esa.beam.util.io.FileChooserFactory;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.db.ProductEntry;
import org.esa.nest.util.DialogUtils;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;

/**
 * Stack Reader Operator User Interface
 * User: lveci
 * Date: Feb 12, 2008
 */
public class ProductSetReaderOpUI extends BaseOperatorUI {

    private final FileModel fileModel = new FileModel();
    private final JTable productSetTable = new JTable(fileModel);

    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);

        final JComponent comp = createComponent(productSetTable, fileModel);
        initParameters();
        return comp;
    }

    @Override
    public void initParameters() {
        convertFromDOM();

        final String[] fList = (String[])paramMap.get("fileList");
        if(fList != null) {
            fileModel.clear();
            for (String str : fList) {
                fileModel.addFile(new File(str));
            }
        }
    }

    @Override
    public UIValidation validateParameters() {

        return new UIValidation(UIValidation.State.OK, "");
    }

    @Override
    public void updateParameters() {

        final ArrayList<File> fileList = fileModel.getFileList();
        if(fileList.isEmpty()) return;

        final String[] fList = new String[fileList.size()];
        for(int i=0; i < fileList.size(); ++i) {
            if(fileList.get(i).getName().isEmpty())
                fList[i] = "";
            else
                fList[i] = fileList.get(i).getAbsolutePath();
        }
        paramMap.put("fileList", fList);
    }

    public static JComponent createComponent(final JTable table, final FileModel fileModel) {

        final JPanel fileListPanel = new JPanel(new BorderLayout(4, 4));

        fileModel.setColumnWidths(table.getColumnModel());
        table.setColumnSelectionAllowed(true);
        table.setDropMode(DropMode.ON);
        table.setTransferHandler(new ProductSetTransferHandler(fileModel));

        final JScrollPane scrollPane = new JScrollPane(table);
        fileListPanel.add(scrollPane, BorderLayout.CENTER);

        final JPanel buttonPanel = initButtonPanel(table, fileModel);
        fileListPanel.add(buttonPanel, BorderLayout.EAST);

        return fileListPanel;
    }

    private static JPanel initButtonPanel(final JTable table, final FileModel fileModel) {
        final JPanel panel = new JPanel();
        final JLabel countLabel = new JLabel();

        panel.setLayout(new GridLayout(10, 1));

        final JButton addButton = DialogUtils.CreateButton("addButton", "Add", null, panel);
        addButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                final File[] files = GetFilePath(addButton, "Add Product");
                if(files != null) {
                    for(File file : files) {
                        final ProductReader reader = ProductIO.getProductReaderForFile(file);
                        if (reader != null) {
                            fileModel.addFile(file);
                            countLabel.setText(fileModel.getRowCount()+" Products");
                        }
                    }
                }
            }
        });

        final JButton addAllOpenButton = DialogUtils.CreateButton("addAllOpenButton", "Add All Open", null, panel);
        addAllOpenButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                final Product[] products = VisatApp.getApp().getProductManager().getProducts();
                for(Product prod : products) {
                    final File file = prod.getFileLocation();
                    if(file != null && file.exists()) {
                        fileModel.addFile(file);
                    }
                }
                countLabel.setText(fileModel.getRowCount()+" Products");
            }
        });

        final JButton removeButton = DialogUtils.CreateButton("removeButton", "Remove", null, panel);
        removeButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                final int selRow = table.getSelectedRow();
                if(selRow >= 0) {
                    fileModel.removeFile(selRow);
                    countLabel.setText(fileModel.getRowCount()+" Products");
                }
            }

        });

        final JButton clearButton = DialogUtils.CreateButton("clearButton", "Clear", null, panel);
        clearButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                fileModel.clear();
                countLabel.setText("");
            }
        });

        panel.add(addButton);
        panel.add(addAllOpenButton);
        panel.add(removeButton);
        panel.add(clearButton);
        panel.add(countLabel);

        return panel;
    }

    private static File[] GetFilePath(Component component, String title) {

        File[] files = null;
        final File openDir = new File(VisatApp.getApp().getPreferences().
                getPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_OPEN_DIR, "."));
        final JFileChooser chooser = FileChooserFactory.getInstance().createFileChooser(openDir);
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle(title);
        if (chooser.showDialog(component, "ok") == JFileChooser.APPROVE_OPTION) {
            files = chooser.getSelectedFiles();

            VisatApp.getApp().getPreferences().
                setPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_OPEN_DIR, chooser.getCurrentDirectory().getAbsolutePath());
        }
        return files;
    }


    public static class FileModel extends AbstractTableModel {

        private final String titles[] = new String[]{
                "File Name", "Type", "Acquisition", "Track", "Orbit"
        };

        private final Class types[] = new Class[]{
                String.class, String.class, String.class, String.class, String.class
        };

        private final int widths[] = new int[]{
                75, 10, 20, 5, 5
        };

        private class FileStats {
            String data[] = new String[titles.length];
            FileStats(File file) {
                data[0] = file.getName();
            }
            FileStats(ProductEntry entry) {
                data[0] = entry.getName();
                data[1] = entry.getProductType();
                data[2] = entry.getFirstLineTime().format();
                data[3] = "";//entry.track;
                data[4] = "";//entry.orbit;
            }
        }

        private final ArrayList<File> fileList = new ArrayList<File>(10);
        private final ArrayList<FileStats> dataList = new ArrayList<FileStats>(10);

        public FileModel() {
            addBlankFile();
        }

        public ArrayList<File> getFileList() {
            return fileList;
        }

        public void addFile(final File file) {
            fileList.add(file);
            clearBlankFile();

            FileStats fstat = new FileStats(file);
            dataList.add(fstat);
            fireTableDataChanged();

            updateProductData(fstat, file);
        }

        public void addFile(final ProductEntry entry) {
            fileList.add(entry.getFile());
            clearBlankFile();

            FileStats fstat = new FileStats(entry);
            dataList.add(fstat);
            fireTableDataChanged();
        }

        public void removeFile(final int index) {
            fileList.remove(index);
            dataList.get(index).data = null;
            dataList.remove(index);

            fireTableDataChanged();
        }

        /**
         * Needed for drag and drop
         */
        private void addBlankFile() {
            addFile(new File(""));
        }

        private void clearBlankFile() {
            if(fileList.size() > 1 && fileList.get(0).getName().isEmpty()) {
                removeFile(0);
            }
        }

        public void clear() {
            fileList.clear();
            dataList.clear();
            addBlankFile();

            fireTableDataChanged();
        }

        // Implement the methods of the TableModel interface we're interested
        // in.  Only getRowCount(), getColumnCount() and getValueAt() are
        // required.  The other methods tailor the look of the table.
        public int getRowCount() {
            return dataList.size();
        }

        public int getColumnCount() {
            return titles.length;
        }

        @Override
        public String getColumnName(int c) {
            return titles[c];
        }

        @Override
        public Class getColumnClass(int c) {
            return types[c];
        }

        public Object getValueAt(int r, int c) {
            return dataList.get(r).data[c];
        }

        void setColumnWidths(TableColumnModel columnModel) {
            for(int i=0; i < widths.length; ++i) {
                columnModel.getColumn(i).setMinWidth(widths[i]);
                columnModel.getColumn(i).setPreferredWidth(widths[i]);
                columnModel.getColumn(i).setWidth(widths[i]);
            }
        }

        private void updateProductData(final FileStats fstat, final File file) {

            final SwingWorker worker = new SwingWorker() {
                @Override
                protected Object doInBackground() throws Exception {
                    try {
                        if(!file.getName().isEmpty()) {
                            try {
                                final Product product = ProductIO.readProduct(file);
                                final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);

                                fstat.data[0] = product.getName();
                                fstat.data[1] = product.getProductType();
                                fstat.data[2] = OperatorUtils.getAcquisitionDate(absRoot);
                                fstat.data[3] = String.valueOf(absRoot.getAttributeInt(AbstractMetadata.REL_ORBIT, 0));
                                fstat.data[4] = String.valueOf(absRoot.getAttributeInt(AbstractMetadata.ABS_ORBIT, 0));
                            } catch(Exception ex) {
                                fstat.data[0] = file.getName();
                                fstat.data[1] = "";
                                fstat.data[2] = "";
                                fstat.data[3] = "";
                                fstat.data[4] = "";
                            }
                        }
                    } finally {
                        fireTableDataChanged();
                    }
                    return null;
                }
            };
            worker.execute();
        }
    }


    public static class ProductSetTransferHandler extends TransferHandler {

        private final FileModel fileModel;

        public ProductSetTransferHandler(FileModel model) {
            fileModel = model;
        }

        @Override
        public boolean canImport(TransferHandler.TransferSupport info) {
            return info.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public int getSourceActions(JComponent c) {
            return TransferHandler.COPY;
        }

        /**
         * Perform the actual import 
         */
        @Override
        public boolean importData(TransferHandler.TransferSupport info) {
            if (!info.isDrop()) {
                return false;
            }

            // Get the string that is being dropped.
            final Transferable t = info.getTransferable();
            String data;
            try {
                data = (String) t.getTransferData(DataFlavor.stringFlavor);
            }
            catch (Exception e) {
                return false;
            }

            // Wherever there is a newline in the incoming data,
            // break it into a separate item in the list.
            final String[] values = data.split("\n");

            // Perform the actual import.
            for (String value : values) {

                final File file = new File(value);
                if(file.exists()) {
                    final ProductReader reader = ProductIO.getProductReaderForFile(file);
                    if (reader != null) {
                        fileModel.addFile(file);
                    }
                }
            }
            return true;
        }
    }
}