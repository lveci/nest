package org.esa.nest.datamodel;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.Tile;

import java.util.HashMap;
import java.io.File;

import com.bc.ceres.core.ProgressMonitor;

/**
* The abstract base class for all calibration operators intended to be extended by clients.
 * The following methods are intended to be implemented or overidden:
 */
public interface Calibrator {

    /**
     *
     * @param sourceProduct The source product.
     * @param targetProduct The target product.
     * @param mustPerformRetroCalibration For absolut calibration, this flag is false because retro-calibration may not
     *        be needed in case XCA file is not available or the old and new XCA files are identical. For radiometric
     *        normalization in Terrain Correction, this flag is true because retro-calibration is always needed.
     * @param mustUpdateMetadata For Pre-Calibration, thie flag is false because calibration has net been performed.
     *        For absolut calibration or radiometric normalization, the flag is true.
     * @throws OperatorException The exception.
     */
    public void initialize(Product sourceProduct, Product targetProduct,
                           boolean mustPerformRetroCalibration, boolean mustUpdateMetadata)
            throws OperatorException;

    public void computeTile(Band targetBand, Tile targetTile,
                            HashMap<String, String[]> targetBandNameToSourceBandName,
                            com.bc.ceres.core.ProgressMonitor pm) throws OperatorException;

    public void setOutputImageIndB(boolean flag);

    public void setExternalAuxFile(File file);

    public double applyRetroCalibration(int x, int y, double v, int bandPolar, final Unit.UnitType bandUnit, int[] subSwathIndex);

    public double applyCalibration(
            final double v, final int rangeIndex, final double slantRange, final double satelliteHeight,
            final double sceneToEarthCentre, final double localIncidenceAngle, final int bandPolar,
            final Unit.UnitType bandUnit, int[] subSwathIndex);
    
    public void removeFactorsForCurrentTile(Band targetBand, Tile targetTile, String srcBandName, ProgressMonitor pm);
}