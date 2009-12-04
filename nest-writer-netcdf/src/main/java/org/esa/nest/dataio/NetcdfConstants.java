package org.esa.nest.dataio;

/**
 * Provides most of the constants used in this package.
 */
public interface NetcdfConstants {

    final static String[] NETCDF_FORMAT_NAMES = { "NetCDF" };
	final static String[] NETCDF_FORMAT_FILE_EXTENSIONS = { "nc", "nc3" };
    final static String NETCDF_PLUGIN_DESCRIPTION = "NetCDF Products";

    final static String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    final static String GLOBAL_ATTRIBUTES_NAME = "Global_Attributes";

    final static String SCALE_FACTOR_ATT_NAME = "scale_factor";
    final static String SLOPE_ATT_NAME = "slope";
    final static String ADD_OFFSET_ATT_NAME = "add_offset";
    final static String INTERCEPT_ATT_NAME = "intercept";
    final static String FILL_VALUE_ATT_NAME = "_FillValue";
    final static String MISSING_VALUE_ATT_NAME = "missing_value";
    final static String VALID_MIN_ATT_NAME = "valid_min";
    final static String VALID_MAX_ATT_NAME = "valid_max";
    final static String STEP_ATT_NAME = "step";
    final static String START_DATE_ATT_NAME = "start_date";
    final static String START_TIME_ATT_NAME = "start_time";
    final static String STOP_DATE_ATT_NAME = "stop_date";
    final static String STOP_TIME_ATT_NAME = "stop_time";

    // CF convention lon
    // COARDS convention longitude
    // Enviview longs first_line_tie_points.longs
    final static String[] LON_VAR_NAMES = { "lon", "longitude", "longs", "first_line_tie_points.longs" };
    final static String[] LAT_VAR_NAMES = { "lat", "latitude", "lats", "first_line_tie_points.lats" };
}