package org.esa.nest.dat.plugins;

import junit.framework.TestCase;
import java.util.Set;

/**
 * GraphProcessorVPI Tester.
 *
 * @author <Authors name>
 * @since <pre>12/21/2007</pre>
 * @version 1.0
 */
public class GraphProcessorVPITest extends TestCase {

    private GraphProcessorVPI _gp;

    public GraphProcessorVPITest(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        _gp = new GraphProcessorVPI();

    }

    public void tearDown() throws Exception {
        _gp = null;
    }


}
