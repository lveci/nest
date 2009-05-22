package org.esa.nest.dat;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.datamodel.Unit;
import org.esa.nest.gpf.ASARCalibrationOperator;

/**
 * Sigma0toGamma0Action action.
 *
 */
public class Sigma0toGamma0Action extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {

        ASARCalibrationOperator.createGammaVirtualBand(VisatApp.getApp().getSelectedProduct(), false);
    }

    @Override
    public void updateState(CommandEvent event) {
        final ProductNode node = VisatApp.getApp().getSelectedProductNode();
        if(node instanceof Band) {
            final Band band = (Band) node;
            final String unit = band.getUnit();
            if(unit != null && unit.contains(Unit.INTENSITY) && band.getName().toLowerCase().contains("sigma")) {
                event.getCommand().setEnabled(true);
                return;
            }
        }
        event.getCommand().setEnabled(false);
    }

}