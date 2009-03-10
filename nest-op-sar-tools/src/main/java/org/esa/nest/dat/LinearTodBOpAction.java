package org.esa.nest.dat;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.datamodel.Unit;

/**
 * LinearTodB action.
 *
 */
public class LinearTodBOpAction extends AbstractVisatAction {

    private static final String dBStr = "_"+Unit.DB;

    @Override
    public void actionPerformed(CommandEvent event) {

        final VisatApp visatApp = VisatApp.getApp();

        final ProductNode node = visatApp.getSelectedProductNode();
        if(node instanceof Band) {
            final Product product = visatApp.getSelectedProduct();
            final Band band = (Band) node;
            String bandName = band.getName();
            final String unit = band.getUnit();

            if(!unit.contains(Unit.DB)) {
                bandName += dBStr;
                if(product.getBand(bandName) != null) {
                    visatApp.showWarningDialog(product.getName() + " already contains a dB "
                        + bandName + " band");
                    return;
                }

                if(visatApp.showQuestionDialog("Convert to dB", "Would you like to convert band "
                        + band.getName() + " into dB in a new virtual band?", true, null) == 0) {
                    convert(product, band, true);
                }
            } else {

                bandName = bandName.substring(0, bandName.indexOf(dBStr));
                if(product.getBand(bandName) != null) {
                    visatApp.showWarningDialog(product.getName() + " already contains a linear "
                        + bandName + " band");
                    return;
                }
                if(visatApp.showQuestionDialog("Convert to linear", "Would you like to convert band "
                        + band.getName() + " into linear in a new virtual band?", true, null) == 0) {
                    convert(product, band, false);
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
            if(unit != null && !unit.contains(Unit.PHASE)) {
                event.getCommand().setEnabled(true);
                return;
            }
        }
        event.getCommand().setEnabled(false);
    }

    static void convert(Product product, Band band, boolean todB) {
        String bandName = band.getName();
        String unit = band.getUnit();

        String expression;
        if(todB) {
            expression = bandName + "==0 ? 0 : 10 * log10(abs("+bandName+"))";
            bandName += dBStr;
            unit += dBStr;
        } else {
            expression = "pow(10," + bandName + "/10.0)";
            bandName = bandName.substring(0, bandName.indexOf(dBStr));
            unit = unit.substring(0, unit.indexOf(dBStr));
        }

        final VirtualBand virtBand = new VirtualBand(bandName,
                ProductData.TYPE_FLOAT32,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        virtBand.setSynthetic(true);
        virtBand.setUnit(unit);
        virtBand.setDescription(band.getDescription());
        product.addBand(virtBand);
    }

}