package org.esa.nest.dat.views.polarview;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.ui.product.ProductSceneImage;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.framework.ui.PixelInfoFactory;
import org.esa.beam.framework.ui.BasicView;
import org.esa.beam.framework.ui.command.CommandUIFactory;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;

/**
 * NEST
 * User: lveci
 * Date: Dec 1, 2008
 */
public class PolarView extends BasicView implements ActionListener, PopupMenuListener, MouseListener, MouseMotionListener {

    private final ProductSceneImage productSceneImage;
    private final Product product;
    private final int numRecords;
    private final int recordLength;
    private int numDirBins;
    private int numWLBins;

    private float firstDirBins;
    private float dirBinStep;
    private float firstWLBin;
    private float lastWLBin;

    private final float[] dataset;
    private int currentRecord = 0;

    private enum Unit { REAL, IMAGINARY, BOTH, AMPLITUDE, INTENSITY, MULTIPLIED };

    private PolarCanvas graphView;
    private ReadoutCanvas readoutView;
    private Unit graphType = Unit.REAL;
    private float spectrum[][];

    public static final Color colourTable[] = (new Color[]{
            new Color(0, 0, 0), new Color(0, 0, 255), new Color(0, 255, 255),
            new Color(0, 255, 0), new Color(255, 255, 0), new Color(255, 0, 0)
    });
    private static final double rings[] = {50.0, 100.0, 200.0};
    private static final String ringTextStrings[] = {"200 (m)", "100 (m)", "50 (m)"};

    public PolarView(Product prod, ProductSceneImage sceneImage) {
        //super(sceneImage);
        productSceneImage = sceneImage;
        product = prod;

        getMetadata();

        final RasterDataNode[] rasters = productSceneImage.getRasters();
        final RasterDataNode rasterNode = rasters[0];
        numRecords = rasterNode.getRasterHeight();
        recordLength = rasterNode.getRasterWidth();
        dataset = new float[recordLength];

        graphView = new PolarCanvas();
        graphView.setCompassNames("Range", "Azimuth");

        readoutView = new ReadoutCanvas();

        CreateContextMenu();
        addMouseListener(this);
        addMouseMotionListener(this);

        createPlot(rasterNode, currentRecord);
    }

    private void getMetadata() {
        final MetadataElement sph = product.getMetadataRoot().getElement("SPH");
        numDirBins = sph.getAttributeInt("NUM_DIR_BINS", 0);
        numWLBins = sph.getAttributeInt("NUM_WL_BINS", 0);
        firstDirBins = (float) sph.getAttributeDouble("FIRST_DIR_BIN", 0);
        dirBinStep = (float) sph.getAttributeDouble("DIR_BIN_STEP", 0);
        firstWLBin = (float) sph.getAttributeDouble("FIRST_WL_BIN", 0);
        lastWLBin = (float) sph.getAttributeDouble("LAST_WL_BIN", 0);
    }

    private void createPlot(RasterDataNode rasterNode, int rec) {
        try {
            rasterNode.loadRasterData();
            rasterNode.getPixels(0, rec, recordLength, 1, dataset);

        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        spectrum = getSpectrum(graphType != Unit.IMAGINARY);
        float minValue = 0;//Float.MAX_VALUE;
        float maxValue = 255;//Float.MIN_VALUE;

        // complex data
        if (graphType == Unit.AMPLITUDE || graphType == Unit.INTENSITY || graphType == Unit.BOTH || graphType == Unit.MULTIPLIED) {
            final float imagSpectrum[][] = getSpectrum(false);
            minValue = Float.MAX_VALUE;
            maxValue = Float.MIN_VALUE;
            final int halfCircle = spectrum.length / 2;
            for (int i = 0; i < spectrum.length; i++) {
                for (int j = 0; j < spectrum[0].length; j++) {
                    final float rS = spectrum[i][j];
                    final float iS = imagSpectrum[i][j];
                    float v = rS;
                    if (graphType == Unit.BOTH) {
                        if (i >= halfCircle)
                            v = iS;
                    } else if (graphType == Unit.MULTIPLIED) {
                        if (sign(rS) == sign(iS))
                            v *= iS;
                        else
                            v = 0.0F;
                    } else {
                        if (sign(rS) == sign(iS))
                            v = rS * rS + iS * iS;
                        else
                            v = 0.0F;
                        if (graphType == Unit.INTENSITY)
                            v = (float) Math.sqrt(v);
                    }
                    spectrum[i][j] = v;
                    minValue = Math.min(minValue, v);
                    maxValue = Math.max(maxValue, v);
                }
            }
        }

        final float thFirst = firstDirBins - 5f;
        final float rStep = (float) (Math.log(lastWLBin) - Math.log(firstWLBin)) / (float) (numWLBins - 1);
        final double colourRange[] = {(double) minValue, (double) maxValue};
        final double radialRange[] = {0.0, 333.33333333333};
        double logr = Math.log(firstWLBin) - (rStep / 2.0);

        final int nWl = spectrum[0].length;
        final float radii[] = new float[nWl + 1];
        for (int j = 0; j <= nWl; j++) {
            radii[j] = (float) (10000.0 / Math.exp(logr));
            logr += rStep;
        }

        final PolarData data = new PolarData(spectrum, 90f + thFirst, dirBinStep, radii);

        graphView.getColourAxis().setDataRange(colourRange);
        graphView.getRadialAxis().setAutoRange(false);
        graphView.getRadialAxis().setDataRange(radialRange);
        graphView.getRadialAxis().setRange(radialRange[0], radialRange[1], 4);
        graphView.setRings(rings, null);
        data.setColorScale(ColourScale.newCustomScale(colourRange));
        graphView.setData(data);

        repaint();
    }

    private float[][] getSpectrum(boolean getReal) {

        final int Nd2 = numDirBins / 2;
        final float minValue = 0;
        final float maxValue = 255;
        final float scale = (maxValue - minValue) / 255F;
        final float spectrum[][] = new float[numDirBins][numWLBins];
        int index = 0;
        for (int i = 0; i < Nd2; i++) {
            for (int j = 0; j < numWLBins; j++) {
                spectrum[i][j] = dataset[index++] * scale + minValue;
            }
        }

        if (getReal) {
            for (int i = 0; i < Nd2; i++) {
                System.arraycopy(spectrum[i], 0, spectrum[i + Nd2], 0, numWLBins);
            }
        } else {
            for (int i = 0; i < Nd2; i++) {
                for (int j = 0; j < numWLBins; j++) {
                    spectrum[i + Nd2][j] = -spectrum[i][j];
                }
            }
        }
        return spectrum;
    }

    private static int sign(float f) {
        return f < 0.0F ? -1 : 1;
    }

  /*  private static float[][] resampleSpectrum(float spectrum[][], int resampling) {
        final int nTh = spectrum.length;
        final int nWl = spectrum[0].length;
        final float resampledSpectrum[][] = new float[nTh * resampling][nWl];
        int i = 0;
        for (int k = 0; i < nTh - 1; k += resampling) {
            for (int j = 0; j < nWl; j++) {
                final float s = spectrum[i][j];
                final float ds = spectrum[i + 1][j] - s;
                for (int l = 0; l < resampling; l++) {
                    resampledSpectrum[k + l][j] = s + (ds * (float) l) / (float) resampling;
                }
            }
            i++;
        }

        System.arraycopy(spectrum[nTh - 1], 0, resampledSpectrum[nTh * 2 - 1], 0, nWl);

        return resampledSpectrum;
    }      */

    /**
     * Paints the panel component
     *
     * @param g The Graphics
     */
    @Override
    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);

        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        graphView.setSize(getWidth(), getHeight());
        graphView.paint(g);

        readoutView.paint(g);
    }

    private void CreateContextMenu() {
        final ImageIcon opIcon = null;

    }


    @Override
    public JPopupMenu createPopupMenu(Component component) {
        return null;
    }

    @Override
    public JPopupMenu createPopupMenu(MouseEvent event) {
        final JPopupMenu popup = new JPopupMenu();
        //getCommandUIFactory().addContextDependentMenuItems("image", popup);
        final JMenuItem itemNext = new JMenuItem("Next");
        popup.add(itemNext);
        itemNext.setHorizontalTextPosition(JMenuItem.RIGHT);
        itemNext.addActionListener(this);

        final JMenuItem itemPrev = new JMenuItem("Previous");
        popup.add(itemPrev);
        itemPrev.setHorizontalTextPosition(JMenuItem.RIGHT);
        itemPrev.addActionListener(this);

        popup.setLabel("Justification");
        popup.setBorder(new BevelBorder(BevelBorder.RAISED));
        popup.addPopupMenuListener(this);
        popup.show(this, event.getX(), event.getY());

        return popup;
    }

    /**
     * Handles menu item pressed events
     *
     * @param event the action event
     */
    public void actionPerformed(ActionEvent event) {
        final RasterDataNode[] rasters = productSceneImage.getRasters();
        final RasterDataNode rasterNode = rasters[0];
        createPlot(rasterNode, ++currentRecord);
    }

    private void checkPopup(MouseEvent e) {
        if (e.isPopupTrigger()) {
            createPopupMenu(e);
        }
    }

    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
    }

    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
    }

    public void popupMenuCanceled(PopupMenuEvent e) {
    }


    /**
     * Handle mouse pressed event
     *
     * @param e the mouse event
     */
    public void mousePressed(MouseEvent e) {
        checkPopup(e);

    }

    /**
     * Handle mouse clicked event
     *
     * @param e the mouse event
     */
    public void mouseClicked(MouseEvent e) {
        checkPopup(e);

        final Object src = e.getSource();
        if(src == graphView) {
            final Axis axis = graphView.selectAxis(e.getPoint());
            if(axis != null && axis == graphView.getColourAxis()) {
                //axisPanel = new AxisDialog(this);
                //axisPanel.show(axis, graphView, p);
            }
        }
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    /**
     * Handle mouse released event
     *
     * @param e the mouse event
     */
    public void mouseReleased(MouseEvent e) {
        checkPopup(e);


        repaint();
    }

    /**
     * Handle mouse dragged event
     *
     * @param e the mouse event
     */
    public void mouseDragged(MouseEvent e) {

    }

    /**
     * Handle mouse moved event
     *
     * @param e the mouse event
     */
    public void mouseMoved(MouseEvent e) {
        updateReadout(e);
    }

    private void updateReadout(MouseEvent evt) {
        if(spectrum == null)
            return;
        final double rTh[] = graphView.getRTheta(evt.getPoint());
        if(rTh != null) {
            final float thFirst = firstDirBins - 5f;
            final float rStep = (float) (Math.log(lastWLBin) - Math.log(firstWLBin)) / (float) (numWLBins - 1);
            final int thBin = (int)(((rTh[1] - (double)thFirst) % 360D) / (double)dirBinStep);
            int wvBin = (int)(((rStep / 2D + Math.log(10000D / rTh[0])) - Math.log(firstWLBin)) / rStep);
            wvBin = Math.min(wvBin, spectrum[0].length - 1);
            final int wl = (int)Math.round(Math.exp((double)wvBin * rStep + Math.log(firstWLBin)));
            final int element = (thBin % (spectrum.length / 2)) * spectrum[0].length + wvBin;

            final String[] readoutList = new String[4];
            readoutList[0] = "Bin: " + (thBin + 1) + "," + (wvBin + 1) + " Element: [" + element + "]";
            readoutList[1] = "Wavelength: " + wl + " (m)";
            readoutList[2] = "Direction: " + (int)((float)thBin * dirBinStep + dirBinStep / 2.0F + thFirst) + " degrees";
            readoutList[3] = "Value: " + spectrum[thBin][wvBin];

            readoutView.setReadout(readoutList);

            repaint();
        }
    }

}
