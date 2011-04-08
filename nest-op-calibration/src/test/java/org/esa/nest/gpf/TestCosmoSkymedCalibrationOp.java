/*
 * Copyright (C) 2011 by Array Systems Computing Inc. http://www.array.ca
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
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.nest.util.TestUtils;

import java.io.File;

/**
 * Unit test for Calibration Operator.
 */
public class TestCosmoSkymedCalibrationOp extends TestCase {

    private OperatorSpi spi;
    private final static String inputPath =     TestUtils.rootPathExpectedProducts+"\\input\\subset_CSKS3_SCS_B_PP_01_CO_RA_SF_20100513.dim";
    private final static String expectedPath =  TestUtils.rootPathExpectedProducts+"\\expected\\Calib.dim";


    @Override
    protected void setUp() throws Exception {
        spi = new CalibrationOp.Spi();
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(spi);
    }

    @Override
    protected void tearDown() throws Exception {
        GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(spi);
    }

    public void testProcessingLevel1_1() throws Exception {
        processFile(inputPath, expectedPath);
    }

    /**
     * Processes a product and compares it to processed product known to be correct
     * @param inputPath the path to the input product
     * @param expectedPath the path to the expected product
     * @throws Exception general exception
     */
    public void processFile(String inputPath, String expectedPath) throws Exception {

        final File inputFile = new File(inputPath);
        if(!inputFile.exists()) return;

        final ProductReader reader = ProductIO.getProductReaderForFile(inputFile);
        assertNotNull(reader);
        final Product sourceProduct = reader.readProductNodes(inputFile, null);

        final CalibrationOp op = (CalibrationOp)spi.createOperator();
        assertNotNull(op);
        op.setSourceProduct(sourceProduct);

        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, false, false);
        TestUtils.compareProducts(op, targetProduct, expectedPath, null);
    }    
}