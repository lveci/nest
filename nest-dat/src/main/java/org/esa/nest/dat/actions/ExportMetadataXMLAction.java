package org.esa.nest.dat.actions;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.framework.ui.product.ProductMetadataTable;
import org.esa.beam.framework.ui.product.ProductMetadataView;
import org.esa.beam.framework.ui.product.ProductNodeView;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.dat.dialogs.ProductSelectorDialog;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.AbstractMetadataIO;
import org.esa.nest.util.ResourceUtils;

import java.io.File;
import java.util.ArrayList;

/**
 * This action replaces the Metadata with that of another product
 *
 * @author lveci
 * @version $Revision: 1.1 $ $Date: 2010-02-16 14:35:58 $
 */
public class ExportMetadataXMLAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {
        try {
            final ProductNodeView view = VisatApp.getApp().getSelectedProductNodeView();
            if (!(view instanceof ProductMetadataView)) {
                return;
            }

            final ProductMetadataView productMetadataView = (ProductMetadataView) view;
            final ProductMetadataTable metadataTable = productMetadataView.getMetadataTable();
            final MetadataElement root = metadataTable.getMetadataElement();
            final Product srcProduct = productMetadataView.getProduct();

            final String fileName = srcProduct.getName() + "_metadata.xml";
            final File file = ResourceUtils.GetFilePath("Save Metadata", "XML", "xml", fileName, "Metadata XML File", true);
            if(file == null) return;

            AbstractMetadataIO.Save(srcProduct, root, file);
        } catch(Exception e) {
            VisatApp.getApp().showErrorDialog("Unable to save metadata\n"+e.getMessage());
        }
    }

    /**
     * Called when a command should update its state.
     * <p/>
     * <p> This method can contain some code which analyzes the underlying element and makes a decision whether
     * this item or group should be made visible/invisible or enabled/disabled etc.
     *
     * @param event the command event
     */
    @Override
    public void updateState(CommandEvent event) {
        ProductNodeView view = VisatApp.getApp().getSelectedProductNodeView();
        setEnabled(view instanceof ProductMetadataView);
    }

}