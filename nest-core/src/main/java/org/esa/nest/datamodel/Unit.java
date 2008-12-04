package org.esa.nest.datamodel;

import org.esa.beam.framework.datamodel.Band;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Nov 27, 2008
 * Time: 3:25:19 PM
 * To change this template use File | Settings | File Templates.
 */
public class Unit {

    public static final String AMPLITUDE = "amplitude";
    public static final String INTENSITY = "intensity";
    public static final String PHASE = "phase";

    public static final String REAL = "real";
    public static final String IMAGINARY = "imaginary";

    public static final String DB = "db";

    public static final String AMPLITUDE_DB = AMPLITUDE+'_'+DB;
    public static final String INTENSITY_DB = INTENSITY+'_'+DB;

    public enum UnitType { AMPLITUDE, INTENSITY, REAL, IMAGINARY, PHASE, INTENSITY_DB, AMPLITUDE_DB, UNKNOWN }

    public static UnitType getUnitType(Band sourceBand) {

        String  unit =  sourceBand.getUnit();
        if (unit.contains(AMPLITUDE)) {
            if (unit.contains(DB))
                return UnitType.AMPLITUDE_DB;
            else
                return UnitType.AMPLITUDE;
        } else if (unit.contains(INTENSITY)) {
            if (unit.contains(DB))
                return UnitType.INTENSITY_DB;
            else
                return UnitType.INTENSITY;
        } else if (unit.contains(PHASE)) {
            return UnitType.PHASE;
        } else if (unit.contains(REAL)) {
             return UnitType.REAL;
        } else if (unit.contains(IMAGINARY)) {
             return UnitType.IMAGINARY;
        } else {
            return UnitType.UNKNOWN;
        }
    }
}
