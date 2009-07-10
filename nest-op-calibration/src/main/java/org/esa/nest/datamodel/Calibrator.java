package org.esa.nest.datamodel;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.Tile;

import java.util.HashMap;
import java.io.File;

/**
* The abstract base class for all calibration operators intended to be extended by clients.
 * The following methods are intended to be implemented or overidden:
 */
public interface Calibrator {

    public void initialize(Product sourceProduct, Product targetProduct) throws OperatorException;

    public void computeTile(Band targetBand, Tile targetTile,
                            HashMap<String, String[]> targetBandNameToSourceBandName,
                            com.bc.ceres.core.ProgressMonitor pm) throws OperatorException;

    public void setOutputImageIndB(boolean flag);

    public void setExternalAuxFile(File file);
}