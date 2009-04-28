/*
 * $Id: ElevationModel.java,v 1.1 2009-04-28 14:39:33 lveci Exp $
 *
 * Copyright (c) 2003 Brockmann Consult GmbH. All right reserved.
 * http://www.brockmann-consult.de
 */
package org.esa.beam.framework.dataop.dem;

import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.dataop.resamp.Resampling;

/**
 * An <code>ElevationModel</code> is used to obtain an elevation above a
 * specified geographical datum for a given geographical position.
 *
 * @author Norman Fomferra
 * @version $Revision: 1.1 $
 */
public interface ElevationModel {

    /**
     * Gets the descriptor of this DEM.
     * @return the descriptor which is never null
     */
    ElevationModelDescriptor getDescriptor();

    /**
     * Gets the elevation at the geographical coordinate in meters.
     * @param geoPos  the geographical coordinate
     * @return  an elevation in meters, or the special value returned by {@link ElevationModelDescriptor#getNoDataValue()} if an elevation is not available
     * @exception Exception if a non-runtime error occurs, e.g I/O error
     */
    float getElevation(GeoPos geoPos) throws Exception;

    /**
     * Sets the resampling method to use
     * @param method the interpolation
     */
    void setResamplingMethod(Resampling method);

    /**
     * Releases all of the resources used by this object instance and all of its owned children. Its primary use is to
     * allow the garbage collector to perform a vanilla job.
     * <p/>
     * <p>This method should be called only if it is for sure that this object instance will never be used again. The
     * results of referencing an instance of this class after a call to <code>dispose()</code> are undefined.
     * <p/>
     * <p>Overrides of this method should always call <code>super.dispose();</code> after disposing this instance.
     * <p/>
     */
    void dispose();
}
