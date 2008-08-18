package org.esa.nest.dat.actions;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.dialogs.BandArithmetikDialog;
import org.esa.beam.util.Debug;
import org.esa.beam.util.math.MathUtils;

import javax.swing.*;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.swing.progress.DialogProgressMonitor;
import com.bc.jexp.ParseException;
import com.bc.jexp.EvalException;

import java.awt.*;
import java.io.IOException;

/**
 * Convert a virtual band into a real band easily
 *
 */
public class VirtualToRealBandAction extends ExecCommand {

    private BandArithmetikDialog _bandArithmetikDialog;

    @Override
    public void actionPerformed(final CommandEvent event) {
        openBandArithmeticDialog(VisatApp.getApp(), event.getCommand().getHelpId());
    }

    @Override
    public void updateState(final CommandEvent event) {
        final ProductNode selectedProductNode = VisatApp.getApp().getSelectedProductNode();
        final RasterDataNode raster = selectedProductNode instanceof RasterDataNode ? (RasterDataNode) selectedProductNode : null;

        event.getSelectableCommand().setEnabled(raster != null && raster.isSynthetic());
    }

    private void openBandArithmeticDialog(final VisatApp visatApp, final String helpId) {

        final Product targetProduct = visatApp.getSelectedProduct();
        final String srcBandName = visatApp.getSelectedProductNode().getName();
        final Band srcBand = targetProduct.getBand(srcBandName);
        final Band targetBand = new Band("new_"+srcBandName, srcBand.getDataType(),
                srcBand.getSceneRasterWidth(), srcBand.getSceneRasterHeight());

        final boolean checkInvalids = false;
        final boolean noDataValueUsed = false;
        final double noDataValue = srcBand.getNoDataValue();

        targetBand.setImageInfo(null);
        targetBand.setGeophysicalNoDataValue(noDataValue);
        targetBand.setNoDataValueUsed(noDataValueUsed);
        if (!targetProduct.containsBand(targetBand.getName())) {
            targetProduct.addBand(targetBand);
        }

        try {
            targetBand.createCompatibleRasterData();
        } catch (OutOfMemoryError e) {
            visatApp.showOutOfMemoryErrorDialog("The new band could not be created.");
        }

        final String expression = srcBandName;
        targetBand.setSynthetic(true);

        SwingWorker swingWorker = new SwingWorker() {
            final ProgressMonitor pm = new DialogProgressMonitor(visatApp.getMainFrame(),
                    "Converting Virutal Band to Real",
                    Dialog.ModalityType.APPLICATION_MODAL);

            String _errorMessage;
            int _numInvalids;

            @Override
            protected Object doInBackground() throws Exception {
                _errorMessage = null;
                try {
                    _numInvalids = targetBand.computeBand(expression,
                                                           new Product[] {targetProduct},
                                                           checkInvalids,
                                                           noDataValueUsed,
                                                           noDataValue,
                                                           pm);
                    targetBand.fireProductNodeDataChanged();
                } catch (IOException e) {
                    Debug.trace(e);
                    _errorMessage = "The band could not be created.\nAn I/O error occurred:\n" + e.getMessage();
                } catch (ParseException e) {
                    Debug.trace(e);
                    _errorMessage = "The band could not be created.\nAn expression parse error occurred:\n" + e.getMessage();
                } catch (EvalException e) {
                    Debug.trace(e);
                    _errorMessage = "The band could not be created.\nAn expression evaluation error occured:\n" + e.getMessage();
                } finally {
                }
                return null;

            }

            @Override
            public void done() {
                boolean ok = true;
                if (_errorMessage != null) {
                    visatApp.showErrorDialog(_errorMessage);
                    ok = false;
                } else if (pm.isCanceled()) {
                    visatApp.showErrorDialog("Band arithmetic has been canceled.");
                    ok = false;
                }

                if(ok) {
                    targetBand.setModified(true);
                    targetProduct.removeBand(srcBand);
                    targetBand.setName(srcBandName);
                } else {
                    targetProduct.removeBand(targetProduct.getBand(targetBand.getName()));
                }
            }
        };

        swingWorker.execute();
    }
}