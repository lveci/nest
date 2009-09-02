package org.esa.nest.datamodel;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.nest.gpf.ALOSCalibrator;
import org.esa.nest.gpf.ASARCalibrator;
import org.esa.nest.gpf.ERSCalibrator;
import org.esa.nest.gpf.Radarsat2Calibrator;

/**
* The abstract base class for all calibration operators intended to be extended by clients.
 * The following methods are intended to be implemented or overidden:
 */
public class CalibrationFactory {

    public static Calibrator createCalibrator(Product sourceProduct)
                                            throws OperatorException, IllegalArgumentException {

        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);
        if(absRoot == null) {
            throw new OperatorException("AbstractMetadata is null");
        }
        final String mission = absRoot.getAttributeString(AbstractMetadata.MISSION);

        if(mission.equals("ENVISAT")) {
            return new ASARCalibrator();
        } else if(mission.contains("ERS1") || mission.contains("ERS2")) {
            return new ERSCalibrator();
        } else if(mission.equals("ALOS")) {
            return new ALOSCalibrator();
        } else if(mission.equals("RS2")) {
            return new Radarsat2Calibrator();
        }
        return null;
    }

}