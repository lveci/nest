package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.datamodel.Unit;

/**
 * AmplitudeToIntensityOp action.
 *
 */
public class AmplitudeToIntensityOpAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {

        final VisatApp visatApp = VisatApp.getApp();

        final ProductNode node = visatApp.getSelectedProductNode();
        if(node instanceof Band) {
            final Product product = visatApp.getSelectedProduct();
            final Band band = (Band) node;
            String bandName = band.getName();
            String unit = band.getUnit();

            if(unit.contains(Unit.DB)) {
                visatApp.showWarningDialog("Please convert band " + bandName + " from dB to linear first");
                return;
            }

            if(unit.contains(Unit.AMPLITUDE)) {
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
            } else if(unit.contains(Unit.INTENSITY)) {

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
            if(unit != null && (unit.contains(Unit.AMPLITUDE) || unit.contains(Unit.INTENSITY))) {
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
            unit = Unit.AMPLITUDE;
        } else {
            expression = bandName + " * "+bandName+"))";
            bandName = bandName.replace("Amplitude", "Intensity");
            unit = Unit.INTENSITY;
        }

        final VirtualBand virtBand = new VirtualBand(bandName,
                ProductData.TYPE_FLOAT32,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        virtBand.setSynthetic(true);
        virtBand.setUnit(unit);
        virtBand.setDescription("");
        product.addBand(virtBand);
    }

}