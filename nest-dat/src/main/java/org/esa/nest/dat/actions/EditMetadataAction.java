
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.dat.toolviews.Projects.Project;

/**
 * This action to edit Metadata
 *
 * @author lveci
 * @version $Revision: 1.2 $ $Date: 2008-08-08 14:44:17 $
 */
public class EditMetadataAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

        Product product = VisatApp.getApp().getSelectedProduct();
        MetadataElement root = product.getMetadataRoot();
        MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);

        if(absRoot != null) {
            VisatApp.getApp().createProductMetadataView(absRoot);
        } else {
            // no attributes found
            VisatApp.getApp().showErrorDialog("Edit Metadata", "No editable metadata found.");
        }
    }

    @Override
    public void updateState(final CommandEvent event) {
        final int n = VisatApp.getApp().getProductManager().getProductCount();
        setEnabled(n > 0);
    }
}