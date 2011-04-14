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

import com.bc.ceres.swing.TableLayout;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.BasicApp;
import org.esa.beam.util.SystemUtils;
import org.esa.nest.db.ProductEntry;
import org.esa.nest.gpf.ProductSetReaderOpUI;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;

/**
 * NEST IO Panel to handle source and target selection
 * User: lveci
 * Date: Feb 5, 2009
 */
public class ProductSetPanel extends JPanel {

    private final ProductSetReaderOpUI.FileModel fileModel = new ProductSetReaderOpUI.FileModel();
    private final TargetFolderSelector targetProductSelector;
    private final AppContext appContext;
    private String targetProductNameSuffix = "";

    public ProductSetPanel(final AppContext theAppContext) {
        super(new BorderLayout());
        this.appContext = theAppContext;
        
        final JTable productSetTable = new JTable(fileModel);
        final JComponent productSetContent = ProductSetReaderOpUI.createComponent(productSetTable, fileModel);
        this.add(productSetContent, BorderLayout.CENTER);

        targetProductSelector = new TargetFolderSelector();
        final String homeDirPath = SystemUtils.getUserHomeDir().getPath();
        final String saveDir = theAppContext.getPreferences().getPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_SAVE_DIR, homeDirPath);
        targetProductSelector.getModel().setProductDir(new File(saveDir));
        targetProductSelector.getOpenInAppCheckBox().setText("Open in " + theAppContext.getApplicationName());
        targetProductSelector.getOpenInAppCheckBox().setVisible(false);

        this.add(targetProductSelector.createPanel(), BorderLayout.SOUTH);
    }

    public void setTargetProductName(final String name) {
        final TargetProductSelectorModel targetProductSelectorModel = targetProductSelector.getModel();
        targetProductSelectorModel.setProductName(name + getTargetProductNameSuffix());
    }

    public void initProducts() {

    }

    public void releaseProducts() {

    }

    public void onApply() {
        final String productDir = targetProductSelector.getModel().getProductDir().getAbsolutePath();
        appContext.getPreferences().setPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_SAVE_DIR, productDir);
    }

    String getTargetProductNameSuffix() {
        return targetProductNameSuffix;
    }

    public void setTargetProductNameSuffix(final String suffix) {
        targetProductNameSuffix = suffix;
    }

    public File getTargetFolder() {
        final TargetProductSelectorModel targetProductSelectorModel = targetProductSelector.getModel();

        return targetProductSelectorModel.getProductDir();
    }

    public void setTargetFolder(final File path) {
        final TargetProductSelectorModel targetProductSelectorModel = targetProductSelector.getModel();
        targetProductSelectorModel.setProductDir(path);
    }

    public File[] getFileList() {
        final ArrayList<File> fileList = fileModel.getFileList();
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