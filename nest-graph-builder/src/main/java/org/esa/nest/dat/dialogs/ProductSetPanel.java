package org.esa.nest.dat.dialogs;

import com.bc.ceres.swing.TableLayout;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.BasicApp;
import org.esa.beam.util.SystemUtils;
import org.esa.nest.gpf.ProductSetReaderOpUI;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;

/**
 * NEST IO Panel to handle source and target selection
 * User: lveci
 * Date: Feb 5, 2009
 */
public class ProductSetPanel {

    private final AppContext appContext;
    private final ProductSetReaderOpUI.FileModel fileModel = new ProductSetReaderOpUI.FileModel();
    private final JTable productSetTable = new JTable(fileModel);
    private final TargetFolderSelector targetProductSelector;

    private String targetProductNameSuffix = "";

    ProductSetPanel(final AppContext theAppContext, final JTabbedPane tabbedPane) {
        this.appContext = theAppContext;

        final TableLayout tableLayout = new TableLayout(1);
        tableLayout.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        tableLayout.setTableWeightX(1.0);
        tableLayout.setTableFill(TableLayout.Fill.BOTH);
        tableLayout.setTablePadding(1, 1);

        final JComponent productSetContent = ProductSetReaderOpUI.createComponent(productSetTable, fileModel);
        final JPanel ioParametersPanel = new JPanel(tableLayout);
        ioParametersPanel.add(productSetContent);

        targetProductSelector = new TargetFolderSelector();
        String homeDirPath = SystemUtils.getUserHomeDir().getPath();
        String saveDir = appContext.getPreferences().getPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_SAVE_DIR, homeDirPath);
        targetProductSelector.getModel().setProductDir(new File(saveDir));
        targetProductSelector.getOpenInAppCheckBox().setText("Open in " + appContext.getApplicationName());

        ioParametersPanel.add(targetProductSelector.createPanel());

        tabbedPane.add("I/O Parameters", ioParametersPanel);
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
        //final String productDir = targetProductSelector.getModel().getProductDir().getAbsolutePath();
        //appContext.getPreferences().setPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_SAVE_DIR, productDir);
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

    public void setFileList(final File[] files) {
        fileModel.clear();
        for(File f : files) {
            fileModel.addFile(f);
        }
    }
}