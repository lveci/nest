
package org.esa.nest.dat.actions;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.datamodel.AbstractMetadataIO;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.util.ResourceUtils;
import org.esa.nest.dat.dialogs.ProductSelectorDialog;

import java.io.File;
import java.util.ArrayList;

/**
 * This action replaces the Metadata with that of another product
 *
 * @author lveci
 * @version $Revision: 1.1 $ $Date: 2009-10-14 19:16:21 $
 */
public class ReplaceMetadataAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

        final Product destProduct = VisatApp.getApp().getSelectedProduct();
        final String[] compatibleProductNames = getCompatibleProducts(destProduct);
        if(compatibleProductNames.length == 0) {
            VisatApp.getApp().showErrorDialog("There are not any compatible products currently opened\nDimensions must be the same");
            return;
        }

        final ProductSelectorDialog dlg = new ProductSelectorDialog("Replace Metadata with", compatibleProductNames);
        dlg.show();
        if(dlg.IsOK()) {
            try {
                final String srcProductName = dlg.getSelectedProductName();
                final Product[] products = VisatApp.getApp().getProductManager().getProducts();

                Product srcProduct = null;
                for(Product prod : products) {
                    if(prod.getDisplayName().equals(srcProductName)) {
                        srcProduct = prod;
                        break;
                    }
                }

                final MetadataElement srcAbsRoot = AbstractMetadata.getAbstractedMetadata(srcProduct);
                final File tmpMetadataFile = new File(ResourceUtils.getApplicationUserTempDataDir(),
                        srcProduct.getName() + "_metadata.xml");
                AbstractMetadataIO.Save(srcProduct, srcAbsRoot, tmpMetadataFile);

                VisatApp.getApp().closeAllAssociatedFrames(destProduct);
                clearProductMetadata(destProduct);
                VisatApp.getApp().removeProduct(destProduct);

                final MetadataElement destAbsRoot = AbstractMetadata.getAbstractedMetadata(destProduct);
                AbstractMetadataIO.Load(destProduct, destAbsRoot, tmpMetadataFile);                
                VisatApp.getApp().addProduct(destProduct);

                tmpMetadataFile.delete();
            } catch(Exception e) {
                VisatApp.getApp().showErrorDialog("Unable to save or load metadata\n"+e.getMessage());
            }
        }
    }

    @Override
    public void updateState(final CommandEvent event) {
        final Product product = VisatApp.getApp().getSelectedProduct();
        setEnabled(product != null);
    }

    private static String[] getCompatibleProducts(final Product destProduct) {
        final ArrayList<String> prodList = new ArrayList<String>();

        final Product[] products = VisatApp.getApp().getProductManager().getProducts();
        for(Product p : products) {
            if(p != destProduct &&
               p.getSceneRasterWidth() == destProduct.getSceneRasterWidth() &&
               p.getSceneRasterHeight() == destProduct.getSceneRasterHeight()) {
                prodList.add(p.getDisplayName());
            }
        }
        return prodList.toArray(new String[prodList.size()]);
    }

    private static void clearProductMetadata(final Product product) {
        final String[] tpgNames = product.getTiePointGridNames();
        for(String tpg : tpgNames) {
            product.removeTiePointGrid(product.getTiePointGrid(tpg));
        }

        final MetadataElement root = product.getMetadataRoot();
        final MetadataElement[] elems = root.getElements();
        for(MetadataElement e : elems) {
            root.removeElement(e);
        }
        AbstractMetadata.addAbstractedMetadataHeader(product.getMetadataRoot());
    }

}