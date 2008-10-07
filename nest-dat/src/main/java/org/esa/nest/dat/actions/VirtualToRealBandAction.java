package org.esa.nest.dat.actions;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.util.Debug;

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

    @Override
    public void actionPerformed(final CommandEvent event) {

        final Product targetProduct = VisatApp.getApp().getSelectedProduct();
        final String srcBandName = VisatApp.getApp().getSelectedProductNode().getName();
        final Band srcBand = targetProduct.getBand(srcBandName);
        convertVirtualToRealBand(targetProduct, srcBand, VisatApp.getApp());
    }

    @Override
    public void updateState(final CommandEvent event) {
        final ProductNode selectedProductNode = VisatApp.getApp().getSelectedProductNode();
        final RasterDataNode raster = selectedProductNode instanceof RasterDataNode ? (RasterDataNode) selectedProductNode : null;

        event.getSelectableCommand().setEnabled(raster != null && raster.isSynthetic());
    }

    protected static void convertVirtualToRealBand(final Product targetProduct, final Band srcBand,
                                                   final VisatApp visatApp) {

        final Band targetBand = new Band("new_"+srcBand.getName(), srcBand.getDataType(),
                srcBand.getSceneRasterWidth(), srcBand.getSceneRasterHeight());

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
            VisatApp.getApp().showOutOfMemoryErrorDialog("The new band could not be created.");
            targetProduct.removeBand(targetProduct.getBand(targetBand.getName()));
            return;
        }

        final String expression = srcBand.getName();
        targetBand.setSynthetic(true);

        if(visatApp != null) {
            final ProgressMonitor pm = new DialogProgressMonitor(visatApp.getMainFrame(),
                    "Converting Virtual Band to Real",
                    Dialog.ModalityType.APPLICATION_MODAL);

            SwingWorker swingWorker = new SwingWorker() {

                String _errorMessage;

                @Override
                protected Object doInBackground() throws Exception {
                    _errorMessage = computeBand(targetProduct, targetBand, expression,
                            noDataValue, pm);
                    return null;
                }

                @Override
                public void done() {
                    boolean ok = true;
                    if (_errorMessage != null) {
                        if(visatApp != null)
                            visatApp.showErrorDialog(_errorMessage);
                        ok = false;
                    } else if (pm.isCanceled()) {
                        if(visatApp != null)
                            visatApp.showErrorDialog("Operation has been canceled.");
                        ok = false;
                    }

                    if(ok) {
                        targetBand.setModified(true);
                        targetProduct.removeBand(srcBand);
                        targetBand.setName(srcBand.getName());
                    } else {
                        targetProduct.removeBand(targetProduct.getBand(targetBand.getName()));
                    }
                }
            };

            swingWorker.execute();

        } else {
            final ProgressMonitor pm = ProgressMonitor.NULL;

            String _errorMessage = computeBand(targetProduct, targetBand, expression,
                            noDataValue, pm);

            if (_errorMessage == null) {
                targetBand.setModified(true);
                targetProduct.removeBand(srcBand);
                targetBand.setName(srcBand.getName());
            } else {
                targetProduct.removeBand(targetProduct.getBand(targetBand.getName()));
            }
        }

    }

    protected static String computeBand(Product targetProduct, Band targetBand, String expression,
                                        double noDataValue, ProgressMonitor pm) {
        String errorMessage = null;
        try {
            targetBand.computeBand(expression,
                    new Product[] {targetProduct},
                    false,
                    false,
                    noDataValue,
                    pm);
            targetBand.fireProductNodeDataChanged();
        } catch (IOException e) {
            Debug.trace(e);
            errorMessage = "The band could not be created.\nAn I/O error occurred:\n" + e.getMessage();
        } catch (ParseException e) {
            Debug.trace(e);
            errorMessage = "The band could not be created.\nAn expression parse error occurred:\n" + e.getMessage();
        } catch (EvalException e) {
            Debug.trace(e);
            errorMessage = "The band could not be created.\nAn expression evaluation error occured:\n" + e.getMessage();
        }
        return errorMessage;
    }
}