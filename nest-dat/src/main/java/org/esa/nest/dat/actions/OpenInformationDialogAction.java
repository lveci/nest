package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.toolviews.stat.StatisticDialogHelper;
import org.esa.beam.visat.toolviews.stat.StatisticsToolView;
import org.esa.nest.dat.toolviews.DatStatisticsToolView;

public class OpenInformationDialogAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {
        StatisticsToolView statisticsToolView = (StatisticsToolView) VisatApp.getApp().getPage().getToolView(StatisticsToolView.ID);
        statisticsToolView.show(StatisticsToolView.INFORMATION_TAB_INDEX);
    }

    @Override
    public void updateState(final CommandEvent event) {
        StatisticDialogHelper.enableCommandIfProductSelected(VisatApp.getApp(), event);
    }
}