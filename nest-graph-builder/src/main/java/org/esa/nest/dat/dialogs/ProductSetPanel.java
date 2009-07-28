package org.esa.nest.dat.dialogs;

import com.bc.ceres.swing.TableLayout;
import org.esa.beam.framework.ui.AppContext;
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

    private String targetProductNameSuffix = "";

    ProductSetPanel(final AppContext theAppContext, final JTabbedPane tabbedPane) {
        this.appContext = theAppContext;

        final TableLayout tableLayout = new TableLayout(1);
        tableLayout.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        tableLayout.setTableWeightX(1.0);
        tableLayout.setTableFill(TableLayout.Fill.BOTH);
        tableLayout.setTablePadding(1, 1);

        final JComponent content = ProductSetReaderOpUI.createComponent(productSetTable, fileModel);
        final JPanel ioParametersPanel = new JPanel(tableLayout);

        ioParametersPanel.add(content);
        tabbedPane.add("I/O Parameters", ioParametersPanel);
    }

    public void setTargetProductName(final String name) {
        //final TargetProductSelectorModel targetProductSelectorModel = targetProductSelector.getModel();
        //targetProductSelectorModel.setProductName(name + getTargetProductNameSuffix());
    }

    public void initProducts() {

    }

    public void releaseProducts() {

    }

    public void onApply() {
        //final String productDir = targetProductSelector.getModel().getProductDir().getAbsolutePath();
        //appContext.getPreferences().setPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_SAVE_DIR, productDir);
    }

    public String getTargetProductNameSuffix() {
        return targetProductNameSuffix;
    }

    public void setTargetProductNameSuffix(final String suffix) {
        targetProductNameSuffix = suffix;
    }

    public File getTargetFolder() {
        return new File("C:\\data\\output");
    }

    public File[] getFileList() {
        final ArrayList<File> fileList = fileModel.getFileList();
        return fileList.toArray(new File[fileList.size()]);
    }
}