
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.dat.toolviews.Projects.Project;
import org.esa.nest.dat.dialogs.OrbitFileDialog;
import org.esa.nest.dataio.ApplyOrbitFile;

/**
 * This action to apply orbit file
 *
 * @author lveci
 * @version $Revision: 1.3 $ $Date: 2008-09-08 21:00:11 $
 */
public class ApplyOrbitFileAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {
        
        final Product partentProduct = VisatApp.getApp().getSelectedProduct();
        final OrbitFileDialog dialog = new OrbitFileDialog(VisatApp.getApp().getMainFrame(), partentProduct, getHelpId());
        if (dialog.show() == ModalDialog.ID_OK) {
            final Product product = dialog.getResultProduct();
            if (product != null) {
                VisatApp.getApp().addProduct(product);
            } else if (dialog.getException() != null) {
                VisatApp.getApp().showErrorDialog("The flipped product could not be created:\n" +
                                         dialog.getException().getMessage());
            }
        }
    }

    @Override
    public void updateState(final CommandEvent event) {
        final Product product = VisatApp.getApp().getSelectedProduct();
        setEnabled(product != null && product.getNumBands() + product.getNumTiePointGrids() > 0);
    }

}