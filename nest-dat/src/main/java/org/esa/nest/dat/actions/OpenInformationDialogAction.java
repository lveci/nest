package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.SharedApp;
import org.esa.beam.visat.toolviews.stat.StatisticDialogHelper;
import org.esa.beam.visat.toolviews.stat.StatisticsToolView;
import org.esa.nest.dat.toolviews.DatStatisticsToolView;

public class OpenInformationDialogAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {
        DatStatisticsToolView statisticsToolView = (DatStatisticsToolView) SharedApp.instance().getApp().getPage().getToolView(DatStatisticsToolView.ID);
        statisticsToolView.show(DatStatisticsToolView.INFORMATION_TAB_INDEX);
    }

    @Override
    public void updateState(final CommandEvent event) {
        StatisticDialogHelper.enableCommandIfProductSelected(SharedApp.instance().getApp(), event);
    }
}