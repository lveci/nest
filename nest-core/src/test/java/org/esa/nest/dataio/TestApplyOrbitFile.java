package org.esa.nest.dataio;

import junit.framework.TestCase;
import org.esa.beam.dataio.envisat.EnvisatOrbitReader;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Sep 4, 2008
 * To change this template use File | Settings | File Templates.
 */
public class TestApplyOrbitFile  extends TestCase {

    File vorPath = new File("P:\\nest\\nest\\ESA Data\\Orbits\\Doris\\vor");
    
    public TestApplyOrbitFile(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        super.setUp();
    }

    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testOpenFile() {

        EnvisatOrbitReader reader = new EnvisatOrbitReader();

        //Date productDate = new Date(12345678);
        //OrbitFileUpdater.FindOrbitFile(reader, vorPath, productDate);
    }
}
