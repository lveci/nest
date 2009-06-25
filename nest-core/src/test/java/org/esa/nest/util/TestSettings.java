package org.esa.nest.util;

import junit.framework.TestCase;

/**
 * Settings Tester.
 *
 * @author lveci
 */
public class TestSettings extends TestCase {

    public TestSettings(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testLoadSettings()
    {        
        final Settings settings = Settings.instance();

        String value1 = settings.get("AuxData/envisatAuxDataPath");

        String value2 = settings.get("DEM/srtm3GeoTiffDEM_FTP");
    }

    public void testGet()
    {
        final Settings settings = Settings.instance();

        String value = settings.get("DEM/srtm3GeoTiffDEM_FTP");
    }
}