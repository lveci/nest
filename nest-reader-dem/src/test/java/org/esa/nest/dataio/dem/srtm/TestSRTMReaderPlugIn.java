package org.esa.nest.dataio.dem.srtm;

import junit.framework.TestCase;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.DecodeQualification;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;


public class TestSRTMReaderPlugIn extends TestCase {

    private SRTMReaderPlugIn _plugIn;

    @Override
    protected void setUp() throws Exception {
        _plugIn = new SRTMReaderPlugIn();
    }

    @Override
    protected void tearDown() throws Exception {
        _plugIn = null;
    }

    public void testValidInputs() {
        testValidInput("e020s10.DEM");
        testValidInput("w100s10.DEM"); 
        testValidInput("w100s10.DEM.zip");
    }

    private void testValidInput(final String s) {
        assertTrue(_plugIn.getDecodeQualification(s) == DecodeQualification.INTENDED);
        assertTrue(_plugIn.getDecodeQualification(new File(s)) == DecodeQualification.INTENDED);
    }

    public void testInvalidInputs() {
        testInvalidInput("10n143w.SRTM");
        testInvalidInput("w100s10.jpg.zip");
        testInvalidInput("./SRTM/00N015W.SRTM.zip");
        testInvalidInput("./SRTM/readme.txt");
        testInvalidInput("./SRTM/readme.txt.zip");
        testInvalidInput("./SRTM/readme");
        testInvalidInput("./SRTM/");
        testInvalidInput("./");
        testInvalidInput(".");
        testInvalidInput("");
    }

    private void testInvalidInput(final String s) {
        assertEquals(DecodeQualification.UNABLE, _plugIn.getDecodeQualification(s));
        assertEquals(DecodeQualification.UNABLE, _plugIn.getDecodeQualification(new File(s)));
    }

    public void testThatOtherTypesCannotBeDecoded() throws MalformedURLException {
        assertEquals(DecodeQualification.UNABLE, _plugIn.getDecodeQualification(null));
        final URL url = new File("./SRTM/readme.txt").toURI().toURL();
        assertEquals(DecodeQualification.UNABLE, _plugIn.getDecodeQualification(url));
        final Object object = new Object();
        assertEquals(DecodeQualification.UNABLE, _plugIn.getDecodeQualification(object));
    }

    public void testCreateReaderInstance() {
        final ProductReader reader = _plugIn.createReaderInstance();
        assertTrue(reader instanceof SRTMReader);
    }

    public void testGetInputTypes() {
        final Class[] inputTypes = _plugIn.getInputTypes();
        assertNotNull(inputTypes);
        assertTrue(inputTypes.length == 2);
        assertEquals(String.class, inputTypes[0]);
        assertEquals(File.class, inputTypes[1]);
    }

    public void testGetFormatNames() {
        final String[] formatNames = _plugIn.getFormatNames();
        assertNotNull(formatNames);
        assertTrue(formatNames.length == 1);
        assertEquals("SRTM30", formatNames[0]);
    }

    public void testGetDefaultFileExtensions() {
        final String[] defaultFileExtensions = _plugIn.getDefaultFileExtensions();
        assertNotNull(defaultFileExtensions);
        assertEquals(".dem", defaultFileExtensions[0]);
    }

}
