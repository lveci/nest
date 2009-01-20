package org.esa.nest.dataio;

import junit.framework.TestCase;
import ucar.nc2.Variable;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFileWriteable;
import ucar.nc2.dataset.NetcdfDataset;

import java.io.IOException;
import java.util.List;


/**
 *
 * @author lveci
 */
public class TestNetCDFReader extends TestCase {

    private final String geoTiffFile = "F:\\data\\GIS\\Tiff\\Osaka Japan\\tc_osaka_geo.tif";
    private final String netCDFFile = "C:\\Data\\netcdf_data\\tos_O1_2001-2002.nc";
    private final String hdfFile = "C:\\Data\\netcdf_data\\PALAPR01.h5";

    public TestNetCDFReader(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        super.setUp();
    }

    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testOpenNetCDF() throws IOException {
/*        NetcdfDataset ds = NetcdfDataset.openDataset(netCDFFile);

        List<Variable> varList = ds.getVariables();

        List<Group> groupList = ds.getRootGroup().getGroups();
         */

    }

    public void testOpenHDF() throws IOException {
  /*      NetcdfDataset ds = NetcdfDataset.openDataset(hdfFile);

        List<Variable> varList = ds.getVariables();

        List<Group> groupList = ds.getRootGroup().getGroups();

        */
    }

    public void testWriteHDF() throws IOException {

       //NetcdfFileWriteable netCDFWriteable = NetcdfFileWriteable.createNew("ha");




    }
}