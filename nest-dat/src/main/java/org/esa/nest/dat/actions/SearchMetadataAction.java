
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
 * @version $Revision: 1.6 $ $Date: 2010-01-04 14:23:42 $
 */
public class SearchMetadataAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

        final PromptDialog dlg = new PromptDialog("Search Metadata", "tag", "", false);
        dlg.show();
        if(dlg.IsOK()) {
                final String tag = dlg.getValue().toUpperCase();
                final MetadataElement resultElem = new MetadataElement("Search result ("+dlg.getValue()+')');

               /* ProductMetadataView metadataView = VisatApp.getApp().getSelectedProductMetadataView();
                if(metadataView != null) {
                    final MetadataElement elem = metadataView.getMetadataElement();
                    resultElem.setOwner(elem.getProduct());
                    
                    searchMetadata(resultElem, elem, tag);

                } else {  */
                    final Product product = VisatApp.getApp().getSelectedProduct();
                    final MetadataElement root = product.getMetadataRoot();
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

    private static void searchMetadata(final MetadataElement resultElem, final MetadataElement elem, final String tag) {

            final MetadataElement[] elemList = elem.getElements();
            for(MetadataElement e : elemList) {
                searchMetadata(resultElem, e, tag);
            }
            final MetadataAttribute[] attribList = elem.getAttributes();
            for(MetadataAttribute attrib : attribList) {
                if(attrib.getName().toUpperCase().contains(tag)) {
                    final MetadataAttribute newAttrib = attrib.createDeepClone();
                    newAttrib.setDescription(getAttributePath(attrib));
                    resultElem.addAttribute(newAttrib);
                }
            }
    }

    private static String getAttributePath(final MetadataAttribute attrib) {
        MetadataElement parentElem = attrib.getParentElement();
        String path = parentElem.getName();
        while(parentElem != null && !parentElem.getName().equals("metadata")) {
            parentElem = parentElem.getParentElement();
            if(parentElem != null)
                path = parentElem.getName() + "/" + path;
        }
        return path;
    }
}