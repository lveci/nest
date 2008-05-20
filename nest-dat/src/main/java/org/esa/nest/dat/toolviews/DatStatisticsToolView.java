package org.esa.nest.dat.toolviews;

import org.esa.beam.framework.help.HelpSys;
import org.esa.beam.framework.ui.application.support.AbstractToolView;
import org.esa.beam.util.Debug;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.toolviews.stat.*;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;


/**
 * The window containing all statistics.
 *
 * @author Marco Peters
 */
public class DatStatisticsToolView extends AbstractToolView {

    public static final String ID = DatStatisticsToolView.class.getName();

    public static final int INFORMATION_TAB_INDEX = 0;
    public static final int GEOCODING_TAB_INDEX = 1;
    public static final int QUICKLOOK_TAB_INDEX = 2;
    public static final int STATISTICS_TAB_INDEX = 3;
    public static final int HISTOGRAM_TAB_INDEX = 4;
    public static final int SCATTERPLOT_TAB_INDEX = 5;
    public static final int PROFILEPLOT_TAB_INDEX = 6;
    public static final int COORDLIST_TAB_INDEX = 7;

    private static String[] _helpIDs = {
            "informationDialog",
            "geoCodingInfoDialog",
            "quicklookDialog",
            "statisticsDialog",
            "histogramDialog",
            "scatterplotDialog",
            "profilePlotDialog",
            "coordinateListDialog"
    };

    public static final Color DIAGRAM_BG_COLOR = new Color(200, 200, 255);
    public static final Color DIAGRAM_FG_COLOR = new Color(0, 0, 100);
    public static final Color DIAGRAM_TEXT_COLOR = Color.black;
    public static final int DIAGRAM_MIN_INSETS = 5;

    private int _currTabIndex;

    private JTabbedPane _tabbedPane;
    // todo - reanimate help for each pane
    //  private JButton _helpButton;


    public DatStatisticsToolView() {
    }

    public void show(final int tabIndex) {
        VisatApp.getApp().getPage().showToolView(DatStatisticsToolView.ID);
        if (!isValidTabIndex(tabIndex)) {
            throw new IllegalArgumentException("illegal tab-index");
        }
        _currTabIndex = tabIndex;
        _tabbedPane.setSelectedIndex(tabIndex);
    }

    @Override
    public JComponent createControl() {

        _tabbedPane = new JTabbedPane();
        _tabbedPane.add("Information", new InformationPanel(this)); /*I18N*/
        _tabbedPane.add("Quicklook", new QuicklookPanel(this));/*I18N*/
        _tabbedPane.add("Geo-Coding", new GeoCodingPanel(this));/*I18N*/
        _tabbedPane.add("Statistics", new StatisticsPanel(this)); /*I18N*/
        _tabbedPane.add("Histogram", new HistogramPanel(this));  /*I18N*/
        _tabbedPane.add("Scatter Plot", new ScatterPlotPanel(this)); /*I18N*/
        _tabbedPane.add("Profile Plot", new ProfilePlotPanel(this));  /*I18N*/
        _tabbedPane.add("Co-ordinate List", new CoordListPanel(this));  /*I18N*/

        _tabbedPane.addChangeListener(new ChangeListener() {

            public void stateChanged(final ChangeEvent e) {
                if (_tabbedPane.getSelectedIndex() != _currTabIndex) {
                    _currTabIndex = _tabbedPane.getSelectedIndex();
                    updateUIState();
                    updateHelpBroker();
                }
            }
        });

        updateUIState();
        updateHelpBroker();
        return _tabbedPane;
    }

    private static boolean isValidTabIndex(final int tabIndex) {
        return tabIndex == INFORMATION_TAB_INDEX ||
               tabIndex == STATISTICS_TAB_INDEX ||
               tabIndex == QUICKLOOK_TAB_INDEX ||
               tabIndex == HISTOGRAM_TAB_INDEX ||
               tabIndex == SCATTERPLOT_TAB_INDEX ||
               tabIndex == PROFILEPLOT_TAB_INDEX ||
               tabIndex == COORDLIST_TAB_INDEX ||
               tabIndex == GEOCODING_TAB_INDEX;
    }

    private void updateHelpBroker() {
        Debug.assertTrue(_currTabIndex >= 0 && _currTabIndex < _helpIDs.length);
        setCurrentHelpID(_helpIDs[_currTabIndex]);
    }

    private void setCurrentHelpID(final String helpID) {
        //HelpSys.enableHelpKey(getContentPane(), helpID);
        //HelpSys.enableHelpKey(_tabbedPane, helpID);
        //HelpSys.getHelpBroker().setCurrentID(helpID);
    }

    private void updateUIState() {
        if (_tabbedPane != null) {
            final Component selectedComponent = _tabbedPane.getSelectedComponent();
            if (selectedComponent instanceof PagePanel) {
                final PagePanel pagePanel = (PagePanel) selectedComponent;
                setTitle(pagePanel.getTitle());
            } else {
                setTitle("");
            }
        }
    }

}