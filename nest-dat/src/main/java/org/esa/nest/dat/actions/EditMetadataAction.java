
package org.esa.nest.dat.actions;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.datamodel.AbstractMetadata;

/**
 * This action to edit Metadata
 *
 * @author lveci
 * @version $Revision: 1.5 $ $Date: 2009-10-20 19:57:55 $
 */
public class EditMetadataAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

        final Product product = VisatApp.getApp().getSelectedProduct();
        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);

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