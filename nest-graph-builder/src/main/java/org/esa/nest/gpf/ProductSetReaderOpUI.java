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
import org.esa.nest.db.ProductDB;
import org.esa.nest.util.DialogUtils;
import org.esa.nest.util.ProductFunctions;
import org.esa.nest.dat.dialogs.FileModel;

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
        final JPanel panel = new JPanel(new GridLayout(10, 1));
        final JLabel countLabel = new JLabel();

        final JButton addButton = DialogUtils.CreateButton("addButton", "Add", null, panel);
        addButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                final File[] files = GetFilePath(addButton, "Add Product");
                if(files != null) {
                    for(File file : files) {
                        if (ProductFunctions.isValidProduct(file)) {
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
                final int[] selRows = table.getSelectedRows();
                final ArrayList<File> filesToRemove = new ArrayList<File>(selRows.length);
                for(int row : selRows) {
                    filesToRemove.add(fileModel.getFileAt(row));
                }
                for(File file : filesToRemove) {
                    int index = fileModel.getIndexOf(file);
                    fileModel.removeFile(index);
                }
                countLabel.setText(fileModel.getRowCount()+" Products");
            }

        });

        final JButton moveUpButton = DialogUtils.CreateButton("moveUpButton", "Move Up", null, panel);
        moveUpButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                final int[] selRows = table.getSelectedRows();
                final ArrayList<File> filesToMove = new ArrayList<File>(selRows.length);
                for(int row : selRows) {
                    filesToMove.add(fileModel.getFileAt(row));
                }
                for(File file : filesToMove) {
                    int index = fileModel.getIndexOf(file);
                    if(index > 0) {
                        fileModel.move(index, index-1);
                    }
                }
            }

        });

        final JButton moveDownButton = DialogUtils.CreateButton("moveDownButton", "Move Down", null, panel);
        moveDownButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                final int[] selRows = table.getSelectedRows();
                final ArrayList<File> filesToMove = new ArrayList<File>(selRows.length);
                for(int row : selRows) {
                    filesToMove.add(fileModel.getFileAt(row));
                }
                for(File file : filesToMove) {
                    int index = fileModel.getIndexOf(file);
                    if(index < fileModel.getRowCount()) {
                        fileModel.move(index, index+1);
                    }
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
        panel.add(moveUpButton);
        panel.add(moveDownButton);
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

    public void setProductFileList(final File[] productFileList) {
        fileModel.clear();
        for(File file : productFileList) {
            fileModel.addFile(file);
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
                    if (ProductFunctions.isValidProduct(file)) {
                        fileModel.addFile(file);
                    }
                }
            }
            return true;
        }
    }
}