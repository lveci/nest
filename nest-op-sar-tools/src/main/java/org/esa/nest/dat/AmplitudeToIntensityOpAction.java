package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.beam.visat.VisatApp;

/**
 * AmplitudeToIntensityOp action.
 *
 */
public class AmplitudeToIntensityOpAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {

        VisatApp visatApp = VisatApp.getApp();

        final ProductNode node = visatApp.getSelectedProductNode();
        if(node instanceof Band) {
            final Product product = visatApp.getSelectedProduct();
            final Band band = (Band) node;
            String bandName = band.getName();
            String unit = band.getUnit();

            if(unit.toLowerCase().contains("db")) {
                visatApp.showWarningDialog("Please convert band " + bandName + " from dB to linear first");
                return;
            }

            if(unit.contains("amplitude")) {
                bandName = bandName.replace("Amplitude", "Intensity");
                if(product.getBand(bandName) != null) {
                    visatApp.showWarningDialog(product.getName() + " already contains a dB "
                        + bandName + " band");
                    return;
                }

                if(visatApp.showQuestionDialog("Convert to Intensity", "Would you like to convert band "
                        + band.getName() + " into Intensity in a new virtual band?", true, null) == 0) {
                    convert(product, band, false);
                }
            } else if(unit.contains("intensity")) {

                bandName = bandName.replace("Intensity", "Amplitude");
                if(product.getBand(bandName) != null) {
                    visatApp.showWarningDialog(product.getName() + " already contains a linear "
                        + bandName + " band");
                    return;
                }
                if(visatApp.showQuestionDialog("Convert to Amplitude", "Would you like to convert band "
                        + band.getName() + " into Amplitude in a new virtual band?", true, null) == 0) {
                    convert(product, band, true);
                }
            }
        }
    }

    @Override
    public void updateState(CommandEvent event) {
        final ProductNode node = VisatApp.getApp().getSelectedProductNode();
        if(node instanceof Band) {
            final Band band = (Band) node;
            final String unit = band.getUnit();
            if(unit != null && (unit.contains("amplitude") || unit.contains("intensity"))) {
                event.getCommand().setEnabled(true);
                return;
            }
        }
        event.getCommand().setEnabled(false);
    }

    static void convert(Product product, Band band, boolean toAmplitude) {
        String bandName = band.getName();
        String unit;

        String expression;
        if(toAmplitude) {
            expression = "sqrt(" + bandName + ')';
            bandName = bandName.replace("Intensity", "Amplitude");
            unit = "amplitude";
        } else {
            expression = bandName + " * "+bandName+"))";
            bandName = bandName.replace("Amplitude", "Intensity");
            unit = "intensity";
        }

        final VirtualBand virtBand = new VirtualBand(bandName,
                ProductData.TYPE_FLOAT64,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        virtBand.setSynthetic(true);
        virtBand.setUnit(unit);
        virtBand.setDescription("");
        product.addBand(virtBand);
    }

}