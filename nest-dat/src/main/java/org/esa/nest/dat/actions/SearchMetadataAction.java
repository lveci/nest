
package org.esa.nest.dat.actions;

import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.dialogs.PromptDialog;

/**
 * This action to searches the Metadata
 *
 * @author lveci
 * @version $Revision: 1.5 $ $Date: 2009-11-05 19:13:43 $
 */
public class SearchMetadataAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

        PromptDialog dlg = new PromptDialog("Search Metadata", "tag", "", false);
        dlg.show();
        if(dlg.IsOK()) {
                String tag = dlg.getValue().toUpperCase();
                MetadataElement resultElem = new MetadataElement("Search result ("+dlg.getValue()+')');

               /* ProductMetadataView metadataView = VisatApp.getApp().getSelectedProductMetadataView();
                if(metadataView != null) {
                    MetadataElement elem = metadataView.getMetadataElement();
                    resultElem.setOwner(elem.getProduct());
                    
                    searchMetadata(resultElem, elem, tag);

                } else {  */
                    Product product = VisatApp.getApp().getSelectedProduct();
                    MetadataElement root = product.getMetadataRoot();
                    resultElem.setOwner(product);

                    searchMetadata(resultElem, root, tag);
                //}

                if(resultElem.getNumElements() > 0 || resultElem.getNumAttributes() > 0) {
                    VisatApp.getApp().createProductMetadataView(resultElem);
                } else {
                    // no attributes found
                    VisatApp.getApp().showErrorDialog("Search Metadata", dlg.getValue() + " not found in the Metadata");
                }
        }
    }

    @Override
    public void updateState(final CommandEvent event) {
        final int n = VisatApp.getApp().getProductManager().getProductCount();
        setEnabled(n > 0);
    }

    private static void searchMetadata(MetadataElement resultElem, MetadataElement elem, String tag) {

            MetadataElement[] elemList = elem.getElements();
            for(MetadataElement e : elemList) {
                searchMetadata(resultElem, e, tag);
            }
            MetadataAttribute[] attribList = elem.getAttributes();
            for(MetadataAttribute attrib : attribList) {
                if(attrib.getName().toUpperCase().contains(tag))
                    resultElem.addAttribute(attrib);
            }
    }
}