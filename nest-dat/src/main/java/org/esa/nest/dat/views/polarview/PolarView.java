package org.esa.nest.dat.views.polarview;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.ui.product.ProductSceneImage;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * NEST
 * User: lveci
 * Date: Dec 1, 2008
 */
public class PolarView extends JPanel {

    private final ProductSceneImage productSceneImage;
    private final Product product;
    private final int numRecords;
    private final int recordLength;
    private final int numDirBins;
    private final int numWLBins;

    private final float firstDirBins;
    private final float dirBinStep;
    private final float firstWLBin;
    private final float lastWLBin;

    private final float[] dataset;
    private int currentRecord = 0;

    private static final int REAL = 0;
    private static final int IMAG = 1;
    private static final int BOTH = 2;
    private static final int POWER = 3;
    private static final int INTENSITY = 4;
    private static final int MULTIPLIED = 5;

    private PolarCanvas graphView;
    private int graphType = 0;
    private float spectrum[][];

    private static final double F = 10000D;
    public static final Color waveColorTable[] = (new Color[]{
            new Color(0, 30, 255), new Color(125, 210, 255),
            new Color(85, 210, 90), new Color(255, 210, 40), new Color(255, 30, 0)
    });
    private static final double rings[] = {50.0, 100.0, 200.0};
    private static final String ringTextStrings[] = { "200 (m)", "100 (m)", "50 (m)" };

    public PolarView(Product prod, ProductSceneImage sceneImage) {
        productSceneImage = sceneImage;
        product = prod;

        final MetadataElement sph = product.getMetadataRoot().getElement("SPH");
        numDirBins = sph.getAttributeInt("NUM_DIR_BINS", 0);
        numWLBins = sph.getAttributeInt("NUM_WL_BINS", 0);
        firstDirBins = (float) sph.getAttributeDouble("FIRST_DIR_BIN", 0);
        dirBinStep = (float) sph.getAttributeDouble("DIR_BIN_STEP", 0);
        firstWLBin = (float) sph.getAttributeDouble("FIRST_WL_BIN", 0);
        lastWLBin = (float) sph.getAttributeDouble("LAST_WL_BIN", 0);

        final RasterDataNode[] rasters = productSceneImage.getRasters();
        final RasterDataNode rasterNode = rasters[0];
        numRecords = rasterNode.getRasterHeight();
        recordLength = rasterNode.getRasterWidth();
        dataset = new float[recordLength];

        graphView = new PolarCanvas();
        graphView.setCompassNames("Range", "Azimuth");

        createPlot(rasterNode, currentRecord);
    }

    private void createPlot(RasterDataNode rasterNode, int rec) {
        try {
            rasterNode.loadRasterData();
            rasterNode.getPixels(0, rec, recordLength, 1, dataset);

        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        spectrum = getSpectrum(graphType != 1);
        float minValue = 0;//Float.MAX_VALUE;
        float maxValue = 255;//Float.MIN_VALUE;

        // complex data
        if (graphType == 3 || graphType == 4 || graphType == 2 || graphType == 5) {
            final float imagSpectrum[][] = getSpectrum(false);
            minValue = Float.MAX_VALUE;
            maxValue = Float.MIN_VALUE;
            final int halfCircle = spectrum.length / 2;
            for (int i = 0; i < spectrum.length; i++) {
                for (int j = 0; j < spectrum[0].length; j++) {
                    final float rS = spectrum[i][j];
                    final float iS = imagSpectrum[i][j];
                    float v = rS;
                    if (graphType == 2) {
                        if (i >= halfCircle)
                            v = iS;
                    } else if (graphType == 5) {
                        if (sign(rS) == sign(iS))
                            v *= iS;
                        else
                            v = 0.0F;
                    } else {
                        if (sign(rS) == sign(iS))
                            v = rS * rS + iS * iS;
                        else
                            v = 0.0F;
                        if (graphType == 4)
                            v = (float) Math.sqrt(v);
                    }
                    spectrum[i][j] = v;
                    minValue = Math.min(minValue, v);
                    maxValue = Math.max(maxValue, v);
                }
            }
        }

        final float thFirst = firstDirBins - 5F;
        final float rStep = (float) (Math.log(lastWLBin) - Math.log(firstWLBin)) / (float) (numWLBins - 1);
        final double cRange[] = { (double) minValue, (double) maxValue };
        final double rRange[] = {0.0, 333.33333333333};
        double logr = Math.log(firstWLBin);

        logr -= rStep / 2.0;
        final int nWl = spectrum[0].length;
        final float radii[] = new float[nWl + 1];
        for (int j = 0; j <= nWl; j++) {
            radii[j] = (float) (10000.0 / Math.exp(logr));
            logr += rStep;
        }

        final PolarData data = new PolarData(spectrum, 90F + thFirst, dirBinStep, radii);

        graphView.getCAxis().setDataRange(cRange);
        graphView.getRAxis().setAutoRange(false);
        graphView.getRAxis().setDataRange(rRange);
        graphView.getRAxis().setRange(rRange[0], rRange[1], 2);
        //graphView.setRings(rings, ringText);
        data.setColorScale(ColourScale.newCustomScale(cRange));
        graphView.setData(data);
    }

    private float[][] getSpectrum(boolean getReal) {

        final int Nd2 = numDirBins / 2;
        final float minValue = 0;
        final float maxValue = 255;
        final float scale = (maxValue - minValue) / 255F;
        final float spectrum[][] = new float[numDirBins][numWLBins];
        int index = 0;
        for (int i = 0; i < Nd2; i++) {
            for (int j = 0; j < numWLBins; j++)
                spectrum[i][j] = dataset[index++] * scale + minValue;
        }

        if (getReal) {
            for (int i = 0; i < Nd2; i++) {
                System.arraycopy(spectrum[i], 0, spectrum[i + Nd2], 0, numWLBins);
            }
        } else {
            for (int i = 0; i < Nd2; i++) {
                for (int j = 0; j < numWLBins; j++)  {
                    spectrum[i + Nd2][j] = -spectrum[i][j];
                }
            }
        }
        return spectrum;
    }

    private static int sign(float f) {
        return f < 0.0F ? -1 : 1;
    }

    private static float[][] resampleSpectrum(float spectrum[][], int resampling) {
        int nTh = spectrum.length;
        int nWl = spectrum[0].length;
        float resampledSpectrum[][] = new float[nTh * resampling][nWl];
        int i = 0;
        for (int k = 0; i < nTh - 1; k += resampling) {
            for (int j = 0; j < nWl; j++) {
                float s = spectrum[i][j];
                float ds = spectrum[i + 1][j] - s;
                for (int l = 0; l < resampling; l++)
                    resampledSpectrum[k + l][j] = s + (ds * (float) l) / (float) resampling;

            }

            i++;
        }

        System.arraycopy(spectrum[nTh - 1], 0, resampledSpectrum[nTh * 2 - 1], 0, nWl);

        return resampledSpectrum;
    }

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
    }
}
