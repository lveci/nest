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
package org.esa.nest.dat.dialogs;

import org.esa.nest.db.ProductEntry;
import org.esa.nest.gpf.ProductSetReaderOpUI;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * List of Products
 */
public class ProductListPanel extends JPanel {

    private final FileModel fileModel = new FileModel();
    private final JTable table = new JTable(fileModel);
    private final static int width = 500;
    private final static int height = 100;

    public ProductListPanel(final String title) {
        this();
        setBorder(BorderFactory.createTitledBorder(title));
    }

    public ProductListPanel() {
        super(new BorderLayout());

        table.setPreferredScrollableViewportSize(new Dimension(width, height));
        fileModel.setColumnWidths(table.getColumnModel());
        table.setColumnSelectionAllowed(true);
        table.setDropMode(DropMode.ON);
        table.setTransferHandler(new ProductSetReaderOpUI.ProductSetTransferHandler(fileModel));

        final JScrollPane scrollPane = new JScrollPane(table);
        this.add(scrollPane, BorderLayout.CENTER);
    }

    public File[] getSelectedFiles() {
        return fileModel.getFilesAt(table.getSelectedRows());
    }

    public File[] getFileList() {
        final List<File> fileList = fileModel.getFileList();
        return fileList.toArray(new File[fileList.size()]);
    }

    public Object getValueAt(final int r, final int c) {
        return fileModel.getValueAt(r, c);
    }

    public void setProductFileList(final File[] productFileList) {
        fileModel.clear();
        for(File file : productFileList) {
            fileModel.addFile(file);
        }
    }

    public void setProductEntryList(final ProductEntry[] productEntryList) {
        fileModel.clear();
        for(ProductEntry entry : productEntryList) {
            fileModel.addFile(entry);
        }
    }
}