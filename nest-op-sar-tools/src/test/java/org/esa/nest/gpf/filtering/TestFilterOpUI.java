package org.esa.nest.gpf.filtering;

import junit.framework.TestCase;
import org.esa.beam.framework.gpf.GPF;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit test for SingleTileOperator.
 */
public class TestFilterOpUI extends TestCase {

    private FilterOpUI filterOpUI;
    private final Map<String, Object> parameterMap = new HashMap<String, Object>(5);

    @Override
    protected void setUp() throws Exception {
        filterOpUI = new FilterOpUI();

        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new FilterOperator.Spi());
    }

    @Override
    protected void tearDown() throws Exception {
        filterOpUI = null;
    }

    public void testCreateOpTab() {

        JComponent component = filterOpUI.CreateOpTab("Image-Filter", parameterMap, null);
        assertNotNull(component);
    }

    public void testLoadParameters() {

        parameterMap.put("selectedFilterName", "High-Pass 5x5");
        JComponent component = filterOpUI.CreateOpTab("Image-Filter", parameterMap, null);
        assertNotNull(component);

        FilterOperator.Filter filter = FilterOpUI.getSelectedFilter(filterOpUI.getTree());
        assertNotNull(filter);

        filterOpUI.setSelectedFilter("Median 5x5");

        filterOpUI.updateParameters();

        Object o = parameterMap.get("selectedFilterName");
        assertTrue(((String)o).equals("Median 5x5"));
    }

}
