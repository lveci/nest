package org.esa.nest.dat.dialogs;

import org.esa.beam.framework.gpf.ui.TargetProductSelector;
import org.esa.beam.framework.gpf.ui.SourceProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.framework.ui.BasicApp;
import org.esa.beam.framework.ui.TableLayout;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.application.SelectionChangeListener;
import org.esa.beam.framework.ui.application.SelectionChangeEvent;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.util.SystemUtils;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;

/**
 * NEST
 * User: lveci
 * Date: Feb 5, 2009
 */
public class IOPanel {

    private final AppContext appContext;
    private final TargetProductSelector targetProductSelector;
    private final boolean useSourceSelector;
    private final ArrayList<SourceProductSelector> sourceProductSelectorList = new ArrayList<SourceProductSelector>(3);
    private String targetProductNameSuffix = "";

    IOPanel(final AppContext theAppContext, final JTabbedPane tabbedPane, boolean createSourceSelector) {
        this.appContext = theAppContext;
        this.useSourceSelector = createSourceSelector;

        targetProductSelector = new TargetProductSelector();
        final String homeDirPath = SystemUtils.getUserHomeDir().getPath();
        final String saveDir = appContext.getPreferences().getPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_SAVE_DIR, homeDirPath);
        targetProductSelector.getModel().setProductDir(new File(saveDir));
        targetProductSelector.getOpenInAppCheckBox().setText("Open in " + appContext.getApplicationName());

        final TableLayout tableLayout = new TableLayout(1);
        tableLayout.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        tableLayout.setTableWeightX(1.0);
        tableLayout.setTableFill(TableLayout.Fill.BOTH);
        tableLayout.setTablePadding(1, 1);

        final JPanel ioParametersPanel = new JPanel(tableLayout);

        if(useSourceSelector) {
            // Fetch source products
            sourceProductSelectorList.add(new SourceProductSelector(appContext));

            for (SourceProductSelector selector : sourceProductSelectorList) {
                ioParametersPanel.add(selector.createDefaultPanel());
            }
            ioParametersPanel.add(tableLayout.createVerticalSpacer());
            sourceProductSelectorList.get(0).addSelectionChangeListener(new SelectionChangeListener() {
                public void selectionChanged(SelectionChangeEvent event) {
                    final Product selectedProduct = (Product) event.getSelection().getFirstElement();
                    final TargetProductSelectorModel targetProductSelectorModel = targetProductSelector.getModel();
                    targetProductSelectorModel.setProductName(selectedProduct.getName() + getTargetProductNameSuffix());
                }
            });
        }

        ioParametersPanel.add(targetProductSelector.createDefaultPanel());
        if(useSourceSelector) {
            tabbedPane.add("I/O Parameters", ioParametersPanel);
        } else {
            tabbedPane.add("Target Product", ioParametersPanel);
        }
    }

    public void setTargetProductName(final String name) {
        final TargetProductSelectorModel targetProductSelectorModel = targetProductSelector.getModel();
        targetProductSelectorModel.setProductName(name + getTargetProductNameSuffix());        
    }

    public void initProducts() {
        if(useSourceSelector) {
            for (SourceProductSelector sourceProductSelector : sourceProductSelectorList) {
                sourceProductSelector.initProducts();
            }
        }
    }

    public void releaseProducts() {
        if(!useSourceSelector) {
            for (SourceProductSelector sourceProductSelector : sourceProductSelectorList) {
                sourceProductSelector.releaseProducts();
            }
        }
    }

    public void onApply() {
        final String productDir = targetProductSelector.getModel().getProductDir().getAbsolutePath();
        appContext.getPreferences().setPropertyString(BasicApp.PROPERTY_KEY_APP_LAST_SAVE_DIR, productDir);
    }

    public Product getSelectedSourceProduct() {
        if(useSourceSelector)
            return sourceProductSelectorList.get(0).getSelectedProduct();
        return null;
    }

    public File getTargetFile() {
        return targetProductSelector.getModel().getProductFile();
    }

    public String getTargetFormat() {
        return targetProductSelector.getModel().getFormatName();
    }

    public String getTargetProductNameSuffix() {
        return targetProductNameSuffix;
    }

    public void setTargetProductNameSuffix(final String suffix) {
        targetProductNameSuffix = suffix;
    }

    public boolean isOpenInAppSelected() {
        return targetProductSelector.getModel().isOpenInAppSelected();
    }
}
