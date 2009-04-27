package org.esa.beam.visat.toolviews.stat;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.esa.beam.framework.datamodel.ROIDefinition;
import org.esa.beam.framework.datamodel.Stx;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.ui.application.ToolView;
import org.esa.beam.util.StringUtils;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.RenderedImage;
import java.util.ArrayList;

/**
 * A general pane within the statistics window.
 *
 * @author Marco Peters
 */
class StatisticsPanel extends TextPagePanel {

    private static final String DEFAULT_STATISTICS_TEXT = "No statistics computed yet.";  /*I18N*/
    private static final String TITLE_PREFIX = "Statistics";

    private ComputePanel computePanel;
    private ActionListener allPixelsActionListener;
    private ActionListener roiActionListener;
    private JCheckBox useAllBandsCheckBox;

    public StatisticsPanel(final ToolView parentDialog, String helpID) {
        super(parentDialog, DEFAULT_STATISTICS_TEXT, helpID);
    }

    @Override
    protected String getTitlePrefix() {
        return TITLE_PREFIX;
    }

    @Override
    protected void initContent() {
        super.initContent();
        computePanel = ComputePanel.createComputePane(getAllPixelActionListener(), getRoiActionListener(), getRaster());
        useAllBandsCheckBox = new JCheckBox("Use All Bands");     /*I18N*/
        useAllBandsCheckBox.setEnabled(getRaster() != null);
        computePanel.add(useAllBandsCheckBox);

        final JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(computePanel, BorderLayout.NORTH);
        final JPanel helpPanel = new JPanel(new BorderLayout());
        helpPanel.add(getHelpButton(), BorderLayout.EAST);
        rightPanel.add(helpPanel, BorderLayout.SOUTH);

        add(rightPanel, BorderLayout.EAST);
    }

    private ActionListener getAllPixelActionListener() {
        if (allPixelsActionListener == null) {
            allPixelsActionListener = new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    computeStatistics(false);
                }
            };
        }
        return allPixelsActionListener;
    }

    private ActionListener getRoiActionListener() {
        if (roiActionListener == null) {
            roiActionListener = new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    computeStatistics(true);
                }
            };
        }
        return roiActionListener;
    }

    @Override
    protected void updateContent() {
        super.updateContent();
        if (computePanel != null) {
            final RasterDataNode raster = getRaster();
            computePanel.setRaster(raster);
            if (raster != null && raster.isStxSet() && raster.getStx().getResolutionLevel() == 0) {
                final Stx stx = raster.getStx();
                getTextArea().setText(createText(stx, false, getRaster()));
            } else {
                getTextArea().setText(DEFAULT_STATISTICS_TEXT);
            }
        }
    }

    @Override
    protected String createText() {
        // not used
        return DEFAULT_STATISTICS_TEXT;
    }

    private void computeStatistics(final boolean useROI) {
        final RenderedImage roiImage;
        if (useROI) {
            roiImage = getRoiImage(getRaster());
        } else {
            roiImage = null;
        }
        final boolean useAllBands = useAllBandsCheckBox.isSelected();

        final String title = "Computing Statistics";
        SwingWorker<ArrayList<stxData>, Object> swingWorker = new ProgressMonitorSwingWorker<ArrayList<stxData>, Object>(this, title) {
            @Override
            protected ArrayList<stxData> doInBackground(ProgressMonitor pm) throws Exception {
                final ArrayList<stxData> stxList = new ArrayList<stxData>();

                if(useAllBands) {
                    final int numBands = getProduct().getNumBands();
                    pm.beginTask(title, numBands*100);
                    for(Band raster : getProduct().getBands()) {
                        final Stx stx;
                        if(useROI) {
                            stx = Stx.create(raster, getRoiImage(raster), pm);
                        } else {
                            stx = Stx.create(raster, 0, new SubProgressMonitor(pm, 100));
                            raster.setStx(stx);
                        }
                        stxList.add(new stxData(stx, raster));
                    }
                    pm.done();
                } else {
                    final Stx stx;
                    if (roiImage == null) {
                        stx = Stx.create(getRaster(), 0, pm);
                        getRaster().setStx(stx);
                    } else {
                        stx = Stx.create(getRaster(), roiImage, pm);
                    }
                    stxList.add(new stxData(stx, getRaster()));
                }
                return stxList;
            }


            @Override
            public void done() {

                try {
                    final StringBuffer sb = new StringBuffer(1024);
                    final ArrayList<stxData> stxList = get();
                    String msgPrefix = null;
                    for(stxData stxData : stxList)
                    {
                        final Stx stx = stxData.stx;
                        final RasterDataNode raster = stxData.raster;
                        if (stx.getSampleCount() > 0) {
                            sb.append(createText(stx, useROI, raster));
                        } else {
                            if (useROI) {
                                msgPrefix = "The ROI is empty.";        /*I18N*/
                            } else {
                                msgPrefix = "The scene contains no valid pixels.";  /*I18N*/
                            }
                        }
                    }

                    getTextArea().setText(sb.toString());
                    getTextArea().setCaretPosition(0);

                    if(msgPrefix != null) {
                        JOptionPane.showMessageDialog(getParentDialogContentPane(),
                                                          msgPrefix + "\nStatistics have not been computed.", /*I18N*/
                                                          "Statistics", /*I18N*/
                                                          JOptionPane.WARNING_MESSAGE);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(getParentDialogContentPane(),
                                                  "Failed to compute statistics.\nAn error occured:" + e.getMessage(),
                                                  /*I18N*/
                                                  "Statistics", /*I18N*/
                                                  JOptionPane.ERROR_MESSAGE);
                    getTextArea().setText(DEFAULT_STATISTICS_TEXT);
                }
            }
        };
        swingWorker.execute();
    }

    private class stxData {
        final Stx stx;
        final RasterDataNode raster;

        stxData(final Stx stx, final RasterDataNode raster) {
            this.stx = stx;
            this.raster = raster;
        }
    }

    private String createText(final Stx stat, final boolean hasROI, final RasterDataNode raster) {

        final String unit = (StringUtils.isNotNullAndNotEmpty(raster.getUnit()) ? raster.getUnit() : "1");
        final long numPixelTotal = (long) raster.getSceneRasterWidth() * (long) raster.getSceneRasterHeight();
        final StringBuffer sb = new StringBuffer(1024);

        sb.append("\n");

        sb.append("Band:  \t\t\t");
        sb.append(raster.getName());
        sb.append("\n");

        sb.append("Only ROI pixels considered:  \t");
        sb.append(hasROI ? "Yes" : "No");
        sb.append("\n");

        sb.append("Number of pixels total:      \t\t");
        sb.append(numPixelTotal);
        sb.append("\n");

        sb.append("Number of considered pixels: \t");
        sb.append(stat.getSampleCount());
        sb.append("\n");

        sb.append("Ratio of considered pixels:  \t");
        sb.append(100.0 * stat.getSampleCount() / numPixelTotal);
        sb.append("\t ");
        sb.append("%");
        sb.append("\n");

        sb.append("\n");

        sb.append("Minimum:  \t\t\t");
        sb.append(getMin(stat, raster));
        sb.append("\t ");
        sb.append(unit);
        sb.append("\n");

        sb.append("Maximum:  \t\t\t");
        sb.append(getMax(stat, raster));
        sb.append("\t ");
        sb.append(unit);
        sb.append("\n");

        sb.append("\n");

        sb.append("Mean:     \t\t\t");
        sb.append(getMean(stat, raster));
        sb.append("\t ");
        sb.append(unit);
        sb.append("\n");

        sb.append("Std-Dev:  \t\t\t");
        sb.append(getStandardDeviation(stat, raster));
        sb.append("\t ");
        sb.append(unit);
        sb.append("\n");

        sb.append("Coefficient of Variation:\t\t");
        sb.append(stat.getCoefficientOfVariation());
        sb.append("\t ");
        sb.append(unit);
        sb.append("\n");

        if (hasROI) {

            sb.append("Equivilant Number of Looks:\t");
            sb.append(stat.getEquivilantNumberOfLooks());
            sb.append("\t ");
            sb.append("looks");
            sb.append("\n");

            final ROIDefinition roiDefinition = raster.getROIDefinition();

            sb.append("\n");

            sb.append("ROI area shapes used: \t\t");
            sb.append(roiDefinition.isShapeEnabled() ? "Yes" : "No");
            sb.append("\n");

            sb.append("ROI value range used: \t\t");
            sb.append(roiDefinition.isValueRangeEnabled() ? "Yes" : "No");
            sb.append("\n");

            if (roiDefinition.isValueRangeEnabled()) {
                sb.append("ROI minimum value:   \t\t");
                sb.append(roiDefinition.getValueRangeMin());
                sb.append("\t ");
                sb.append(unit);
                sb.append("\n");

                sb.append("ROI maximum value:   \t\t");
                sb.append(roiDefinition.getValueRangeMax());
                sb.append("\t ");
                sb.append(unit);
                sb.append("\n");
            }

            sb.append("ROI bitmask used: \t\t");
            sb.append(roiDefinition.isBitmaskEnabled() ? "Yes" : "No");
            sb.append("\n");

            if (roiDefinition.isBitmaskEnabled()) {
                sb.append("ROI bitmask expression: \t\t");
                sb.append(roiDefinition.getBitmaskExpr());
                sb.append("\n");
            }

            sb.append("ROI combination operator: \t");
            sb.append(roiDefinition.isOrCombined() ? "OR" : "AND");
            sb.append("\n");

            sb.append("ROI inverted: \t\t");
            sb.append(roiDefinition.isInverted() ? "Yes" : "No");
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    protected String getDataAsText() {
        // remove extra tabs to use in excel
        return getTextArea().getText().replaceAll("\t\t\t", "\t").replaceAll("\t\t", "\t");
    }

    private double getMin(final Stx stat, final RasterDataNode raster) {
        return raster.scale(stat.getMin());
    }

    private double getMax(final Stx stat, final RasterDataNode raster) {
        return raster.scale(stat.getMax());
    }

    private double getMean(final Stx stat, final RasterDataNode raster) {
        return raster.scale(stat.getMean());
    }

    /*
     * Use error-propagation to compute stddev for log10-scaled bands. (Ask Ralf for details)
     */
    private double getStandardDeviation(final Stx stat, final RasterDataNode raster) {
        if (raster.isLog10Scaled()) {
            return raster.getScalingFactor() * Math.log(10.0) * getMean(stat, raster) * stat.getStandardDeviation();
        } else {
            return raster.scale(stat.getStandardDeviation());
        }
    }

    @Override
    public void handleLayerContentChanged() {
        computePanel.updateRoiCheckBoxState();
    }
}
