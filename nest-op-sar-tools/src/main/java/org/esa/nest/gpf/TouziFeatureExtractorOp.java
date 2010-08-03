/*
 * Copyright (C) 2002-2007 by ?
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.nest.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;

import java.awt.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import Jampack.*;

/**
 * Extracts Touzi features of a given product
 */

@OperatorMetadata(alias="Touzi-Feature-Extractor", category = "SAR Tools", description="Extracts features of a given product")
public final class TouziFeatureExtractorOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", itemAlias = "band",
            sourceProductId="source", label="Source Bands")
    private String[] sourceBandNames;

    @Parameter(label="No Data Value", defaultValue = "0.0")
    private double NoDataValue = 0.0;

    private int sourceImageWidth;
    private int sourceImageHeight;

    private enum Direction { UP, DOWN, LEFT, RIGHT }

    /**
     * Initializes this operator and sets the one and only target product.
     * <p>The target product can be either defined by a field of type {@link org.esa.beam.framework.datamodel.Product} annotated with the
     * {@link org.esa.beam.framework.gpf.annotations.TargetProduct TargetProduct} annotation or
     * by calling {@link #setTargetProduct} method.</p>
     * <p>The framework calls this method after it has created this operator.
     * Any client code that must be performed before computation of tile data
     * should be placed here.</p>
     *
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during operator initialisation.
     * @see #getTargetProduct()
     */
    @Override
    public void initialize() throws OperatorException {

        try {
            getSourceImageDimension();

            createTargetProduct();

        } catch(Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Get source image dimension.
     */
    private void getSourceImageDimension() {
        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();
    }

    /**
     * Create target product.
     * @throws Exception The exception.
     */
    private void createTargetProduct() throws Exception {

        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceImageWidth,
                                    sourceImageHeight);

        addSelectedBands();

        OperatorUtils.copyProductNodes(sourceProduct, targetProduct);
    }

    /**
     * Add bands to the target product.
     * @throws OperatorException The exception.
     */
    private void addSelectedBands() throws OperatorException {

        if (sourceBandNames == null || sourceBandNames.length == 0) {
            final Band[] bands = sourceProduct.getBands();
            final ArrayList<String> bandNameList = new ArrayList<String>(sourceProduct.getNumBands());
            for (Band band : bands) {
                bandNameList.add(band.getName());
            }
            sourceBandNames = bandNameList.toArray(new String[bandNameList.size()]);
        }

        /**
         * Name the target bands
         */
        final ArrayList<String> outParamList = new ArrayList<String>();
        outParamList.add("Lambda");
        outParamList.add("m");
        outParamList.add("Psi");
        outParamList.add("Tau");
        outParamList.add("Alpha");
        outParamList.add("Phi");

        final ArrayList<String> targetBandNameList = new ArrayList<String>();
        for(int i=0; i<outParamList.size(); i++)
        {
            for(int j=0; j<3; j++)
            {
                targetBandNameList.add(outParamList.get(i) + j);
            }
        }

        for (String targetBandName : targetBandNameList) {

            final Band targetBand = new Band(targetBandName,
                                             ProductData.TYPE_FLOAT32,
                                             sourceImageWidth,
                                             sourceImageHeight);

            targetProduct.addBand(targetBand);
        }
    }

    /**
     * Called by the framework in order to compute a tile for the given target band.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetBand The target band.
     * @param targetTile The current tile associated with the target band to be computed.
     * @param pm         A progress monitor which should be used to determine computation cancelation requests.
     * @throws org.esa.beam.framework.gpf.OperatorException
     *          If an error occurs during computation of the target raster.
     */
    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {
        try {
            /**
             * Getting the source tiles which contain Shh, Shv, Svh, Svv (complex values)
             */
            final Tile[] sourceTiles = new Tile[sourceBandNames.length];

            Unit.UnitType bandUnit;
            final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);
            ProductData trgData;
            Tile outputTile;
            final Set<Band> keys = targetTiles.keySet();

            for(int x=0; x<targetRectangle.width; x++) {
                for(int y=0; y<targetRectangle.height; y++) {
                    /**
                     * Stores the HH, HV, VH, VV amplitudes in dataValues[0] and the phases in dataValues[1]
                     */
                    double[][] dataValues= new double[2][4];

                    for (int i = 0; i < sourceBandNames.length; i++) {
                        sourceTiles[i] = getSourceTile(sourceProduct.getBand(sourceBandNames[i]), targetRectangle, pm);
                        double data = sourceTiles[i].getSampleDouble(x, y);

                        String pol = OperatorUtils.getBandPolarization(sourceBandNames[i], absRoot);
                        bandUnit = Unit.getUnitType(sourceProduct.getBand(sourceBandNames[i]));

                        if(pol.contains("hh")){

                            if(bandUnit == Unit.UnitType.INTENSITY){
                                dataValues[0][0] = Math.sqrt(data);
                            }
                            if(bandUnit == Unit.UnitType.PHASE){
                                dataValues[1][0] = data;
                            }
                        }

                        if(pol.contains("hv")){
                            if(bandUnit == Unit.UnitType.INTENSITY){
                                dataValues[0][1] = Math.sqrt(data);
                            }
                            if(bandUnit == Unit.UnitType.PHASE){
                                dataValues[1][1] = data;
                            }
                        }

                        if(pol.contains("vh")){
                            if(bandUnit == Unit.UnitType.INTENSITY){
                                dataValues[0][2] = Math.sqrt(data);
                            }
                            if(bandUnit == Unit.UnitType.PHASE){
                                dataValues[1][2] = data;
                            }
                        }

                        if(pol.contains("vv")){
                            if(bandUnit == Unit.UnitType.INTENSITY){
                                dataValues[0][3] = Math.sqrt(data);
                            }
                            if(bandUnit == Unit.UnitType.PHASE){
                                dataValues[1][3] = data;
                            }
                        }
                    }

                    /**
                     * Process the data
                     */
                    double[] output = new double[18];
                    output = getTouziFeatureVector(dataValues);

                    /**
                     * Write the data
                     */
                    int k=0;
                    for(int i=0; i<3; i++){
                        for (Band targetBand : keys){
                            outputTile = targetTiles.get(targetBand);
                            trgData = outputTile.getDataBuffer();
                            trgData.setElemDoubleAt(outputTile.getDataBufferIndex(x, y), output[k]);
                            k++;
                        }
                    }
                    /**
                     * end
                     */
                }
            }

        } catch(Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        } finally {
            pm.done();
        }
    }

    double[] getTouziFeatureVector(double[][] scatterMatrixBuff) throws JampackException {
        /**
         * We need to convert the double[][] data to a complex matrix object
         */

        // Auxiliary variables
        double rePart, imPart;
        Z complexNumber = new Z();
        Z complexNumber2 = new Z();
        Z complexNumber3 = new Z();
        Z complexNumber4 = new Z();
        double doubleNumber;

        // Main variables
        Zmat S = new Zmat(2, 2);            // Starting point: scatter matrix
        double[] features = new double[18];  // Output

        // Intermediate variables
        Zmat k = new Zmat(3, 1);    // Scattering vector
        Zmat kH;                    // Scattering vector transpose conjugate
        Zmat T = new Zmat(3, 3);    // Coherence matrix
        Zmat SH;                    // Scatter matrix transpose conjugate
        Zmat G = new Zmat(2, 2);    // Graves matrix

        // Operators
        H operatorH = new H();
        Times operatorTimes = new Times();

        for(int i=0; i<2; i++){
            for(int j=0; j<2; j++){
                rePart =  scatterMatrixBuff[0][2*i+j] * Math.cos(scatterMatrixBuff[1][2*i+j]);
                imPart =  scatterMatrixBuff[0][2*i+j] * Math.sin(scatterMatrixBuff[1][2*i+j]);

                complexNumber.Eq(rePart, imPart);
                S.put(i, j, complexNumber);
            }
        }

        /**
         * The values on the anti-diagonal should be averaged
         */
        complexNumber.Plus(S.get(0, 1), S.get(1, 0));
        complexNumber.Div(complexNumber, (double) 2);
        S.put(0, 1, complexNumber);
        S.put(1, 0, complexNumber);

        /**
         * Compute k = [Shh + Svv, Shh-Svv, 2Shv]^T
         */
        complexNumber.Plus(S.get(0, 0), S.get(1, 1));
        k.put(0,0,complexNumber);
        complexNumber.Minus(S.get(0, 0), S.get(1, 1));
        k.put(1,0,complexNumber);
        complexNumber.Times((double)2, S.get(0, 1));
        k.put(2,0,complexNumber);

        /**
         * Compute the coherence matrix T = k = k^*T
         */
        kH = operatorH.o(k);
        T = operatorTimes.o(k, kH);

        /**
         * Diagonalize T
         */

        Eig eigenT = new Eig(T);

        for (int i=0; i<2; i++){
            int n=0; // Parameter number

            // Step1: the eigenvalues of T
            complexNumber = eigenT.D.get(i);
            features[6*i+n] = complexNumber.re;
            n++;

            // Step2: the Graves matrix
            k = eigenT.X.get(0,2,i,i);
            S = subS(k);    // Get the scatter sub mechanism matrix
            SH = operatorH.o(S);
            G = operatorTimes.o(SH, S);

            // Step3: diagonalize the Graves matrix
            Eig eigenG = new Eig(G);
            Zmat eigenVect1, eigenVect2;

            // Find the maximum of polarization
            if(complexNumber.abs(eigenG.D.get(0)) > complexNumber.abs(eigenG.D.get(1))){
                doubleNumber = complexNumber.abs(eigenG.D.get(0));
                complexNumber = eigenG.X.get(0,0);
            }
            else{
                doubleNumber = complexNumber.abs(eigenG.D.get(1));
                complexNumber = eigenG.X.get(0,1);
            }

            features[6*i+n] = doubleNumber;
            n++;

            // Find Psi and Tau
            double a = Math.acos(complexNumber.re + complexNumber.im);
            double b = Math.acos(complexNumber.re - complexNumber.im);

            // Psi
            doubleNumber = 0.5 * (a + b);
            features[6*i+n] = doubleNumber;
            n++;

            // Tau
            doubleNumber = 0.5 * (a - b);
            features[6*i+n] = doubleNumber;
            n++;

            // Find Alpha and Phi
            eigenVect1 = eigenG.X.get(0,1,0,0);
            eigenVect2 = eigenG.X.get(0,1,1,1);

            // Computes Alpha_S
            complexNumber = findAlpha_S(eigenG.X.get(0,1,0,0), eigenG.X.get(0,1,1,1), S);

            // Derive Alpha from Alpha_S
            doubleNumber = Math.atan(complexNumber.abs(complexNumber));
            features[6*i+n] = doubleNumber;
            n++;

            // Derive Phi from Alpha_S
            doubleNumber = zAngle(complexNumber);
            features[6*i+n] = doubleNumber;
        }

        return features;
    }

    Zmat subS(Zmat k) throws JampackException {
        Z complexNumber = new Z();
        double doubleNumber;
        Zmat S = new Zmat(2, 2);

        doubleNumber = Math.sqrt(2);
        doubleNumber = 2*doubleNumber;

        complexNumber.Plus(k.get(0,0), k.get(1,0));
        complexNumber.Div(complexNumber, doubleNumber);
        S.put(0, 0, complexNumber);

        complexNumber.Minus(k.get(0,0), k.get(1,0));
        complexNumber.Div(complexNumber, doubleNumber);
        S.put(1, 1, complexNumber);

        complexNumber = k.get(0,1);
        complexNumber.Div(complexNumber, doubleNumber);
        S.put(0, 1, complexNumber);
        S.put(1, 0, complexNumber);

        return S;
    }

    Z findAlpha_S(Zmat e1, Zmat e2, Zmat S) throws JampackException {

        Z complexNumber1 = new Z();
        Z complexNumber2 = new Z();
        Z complexNumber3 = new Z();
        Z complexNumber4 = new Z();

        complexNumber1.Times(S.get(0,0), e1.get(0,0));
        complexNumber2.Times(S.get(1,0), e1.get(1,0));
        complexNumber3.Plus(complexNumber1, complexNumber2);
        complexNumber2 = e1.get(0,0);
        complexNumber1.Div(complexNumber3, complexNumber2.Conj(complexNumber2));

        complexNumber2.Times(S.get(0,0), e2.get(0,0));
        complexNumber3.Times(S.get(1,0), e2.get(1,0));
        complexNumber3.Plus(complexNumber2, complexNumber3);
        complexNumber2 = e2.get(0,0);
        complexNumber2.Div(complexNumber3, complexNumber2.Conj(complexNumber2));

        complexNumber3.Minus(complexNumber1, complexNumber2);      // u1 - u2
        complexNumber4.Plus(complexNumber1, complexNumber2);       // u1 + u2

        complexNumber1.Div(complexNumber3, complexNumber4);        // (u1 - u2) / (u1 + u2)

        return complexNumber1;
    }

    double zAngle(Z c){
        double angle;

        if(c.re == 0 && c.im >= 0) { angle = Math.PI / 2; }
        else if(c.re == 0 && c.im < 0) { angle = -Math.PI / 2; }
        else { angle = Math.atan(c.im / c.re); }

        return angle;
    }
     /**
     * Get source tile rectangle.
     * @param tx0 X coordinate for the upper left corner pixel in the target tile.
     * @param ty0 Y coordinate for the upper left corner pixel in the target tile.
     * @param tw The target tile width.
     * @param th The target tile height.
     * @return The source tile rectangle.
     */
    private Rectangle getSourceRectangle(final int tx0, final int ty0, final int tw, final int th) {
        // extend target rectangle by 20% to all directions
        final int x0 = Math.max(0, tx0 - tw/5);
        final int y0 = Math.max(0, ty0 - th/5);
        final int xMax = Math.min(tx0 + tw - 1 + tw/5, sourceImageWidth);
        final int yMax = Math.min(ty0 + th - 1 + th/5, sourceImageHeight);
        final int w = xMax - x0 + 1;
        final int h = yMax - y0 + 1;
        return new Rectangle(x0, y0, w, h);
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator()
     * @see org.esa.beam.framework.gpf.OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(TouziFeatureExtractorOp.class);
        }
    }
}