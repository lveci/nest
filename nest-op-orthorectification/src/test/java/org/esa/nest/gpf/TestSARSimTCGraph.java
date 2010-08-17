/*
 * Copyright (C) 2010 Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.gpf;

import junit.framework.TestCase;
import org.esa.nest.util.TestUtils;

/**
 * Unit test for SARSim TC Graph
 */
public class TestSARSimTCGraph extends TestCase {

    private final static String inputPathWSM =     TestUtils.rootPathExpectedProducts+"\\input\\subset_1_of_ENVISAT-ASA_WSM_1PNPDE20080119_093446_000000852065_00165_30780_2977.dim";
    private final static String test1 = "P:\\nest\\nest\\ESA Data\\RADAR\\ASAR\\Alternating Polarization Medium Resolution\\ASA_APM_1PNIPA20080510_203246_000000422068_00272_32390_0810.N1";
    private final static String test2 = "P:\\nest\\nest\\ESA Data\\RADAR\\Radarsat2\\Fine Quad Pol\\PK6626_DK340_FQ3_20080331_181047_HH_VV_HV_VH_SLC_(StOfGibraltar_Promo)\\product.xml";

    private String[] productTypeExemptions = { "_BP", "XCA", "WVW", "WVI", "WVS", "WSS", "DOR_VOR_AX" };
    private String[] exceptionExemptions = { "not supported", "already map projected" };

    @Override
    protected void setUp() throws Exception {

    }

    @Override
    protected void tearDown() throws Exception {

    }

    public void test() {
        assertTrue(true);
    }

  /*  public void test1() throws Exception {
        final File inputFile = new File(test1);
        if(!inputFile.exists()) return;

        final MockAppContext appContext = new MockAppContext();
        appContext.setSelectedProduct(ProductIO.readProduct(inputFile));

        final SARSimTerrainCorrectionDialog dialog = new SARSimTerrainCorrectionDialog(appContext,
                "SAR Sim Terrain Correction", "SARSimGeocodingOp");

        dialog.testRunGraph();
    }

    public void test2() throws Exception {
        final File inputFile = new File(test2);
        if(!inputFile.exists()) return;

        final Product subsetProduct = TestUtils.createSubsetProduct(ProductIO.readProduct(inputFile));

        final MockAppContext appContext = new MockAppContext();
        appContext.setSelectedProduct(subsetProduct);

        final SARSimTerrainCorrectionDialog dialog = new SARSimTerrainCorrectionDialog(appContext,
                "SAR Sim Terrain Correction", "SARSimGeocodingOp");

        dialog.testRunGraph();
    }

    public void testDialog() throws Exception {
       final MockAppContext appContext = new MockAppContext();
        appContext.setSelectedProduct(ProductIO.readProduct(inputPathWSM));

        final SARSimTerrainCorrectionDialog dialog = new SARSimTerrainCorrectionDialog(appContext,
                "SAR Sim Terrain Correction", "SARSimGeocodingOp");

        dialog.testRunGraph();
    }

    public void testProcessAllASAR() throws Exception
    {
        final MultiGraphRecursive processor = new MultiGraphRecursive();
        TestUtils.testProcessAllInPath(processor, TestUtils.rootPathASAR, productTypeExemptions, exceptionExemptions);
    }

    public void testProcessAllERS() throws Exception
    {
        final MultiGraphRecursive processor = new MultiGraphRecursive();
        TestUtils.testProcessAllInPath(processor, TestUtils.rootPathERS, productTypeExemptions, exceptionExemptions);
    }

    public void testProcessAllALOS() throws Exception
    {
        final MultiGraphRecursive processor = new MultiGraphRecursive();
        TestUtils.testProcessAllInPath(processor, TestUtils.rootPathALOS, productTypeExemptions, exceptionExemptions);
    }

    public void testProcessAllRadarsat2() throws Exception
    {
        final MultiGraphRecursive processor = new MultiGraphRecursive();
        TestUtils.testProcessAllInPath(processor, TestUtils.rootPathRadarsat2, productTypeExemptions, exceptionExemptions);
    }

    public void testProcessAllTerraSARX() throws Exception
    {
        final MultiGraphRecursive processor = new MultiGraphRecursive();
        TestUtils.testProcessAllInPath(processor, TestUtils.rootPathTerraSarX, productTypeExemptions, exceptionExemptions);
    }

    public void testProcessAllCosmo() throws Exception
    {
        final MultiGraphRecursive processor = new MultiGraphRecursive();
        TestUtils.testProcessAllInPath(processor, TestUtils.rootPathCosmoSkymed, productTypeExemptions, exceptionExemptions);
    }

    public void testProcessAllNestBox() throws Exception
    {
        final MultiGraphRecursive processor = new MultiGraphRecursive();
        TestUtils.testProcessAllInPath(processor, TestUtils.rootPathMixProducts, productTypeExemptions, exceptionExemptions);
    }

    private static class MultiGraphRecursive extends RecursiveProcessor {

        protected void process(final Product sourceProduct) throws Exception {
            System.out.println("Processing "+ sourceProduct.getFileLocation().getAbsolutePath());
            
            final Product subsetProduct = TestUtils.createSubsetProduct(sourceProduct);
            final MockAppContext appContext = new MockAppContext();
            appContext.setSelectedProduct(subsetProduct);

            final SARSimTerrainCorrectionDialog dialog = new SARSimTerrainCorrectionDialog(appContext,
                    "SAR Sim Terrain Correction", "SARSimGeocodingOp");

            dialog.testRunGraph();
        }
    }       */
}