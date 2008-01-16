package org.esa.nest.dat.plugins;

import junit.framework.TestCase;

/**
 * GraphBuilderVPI Tester.
 *
 * @author <Authors name>
 * @since <pre>12/21/2007</pre>
 * @version 1.0
 */
public class TestGraphExecuter extends TestCase {

    private GraphBuilderVPI _gp;

    public TestGraphExecuter(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        _gp = new GraphBuilderVPI();

    }

    public void tearDown() throws Exception {
        _gp = null;
    }

    public void testSomething() {

        assertTrue(true);
    }
}
