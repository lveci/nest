/*
 * $Id: DimapHeaderWriterTest.java,v 1.5 2009-08-06 15:21:21 lveci Exp $
 *
 * Copyright (C) 2002 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.dataio.dimap;

import com.bc.ceres.core.ProgressMonitor;
import junit.framework.TestCase;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.BitmaskDef;
import org.esa.beam.framework.datamodel.ConvolutionFilterBand;
import org.esa.beam.framework.datamodel.FXYGeoCoding;
import org.esa.beam.framework.datamodel.GeneralFilterBand;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.Kernel;
import org.esa.beam.framework.datamodel.MapGeoCoding;
import org.esa.beam.framework.datamodel.Pin;
import org.esa.beam.framework.datamodel.PixelGeoCoding;
import org.esa.beam.framework.datamodel.PlacemarkSymbol;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.framework.datamodel.VirtualBand;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.framework.dataop.maptransf.Ellipsoid;
import org.esa.beam.framework.dataop.maptransf.LambertConformalConicDescriptor;
import org.esa.beam.framework.dataop.maptransf.MapInfo;
import org.esa.beam.framework.dataop.maptransf.MapProjection;
import org.esa.beam.framework.dataop.maptransf.MapTransform;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.math.FXYSum;

import java.awt.Color;
import java.io.IOException;
import java.io.StringWriter;

public class DimapHeaderWriterTest extends TestCase {

    private static final String LS = SystemUtils.LS;
    private static final String header =
            "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + LS +
            "<Dimap_Document name=\"test.dim\">" + LS +
            "    <Metadata_Id>" + LS +
            "        <METADATA_FORMAT version=\"" + DimapProductConstants.DIMAP_CURRENT_VERSION + "\">DIMAP</METADATA_FORMAT>" + LS +
            "        <METADATA_PROFILE>" + DimapProductConstants.DIMAP_METADATA_PROFILE + "</METADATA_PROFILE>" + LS +
            "    </Metadata_Id>" + LS +
            "    <Dataset_Id>" + LS +
            "        <DATASET_SERIES>" + DimapProductConstants.DIMAP_DATASET_SERIES + "</DATASET_SERIES>" + LS +
            "        <DATASET_NAME>test</DATASET_NAME>" + LS +
            "    </Dataset_Id>" + LS +
            "    <Production>" + LS +
            "        <DATASET_PRODUCER_NAME />" + LS +
//            "        <DATASET_PRODUCER_NAME>" + DimapProductConstants.DATASET_PRODUCER_NAME + "</DATASET_PRODUCER_NAME>" + LS +
"        <PRODUCT_TYPE>MER_RR__2P</PRODUCT_TYPE>" + LS +
"        <PRODUCT_SCENE_RASTER_START_TIME>19-MAY-2003 00:34:05.000034</PRODUCT_SCENE_RASTER_START_TIME>" + LS + // product scene sensing start
"        <PRODUCT_SCENE_RASTER_STOP_TIME>19-MAY-2003 00:50:45.000034</PRODUCT_SCENE_RASTER_STOP_TIME>" + LS + // product scene sensing stopt
"    </Production>" + LS;
    private static final String rasterDimensions =
            "    <Raster_Dimensions>" + LS +
            "        <NCOLS>200</NCOLS>" + LS +
            "        <NROWS>300</NROWS>" + LS +
            "        <NBANDS>0</NBANDS>" + LS +
            "    </Raster_Dimensions>" + LS;
    private static final String dataAccess =
            "    <Data_Access>" + LS +
            "        <DATA_FILE_FORMAT>ENVI</DATA_FILE_FORMAT>" + LS +
            "        <DATA_FILE_FORMAT_DESC>ENVI File Format</DATA_FILE_FORMAT_DESC>" + LS +
            "        <DATA_FILE_ORGANISATION>BAND_SEPARATE</DATA_FILE_ORGANISATION>" + LS +
            "        <Data_File>" + LS +
            "            <DATA_FILE_PATH href=\"test.data/b1.hdr\" />" + LS +
            "            <BAND_INDEX>0</BAND_INDEX>" + LS +
            "        </Data_File>" + LS +
            "        <Data_File>" + LS +
            "            <DATA_FILE_PATH href=\"test.data/b2.hdr\" />" + LS +
            "            <BAND_INDEX>1</BAND_INDEX>" + LS +
            "        </Data_File>" + LS +
            "    </Data_Access>" + LS +
            "    <Image_Interpretation>" + LS +
            "        <Spectral_Band_Info>" + LS +
            "            <BAND_INDEX>0</BAND_INDEX>" + LS +
            "            <BAND_DESCRIPTION />" + LS +
            "            <BAND_NAME>b1</BAND_NAME>" + LS +
            "            <DATA_TYPE>int8</DATA_TYPE>" + LS +
            "            <SOLAR_FLUX>0.0</SOLAR_FLUX>" + LS +
            "            <BAND_WAVELEN>0.0</BAND_WAVELEN>" + LS +
            "            <BANDWIDTH>0.0</BANDWIDTH>" + LS +
            "            <SCALING_FACTOR>1.0</SCALING_FACTOR>" + LS +
            "            <SCALING_OFFSET>0.0</SCALING_OFFSET>" + LS +
            "            <LOG10_SCALED>false</LOG10_SCALED>" + LS +
            "            <NO_DATA_VALUE_USED>false</NO_DATA_VALUE_USED>" + LS +
            "            <NO_DATA_VALUE>0.0</NO_DATA_VALUE>" + LS +
            "        </Spectral_Band_Info>" + LS +
            "        <Spectral_Band_Info>" + LS +
            "            <BAND_INDEX>1</BAND_INDEX>" + LS +
            "            <BAND_DESCRIPTION />" + LS +
            "            <BAND_NAME>b2</BAND_NAME>" + LS +
            "            <DATA_TYPE>int8</DATA_TYPE>" + LS +
            "            <SOLAR_FLUX>0.0</SOLAR_FLUX>" + LS +
            "            <BAND_WAVELEN>0.0</BAND_WAVELEN>" + LS +
            "            <BANDWIDTH>0.0</BANDWIDTH>" + LS +
            "            <SCALING_FACTOR>1.0</SCALING_FACTOR>" + LS +
            "            <SCALING_OFFSET>0.0</SCALING_OFFSET>" + LS +
            "            <LOG10_SCALED>false</LOG10_SCALED>" + LS +
            "            <NO_DATA_VALUE_USED>false</NO_DATA_VALUE_USED>" + LS +
            "            <NO_DATA_VALUE>0.0</NO_DATA_VALUE>" + LS +
            "        </Spectral_Band_Info>" + LS +
            "        <Spectral_Band_Info>" + LS +
            "            <BAND_INDEX>2</BAND_INDEX>" + LS +
            "            <BAND_DESCRIPTION />" + LS +
            "            <BAND_NAME>vb1</BAND_NAME>" + LS +
            "            <DATA_TYPE>int8</DATA_TYPE>" + LS +
            "            <SOLAR_FLUX>0.0</SOLAR_FLUX>" + LS +
            "            <BAND_WAVELEN>0.0</BAND_WAVELEN>" + LS +
            "            <BANDWIDTH>0.0</BANDWIDTH>" + LS +
            "            <SCALING_FACTOR>1.0</SCALING_FACTOR>" + LS +
            "            <SCALING_OFFSET>0.0</SCALING_OFFSET>" + LS +
            "            <LOG10_SCALED>false</LOG10_SCALED>" + LS +
            "            <NO_DATA_VALUE_USED>false</NO_DATA_VALUE_USED>" + LS +
            "            <NO_DATA_VALUE>0.0</NO_DATA_VALUE>" + LS +
            "            <VIRTUAL_BAND>true</VIRTUAL_BAND>" + LS +
            "            <EXPRESSION>b1 * 0.4 + 1</EXPRESSION>" + LS +
            "        </Spectral_Band_Info>" + LS +
            "        <Spectral_Band_Info>" + LS +
            "            <BAND_INDEX>3</BAND_INDEX>" + LS +
            "            <BAND_NAME>cfb1</BAND_NAME>" + LS +
            "            <BAND_DESCRIPTION />" + LS +
            "            <DATA_TYPE>float32</DATA_TYPE>" + LS +
            "            <PHYSICAL_UNIT />" + LS +
            "            <SOLAR_FLUX>0.0</SOLAR_FLUX>" + LS +
            "            <BAND_WAVELEN>0.0</BAND_WAVELEN>" + LS +
            "            <BANDWIDTH>0.0</BANDWIDTH>" + LS +
            "            <SCALING_FACTOR>1.0</SCALING_FACTOR>" + LS +
            "            <SCALING_OFFSET>0.0</SCALING_OFFSET>" + LS +
            "            <LOG10_SCALED>false</LOG10_SCALED>" + LS +
            "            <NO_DATA_VALUE_USED>false</NO_DATA_VALUE_USED>" + LS +
            "            <NO_DATA_VALUE>0.0</NO_DATA_VALUE>" + LS +
            "            <Filter_Band_Info bandType=\"ConvolutionFilterBand\">" + LS +
            "                <FILTER_SOURCE>b2</FILTER_SOURCE>" + LS +
            "                <Filter_Kernel>" + LS +
            "                    <KERNEL_WIDTH>3</KERNEL_WIDTH>" + LS +
            "                    <KERNEL_HEIGHT>3</KERNEL_HEIGHT>" + LS +
            "                    <KERNEL_FACTOR>1.0</KERNEL_FACTOR>" + LS +
            "                    <KERNEL_DATA>1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0</KERNEL_DATA>" + LS +
            "                </Filter_Kernel>" + LS +
            "            </Filter_Band_Info>" + LS +
            "        </Spectral_Band_Info>" + LS +
            "        <Spectral_Band_Info>" + LS +
            "            <BAND_INDEX>4</BAND_INDEX>" + LS +
            "            <BAND_NAME>gfb1</BAND_NAME>" + LS +
            "            <BAND_DESCRIPTION />" + LS +
            "            <DATA_TYPE>int8</DATA_TYPE>" + LS +
            "            <PHYSICAL_UNIT />" + LS +
            "            <SOLAR_FLUX>0.0</SOLAR_FLUX>" + LS +
            "            <BAND_WAVELEN>0.0</BAND_WAVELEN>" + LS +
            "            <BANDWIDTH>0.0</BANDWIDTH>" + LS +
            "            <SCALING_FACTOR>1.0</SCALING_FACTOR>" + LS +
            "            <SCALING_OFFSET>0.0</SCALING_OFFSET>" + LS +
            "            <LOG10_SCALED>false</LOG10_SCALED>" + LS +
            "            <NO_DATA_VALUE_USED>false</NO_DATA_VALUE_USED>" + LS +
            "            <NO_DATA_VALUE>0.0</NO_DATA_VALUE>" + LS +
            "            <Filter_Band_Info bandType=\"GeneralFilterBand\" version=\"1.1\">" + LS +
            "                <FILTER_SOURCE>b2</FILTER_SOURCE>" + LS +
            "                <FILTER_SUB_WINDOW_SIZE>150</FILTER_SUB_WINDOW_SIZE>" + LS +
            "                <FILTER_OPERATOR_CLASS_NAME>org.esa.beam.framework.datamodel.GeneralFilterBand$Mean</FILTER_OPERATOR_CLASS_NAME>" + LS +
            "            </Filter_Band_Info>" + LS +
            "        </Spectral_Band_Info>" + LS +
            "    </Image_Interpretation>" + LS;

    private static final String footer = "</Dimap_Document>";
    private Product product;
    private StringWriter stringWriter;
    private DimapHeaderWriter dimapHeaderWriter;

    public DimapHeaderWriterTest(String s) {
        super(s);
    }

    @Override
    protected void setUp() throws Exception {
        product = new Product("test", "MER_RR__2P", 200, 300);
        product.setStartTime(new ProductData.UTC(1234, 2045, 34));
        product.setEndTime(new ProductData.UTC(1234, 3045, 34));
        stringWriter = new StringWriter();
        dimapHeaderWriter = new DimapHeaderWriter(product, stringWriter, "test.data");
    }


    // ###################################################
    // ##  W r i t e   X m l   H e a d e r   L i n e s  ##
    // ###################################################
    public void testWriteXmlHeaderLines() {
        dimapHeaderWriter.writeHeader();

        final String expected = header + rasterDimensions + footer;
        assertEquals(expected, stringWriter.toString());
    }

    // ###########################
    // ##  W r i t e   P i n s  ##
    // ###########################
    public void testWritePins() {
        addPinsToProduct();

        dimapHeaderWriter.writeHeader();

        assertEquals(getExpectedForWritePins(), stringWriter.toString());
    }

    // ###########################
    // ##  W r i t e   G C P s  ##
    // ###########################
    public void testWriteGcps() {
        addGcpsToProduct();

        dimapHeaderWriter.writeHeader();

        assertEquals(getExpectedForWriteGcps(), stringWriter.toString());
    }

    // #########################################
    // ##  W r i t e   B i t m a s k D e f s  ##
    // #########################################
    public void testWriteBitmaskDefs() {
        addBitmaskDefsToProduct();

        dimapHeaderWriter.writeHeader();

        assertEquals(getExpectedForWriteBitmaskDefs(), stringWriter.toString());
    }

    // #############################################
    // ##  W r i t e   M a p   G e o c o d i n g  ##
    // #############################################
    public void testWriteMapGeocoding() {
        final String expected = addMapGeocodingToProductAndGetExpected();

        dimapHeaderWriter.writeHeader();

        assertEquals(expected, stringWriter.toString());
    }

    // ###########################################
    // ##  W r i t e   F X Y G e o C o d i n g  ##
    // ###########################################
    public void testWriteFXYGeoCoding() {
        final String expectedForFXYGeoCoding = setFXYGeoCodingAndGetExpected();

        dimapHeaderWriter.writeHeader();

        assertEquals(expectedForFXYGeoCoding, stringWriter.toString());
    }

    // ########################################################
    // ##  W r i t e   B a n d e d  F X Y G e o C o d i n g  ##
    // ########################################################
    public void testWriteBandedFXYGeoCoding() {
        final String expectedForBandedFXYGeoCoding = setBandedFXYGeoCodingAndGetExpected();

        dimapHeaderWriter.writeHeader();

        assertEquals(expectedForBandedFXYGeoCoding, stringWriter.toString());
    }

    // ###############################################
    // ##  W r i t e   P i x e l G e o C o d i n g  ##
    // ###############################################
    public void testWritePixelGeoCoding() throws IOException {
        final String expectedForPixelGeoCoding = setPixelGeoCodingAndGetExpected();

        dimapHeaderWriter.writeHeader();

        assertEquals(expectedForPixelGeoCoding, stringWriter.toString());
    }

    public void testWritePixelGeoCodingWithoutEstimator() throws IOException {
        final String expectedForPixelGeoCoding = setPixelGeoCodingWithoutEstimatorAndGetExpected();

        dimapHeaderWriter.writeHeader();

        assertEquals(expectedForPixelGeoCoding, stringWriter.toString());
    }

    private void addPinsToProduct() {
        final Pin pin1 = new Pin("pin1", "pin1", "", null, new GeoPos(), PlacemarkSymbol.createDefaultPinSymbol());
        ProductNodeGroup<Pin> pinGroup = product.getPinGroup();
        pinGroup.add(pin1);

        final Pin pin2 = new Pin("pin2", "pin2", "", null, new GeoPos(4, 8), PlacemarkSymbol.createDefaultPinSymbol());
        pin2.setDescription("desc2");
        pinGroup.add(pin2);

        final Pin pin3 = new Pin("pin3", "pin3", "", null, new GeoPos(-23.1234f, -80.543f),
                                 PlacemarkSymbol.createDefaultPinSymbol());
        pinGroup.add(pin3);
    }

    private String getExpectedForWritePins() {
        return
                header +
                rasterDimensions +
                "    <Pin_Group>" + LS +
                "        <Placemark name=\"pin1\">" + LS +
                "            <LABEL>pin1</LABEL>" + LS +
                "            <DESCRIPTION />" + LS +
                "            <LATITUDE>0.0</LATITUDE>" + LS +
                "            <LONGITUDE>0.0</LONGITUDE>" + LS +
                "            <FillColor>" + LS +
                "                <COLOR red=\"128\" green=\"128\" blue=\"255\" alpha=\"255\" />" + LS +
                "            </FillColor>" + LS +
                "            <OutlineColor>" + LS +
                "                <COLOR red=\"0\" green=\"0\" blue=\"64\" alpha=\"255\" />" + LS +
                "            </OutlineColor>" + LS +
                "        </Placemark>" + LS +
                "        <Placemark name=\"pin2\">" + LS +
                "            <LABEL>pin2</LABEL>" + LS +
                "            <DESCRIPTION>desc2</DESCRIPTION>" + LS +
                "            <LATITUDE>4.0</LATITUDE>" + LS +
                "            <LONGITUDE>8.0</LONGITUDE>" + LS +
                "            <FillColor>" + LS +
                "                <COLOR red=\"128\" green=\"128\" blue=\"255\" alpha=\"255\" />" + LS +
                "            </FillColor>" + LS +
                "            <OutlineColor>" + LS +
                "                <COLOR red=\"0\" green=\"0\" blue=\"64\" alpha=\"255\" />" + LS +
                "            </OutlineColor>" + LS +
                "        </Placemark>" + LS +
                "        <Placemark name=\"pin3\">" + LS +
                "            <LABEL>pin3</LABEL>" + LS +
                "            <DESCRIPTION />" + LS +
                "            <LATITUDE>-23.1234</LATITUDE>" + LS +
                "            <LONGITUDE>-80.543</LONGITUDE>" + LS +
                "            <FillColor>" + LS +
                "                <COLOR red=\"128\" green=\"128\" blue=\"255\" alpha=\"255\" />" + LS +
                "            </FillColor>" + LS +
                "            <OutlineColor>" + LS +
                "                <COLOR red=\"0\" green=\"0\" blue=\"64\" alpha=\"255\" />" + LS +
                "            </OutlineColor>" + LS +
                "        </Placemark>" + LS +
                "    </Pin_Group>" + LS +
                footer;
    }

    private void addGcpsToProduct() {
        final Pin pin1 = new Pin("gcp1", "gcp1", "", null, new GeoPos(), PlacemarkSymbol.createDefaultGcpSymbol());
        ProductNodeGroup<Pin> pinGroup = product.getGcpGroup();
        pinGroup.add(pin1);

        final Pin pin2 = new Pin("gcp2", "gcp2", "", null, new GeoPos(4, 8), PlacemarkSymbol.createDefaultGcpSymbol());
        pin2.setDescription("desc2");
        pinGroup.add(pin2);

        final Pin pin3 = new Pin("gcp3", "gcp3", "", null, new GeoPos(-23.1234f, -80.543f),
                                 PlacemarkSymbol.createDefaultGcpSymbol());
        pinGroup.add(pin3);
    }

    private String getExpectedForWriteGcps() {
        return
                header +
                rasterDimensions +
                "    <Gcp_Group>" + LS +
                "        <Placemark name=\"gcp1\">" + LS +
                "            <LABEL>gcp1</LABEL>" + LS +
                "            <DESCRIPTION />" + LS +
                "            <LATITUDE>0.0</LATITUDE>" + LS +
                "            <LONGITUDE>0.0</LONGITUDE>" + LS +
                "        </Placemark>" + LS +
                "        <Placemark name=\"gcp2\">" + LS +
                "            <LABEL>gcp2</LABEL>" + LS +
                "            <DESCRIPTION>desc2</DESCRIPTION>" + LS +
                "            <LATITUDE>4.0</LATITUDE>" + LS +
                "            <LONGITUDE>8.0</LONGITUDE>" + LS +
                "        </Placemark>" + LS +
                "        <Placemark name=\"gcp3\">" + LS +
                "            <LABEL>gcp3</LABEL>" + LS +
                "            <DESCRIPTION />" + LS +
                "            <LATITUDE>-23.1234</LATITUDE>" + LS +
                "            <LONGITUDE>-80.543</LONGITUDE>" + LS +
                "        </Placemark>" + LS +
                "    </Gcp_Group>" + LS +
                footer;
    }

    private void addBitmaskDefsToProduct() {
        BitmaskDef bitmaskDef1 = new BitmaskDef("bitmaskDef1", "description1", "!l1_flags.INVALID", Color.BLUE, 0.75f);
        product.addBitmaskDef(bitmaskDef1);

        BitmaskDef bitmaskDef2 = new BitmaskDef("bitmaskDef2", "description2", "l1_flags.LAND", Color.GREEN, 0.5f);
        product.addBitmaskDef(bitmaskDef2);
    }

    private String getExpectedForWriteBitmaskDefs() {
        return
                header +
                rasterDimensions +
                "    <Bitmask_Definitions>" + LS +
                "        <Bitmask_Definition name=\"bitmaskDef1\">" + LS +
                "            <DESCRIPTION value=\"description1\" />" + LS +
                "            <EXPRESSION value=\"!l1_flags.INVALID\" />" + LS +
                "            <COLOR red=\"0\" green=\"0\" blue=\"255\" alpha=\"255\" />" + LS +
                "            <TRANSPARENCY value=\"0.75\" />" + LS +
                "        </Bitmask_Definition>" + LS +
                "        <Bitmask_Definition name=\"bitmaskDef2\">" + LS +
                "            <DESCRIPTION value=\"description2\" />" + LS +
                "            <EXPRESSION value=\"l1_flags.LAND\" />" + LS +
                "            <COLOR red=\"0\" green=\"255\" blue=\"0\" alpha=\"255\" />" + LS +
                "            <TRANSPARENCY value=\"0.5\" />" + LS +
                "        </Bitmask_Definition>" + LS +
                "    </Bitmask_Definitions>" + LS +
                footer;
    }

    private String addMapGeocodingToProductAndGetExpected() {
        final double semiMinor = 1234.0;
        final double semiMajor = 5678.0;
        final double[] values = new double[]{semiMajor, semiMinor, 15, 16, 17, 18, 19}; // must be seven values
        final String projectionName = "ProjectionName";
        final String ellipsoidName = "EllipsoidName";
        final String datumName = "DatumName";
        final float pixelX = 3.2f;
        final float pixelY = 4.3f;
        final float easting = 5.4f;
        final float northing = 6.5f;
        final float orientation = 7.3f;
        final float pixelSizeX = 7.6f;
        final float pixelSizeY = 8.7f;
        final boolean orthorectified = true;
        final String elevModelName = "GETASSE30";
        final double noDataValue = 99999.99;
        final String typeId = LambertConformalConicDescriptor.TYPE_ID;
        final int sceneWidth = product.getSceneRasterWidth();
        final int sceneHeight = product.getSceneRasterHeight();
        final boolean sceneFitted = true;
        final Resampling resampling = Resampling.NEAREST_NEIGHBOUR;
        final String mapUnit = "mapUnit";

        final LambertConformalConicDescriptor descriptor = new LambertConformalConicDescriptor();
        final MapTransform transform = descriptor.createTransform(values);
        final MapProjection projection = new MapProjection(projectionName, transform, mapUnit);
        final Ellipsoid ellipsoid = new Ellipsoid(ellipsoidName, semiMinor, semiMajor);
        final Datum datum = new Datum(datumName, ellipsoid, 0, 0, 0);
        final MapInfo mapInfo = new MapInfo(projection, pixelX, pixelY, easting, northing, pixelSizeX, pixelSizeY,
                                            datum);
        mapInfo.setOrientation(orientation);
        mapInfo.setOrthorectified(orthorectified);
        mapInfo.setElevationModelName(elevModelName);
        mapInfo.setNoDataValue(noDataValue);
        mapInfo.setSceneWidth(sceneWidth);
        mapInfo.setSceneHeight(sceneHeight);
        mapInfo.setSceneSizeFitted(sceneFitted);
        mapInfo.setResampling(resampling);
        product.setGeoCoding(new MapGeoCoding(mapInfo));

        return header +
               "    <Coordinate_Reference_System>" + LS +
               "        <GEO_TABLES version=\"1.0\">CUSTOM</GEO_TABLES>" + LS +
               "        <Horizontal_CS>" + LS +
               "            <HORIZONTAL_CS_TYPE>PROJECTED</HORIZONTAL_CS_TYPE>" + LS +
               "            <HORIZONTAL_CS_NAME>" + projectionName + "</HORIZONTAL_CS_NAME>" + LS +
               "            <Geographic_CS>" + LS +
               "                <GEOGRAPHIC_CS_NAME>" + projectionName + "</GEOGRAPHIC_CS_NAME>" + LS +
               "                <Horizontal_Datum>" + LS +
               "                    <HORIZONTAL_DATUM_NAME>" + datumName + "</HORIZONTAL_DATUM_NAME>" + LS +
               "                    <Ellipsoid>" + LS +
               "                        <ELLIPSOID_NAME>" + ellipsoidName + "</ELLIPSOID_NAME>" + LS +
               "                        <Ellipsoid_Parameters>" + LS +
               "                            <ELLIPSOID_MAJ_AXIS unit=\"meter\">" + semiMajor + "</ELLIPSOID_MAJ_AXIS>" + LS +
               "                            <ELLIPSOID_MIN_AXIS unit=\"meter\">" + semiMinor + "</ELLIPSOID_MIN_AXIS>" + LS +
               "                        </Ellipsoid_Parameters>" + LS +
               "                    </Ellipsoid>" + LS +
               "                </Horizontal_Datum>" + LS +
               "            </Geographic_CS>" + LS +
               "            <Projection>" + LS +
               "                <NAME>" + projectionName + "</NAME>" + LS +
               "                <Projection_CT_Method>" + LS +
               "                    <PROJECTION_CT_NAME>" + typeId + "</PROJECTION_CT_NAME>" + LS +
               "                    <Projection_Parameters>" + LS +
               "                        <Projection_Parameter>" + LS +
               "                            <PROJECTION_PARAMETER_NAME>" + LambertConformalConicDescriptor.PARAMETER_NAMES[0] + "</PROJECTION_PARAMETER_NAME>" + LS +
               "                            <PROJECTION_PARAMETER_VALUE unit=\"" + LambertConformalConicDescriptor.PARAMETER_UNITS[0] + "\">" + values[0] + "</PROJECTION_PARAMETER_VALUE>" + LS +
               "                        </Projection_Parameter>" + LS +
               "                        <Projection_Parameter>" + LS +
               "                            <PROJECTION_PARAMETER_NAME>" + LambertConformalConicDescriptor.PARAMETER_NAMES[1] + "</PROJECTION_PARAMETER_NAME>" + LS +
               "                            <PROJECTION_PARAMETER_VALUE unit=\"" + LambertConformalConicDescriptor.PARAMETER_UNITS[1] + "\">" + values[1] + "</PROJECTION_PARAMETER_VALUE>" + LS +
               "                        </Projection_Parameter>" + LS +
               "                        <Projection_Parameter>" + LS +
               "                            <PROJECTION_PARAMETER_NAME>" + LambertConformalConicDescriptor.PARAMETER_NAMES[2] + "</PROJECTION_PARAMETER_NAME>" + LS +
               "                            <PROJECTION_PARAMETER_VALUE unit=\"" + LambertConformalConicDescriptor.PARAMETER_UNITS[2] + "\">" + values[2] + "</PROJECTION_PARAMETER_VALUE>" + LS +
               "                        </Projection_Parameter>" + LS +
               "                        <Projection_Parameter>" + LS +
               "                            <PROJECTION_PARAMETER_NAME>" + LambertConformalConicDescriptor.PARAMETER_NAMES[3] + "</PROJECTION_PARAMETER_NAME>" + LS +
               "                            <PROJECTION_PARAMETER_VALUE unit=\"" + LambertConformalConicDescriptor.PARAMETER_UNITS[3] + "\">" + values[3] + "</PROJECTION_PARAMETER_VALUE>" + LS +
               "                        </Projection_Parameter>" + LS +
               "                        <Projection_Parameter>" + LS +
               "                            <PROJECTION_PARAMETER_NAME>" + LambertConformalConicDescriptor.PARAMETER_NAMES[4] + "</PROJECTION_PARAMETER_NAME>" + LS +
               "                            <PROJECTION_PARAMETER_VALUE unit=\"" + LambertConformalConicDescriptor.PARAMETER_UNITS[4] + "\">" + values[4] + "</PROJECTION_PARAMETER_VALUE>" + LS +
               "                        </Projection_Parameter>" + LS +
               "                        <Projection_Parameter>" + LS +
               "                            <PROJECTION_PARAMETER_NAME>" + LambertConformalConicDescriptor.PARAMETER_NAMES[5] + "</PROJECTION_PARAMETER_NAME>" + LS +
               "                            <PROJECTION_PARAMETER_VALUE unit=\"" + LambertConformalConicDescriptor.PARAMETER_UNITS[5] + "\">" + values[5] + "</PROJECTION_PARAMETER_VALUE>" + LS +
               "                        </Projection_Parameter>" + LS +
               "                        <Projection_Parameter>" + LS +
               "                            <PROJECTION_PARAMETER_NAME>" + LambertConformalConicDescriptor.PARAMETER_NAMES[6] + "</PROJECTION_PARAMETER_NAME>" + LS +
               "                            <PROJECTION_PARAMETER_VALUE unit=\"" + LambertConformalConicDescriptor.PARAMETER_UNITS[6] + "\">" + values[6] + "</PROJECTION_PARAMETER_VALUE>" + LS +
               "                        </Projection_Parameter>" + LS +
               "                    </Projection_Parameters>" + LS +
               "                </Projection_CT_Method>" + LS +
               "            </Projection>" + LS +
               "            <MAP_INFO>" + LS +
               "                <PIXEL_X value=\"" + pixelX + "\" />" + LS +
               "                <PIXEL_Y value=\"" + pixelY + "\" />" + LS +
               "                <EASTING value=\"" + easting + "\" />" + LS +
               "                <NORTHING value=\"" + northing + "\" />" + LS +
               "                <ORIENTATION value=\"" + orientation + "\" />" + LS +
               "                <PIXELSIZE_X value=\"" + pixelSizeX + "\" />" + LS +
               "                <PIXELSIZE_Y value=\"" + pixelSizeY + "\" />" + LS +
               "                <NODATA_VALUE value=\"" + noDataValue + "\" />" + LS +
               "                <MAPUNIT value=\"" + mapUnit + "\" />" + LS +
               "                <ORTHORECTIFIED value=\"" + orthorectified + "\" />" + LS +
               "                <ELEVATION_MODEL value=\"" + elevModelName + "\" />" + LS +
               "                <SCENE_FITTED value=\"" + sceneFitted + "\" />" + LS +
               "                <SCENE_WIDTH value=\"" + sceneWidth + "\" />" + LS +
               "                <SCENE_HEIGHT value=\"" + sceneHeight + "\" />" + LS +
               "                <RESAMPLING value=\"" + resampling.getName() + "\" />" + LS +
               "            </MAP_INFO>" + LS +
               "        </Horizontal_CS>" + LS +
               "    </Coordinate_Reference_System>" + LS +
               rasterDimensions +
               footer;
    }

    private String setFXYGeoCodingAndGetExpected() {
        final String fxyGeoCoding = setFXYGeoCodingAndGetCore();
        return header +
               fxyGeoCoding + LS +
               rasterDimensions +
               footer;
    }

    private String setFXYGeoCodingAndGetCore() {
        final double[] xCoefficients = new double[]{0, 1, 2};
        final double[] yCoefficients = new double[]{3, 4, 5};
        final double[] lambdaCoefficients = new double[]{6, 7, 8};
        final double[] phiCoefficients = new double[]{9, 10, 11};
        final FXYSum xFunction = new FXYSum(FXYSum.FXY_LINEAR, 1, xCoefficients);
        final FXYSum yFunction = new FXYSum(FXYSum.FXY_LINEAR, 1, yCoefficients);
        final FXYSum lambdaFunction = new FXYSum(FXYSum.FXY_LINEAR, 1, lambdaCoefficients);
        final FXYSum phiFunction = new FXYSum(FXYSum.FXY_LINEAR, 1, phiCoefficients);
        final float pixelOffsetX = 0;
        final float pixelOffsetY = 0;
        final float pixelSizeX = 1;
        final float pixelSizeY = 1;
        final Datum datum = Datum.WGS_84;
        final FXYGeoCoding geoCoding = new FXYGeoCoding(pixelOffsetX, pixelOffsetY,
                                                        pixelSizeX, pixelSizeY,
                                                        xFunction, yFunction, phiFunction, lambdaFunction,
                                                        datum);
        product.setGeoCoding(geoCoding);
        final Ellipsoid ellipsoid = datum.getEllipsoid();
        return "    <Coordinate_Reference_System>" + LS +
               "        <Horizontal_CS>" + LS +
               "            <HORIZONTAL_CS_TYPE>GEOGRAPHIC</HORIZONTAL_CS_TYPE>" + LS +
               "            <Geographic_CS>" + LS +
               "                <Horizontal_Datum>" + LS +
               "                    <HORIZONTAL_DATUM_NAME>" + datum.getName() + "</HORIZONTAL_DATUM_NAME>" + LS +
               "                    <Ellipsoid>" + LS +
               "                        <ELLIPSOID_NAME>" + ellipsoid.getName() + "</ELLIPSOID_NAME>" + LS +
               "                        <Ellipsoid_Parameters>" + LS +
               "                            <ELLIPSOID_MAJ_AXIS unit=\"M\">" + ellipsoid.getSemiMajor() + "</ELLIPSOID_MAJ_AXIS>" + LS +
               "                            <ELLIPSOID_MIN_AXIS unit=\"M\">" + ellipsoid.getSemiMinor() + "</ELLIPSOID_MIN_AXIS>" + LS +
               "                        </Ellipsoid_Parameters>" + LS +
               "                    </Ellipsoid>" + LS +
               "                </Horizontal_Datum>" + LS +
               "            </Geographic_CS>" + LS +
               "        </Horizontal_CS>" + LS +
               "    </Coordinate_Reference_System>" + LS +
               "    <Geoposition>" + LS +
               "        <Geoposition_Insert>" + LS +
               "            <ULXMAP>" + pixelOffsetX + "</ULXMAP>" + LS +
               "            <ULYMAP>" + pixelOffsetY + "</ULYMAP>" + LS +
               "            <XDIM>" + pixelSizeX + "</XDIM>" + LS +
               "            <YDIM>" + pixelSizeY + "</YDIM>" + LS +
               "        </Geoposition_Insert>" + LS +
               "        <Simplified_Location_Model>" + LS +
               "            <Direct_Location_Model order=\"" + lambdaFunction.getOrder() + "\">" + LS +
               "                <lc_List>" + LS +
               "                    <lc index=\"0\">" + lambdaCoefficients[0] + "</lc>" + LS +
               "                    <lc index=\"1\">" + lambdaCoefficients[1] + "</lc>" + LS +
               "                    <lc index=\"2\">" + lambdaCoefficients[2] + "</lc>" + LS +
               "                </lc_List>" + LS +
               "                <pc_List>" + LS +
               "                    <pc index=\"0\">" + phiCoefficients[0] + "</pc>" + LS +
               "                    <pc index=\"1\">" + phiCoefficients[1] + "</pc>" + LS +
               "                    <pc index=\"2\">" + phiCoefficients[2] + "</pc>" + LS +
               "                </pc_List>" + LS +
               "            </Direct_Location_Model>" + LS +
               "            <Reverse_Location_Model order=\"" + xFunction.getOrder() + "\">" + LS + "                <ic_List>" + LS +
               "                    <ic index=\"0\">" + xCoefficients[0] + "</ic>" + LS +
               "                    <ic index=\"1\">" + xCoefficients[1] + "</ic>" + LS +
               "                    <ic index=\"2\">" + xCoefficients[2] + "</ic>" + LS +
               "                </ic_List>" + LS +
               "                <jc_List>" + LS +
               "                    <jc index=\"0\">" + yCoefficients[0] + "</jc>" + LS +
               "                    <jc index=\"1\">" + yCoefficients[1] + "</jc>" + LS +
               "                    <jc index=\"2\">" + yCoefficients[2] + "</jc>" + LS +
               "                </jc_List>" + LS +
               "            </Reverse_Location_Model>" + LS +
               "        </Simplified_Location_Model>" + LS +
               "    </Geoposition>";
    }

    private String setBandedFXYGeoCodingAndGetExpected() {
        final double[] xCoefficients1 = new double[]{0, 1, 2};
        final double[] yCoefficients1 = new double[]{3, 4, 5};
        final double[] lonCoefficients1 = new double[]{6, 7, 8};
        final double[] latCoefficients1 = new double[]{9, 10, 11};
        final FXYSum xFunction1 = new FXYSum(FXYSum.FXY_LINEAR, 1, xCoefficients1);
        final FXYSum yFunction1 = new FXYSum(FXYSum.FXY_LINEAR, 1, yCoefficients1);
        final FXYSum lambdaFunction1 = new FXYSum(FXYSum.FXY_LINEAR, 1, lonCoefficients1);
        final FXYSum phiFunction1 = new FXYSum(FXYSum.FXY_LINEAR, 1, latCoefficients1);
        final double[] xCoefficients2 = new double[]{12, 13, 14};
        final double[] yCoefficients2 = new double[]{15, 16, 17};
        final double[] lonCoefficients2 = new double[]{18, 19, 20};
        final double[] latCoefficients2 = new double[]{21, 22, 23};
        final FXYSum xFunction2 = new FXYSum(FXYSum.FXY_LINEAR, 1, xCoefficients2);
        final FXYSum yFunction2 = new FXYSum(FXYSum.FXY_LINEAR, 1, yCoefficients2);
        final FXYSum lambdaFunction2 = new FXYSum(FXYSum.FXY_LINEAR, 1, lonCoefficients2);
        final FXYSum phiFunction2 = new FXYSum(FXYSum.FXY_LINEAR, 1, latCoefficients2);
        final float pixelOffsetX = 0;
        final float pixelOffsetY = 0;
        final float pixelSizeX = 1;
        final float pixelSizeY = 1;
        final Datum datum = Datum.WGS_84;
        final FXYGeoCoding geoCoding1 = new FXYGeoCoding(pixelOffsetX, pixelOffsetY,
                                                         pixelSizeX, pixelSizeY,
                                                         xFunction1, yFunction1, phiFunction1, lambdaFunction1,
                                                         datum);
        final FXYGeoCoding geoCoding2 = new FXYGeoCoding(pixelOffsetX, pixelOffsetY,
                                                         pixelSizeX, pixelSizeY,
                                                         xFunction2, yFunction2, phiFunction2, lambdaFunction2,
                                                         datum);
        final Band band1 = product.addBand("b1", ProductData.TYPE_INT8);
        final Band band2 = product.addBand("b2", ProductData.TYPE_INT8);
        product.addBand(new VirtualBand("vb1", ProductData.TYPE_INT8, 200, 300, "b1 * 0.4 + 1"));
        product.addBand(new ConvolutionFilterBand("cfb1", band2,
                                                  new Kernel(3, 3, 1, new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9})));
        product.addBand(new GeneralFilterBand("gfb1", band2, 150, GeneralFilterBand.MEAN));


        band1.setGeoCoding(geoCoding1);
        band2.setGeoCoding(geoCoding2);
        return header +
               "    <Coordinate_Reference_System>" + LS +
               "        <Horizontal_CS>" + LS +
               "            <HORIZONTAL_CS_TYPE>GEOGRAPHIC</HORIZONTAL_CS_TYPE>" + LS +
               "            <Geographic_CS>" + LS +
               "                <Horizontal_Datum>" + LS +
               "                    <HORIZONTAL_DATUM_NAME>" + datum.getName() + "</HORIZONTAL_DATUM_NAME>" + LS +
               "                    <Ellipsoid>" + LS +
               "                        <ELLIPSOID_NAME>" + datum.getEllipsoid().getName() + "</ELLIPSOID_NAME>" + LS +
               "                        <Ellipsoid_Parameters>" + LS +
               "                            <ELLIPSOID_MAJ_AXIS unit=\"M\">" + datum.getEllipsoid().getSemiMajor() + "</ELLIPSOID_MAJ_AXIS>" + LS +
               "                            <ELLIPSOID_MIN_AXIS unit=\"M\">" + datum.getEllipsoid().getSemiMinor() + "</ELLIPSOID_MIN_AXIS>" + LS +
               "                        </Ellipsoid_Parameters>" + LS +
               "                    </Ellipsoid>" + LS +
               "                </Horizontal_Datum>" + LS +
               "            </Geographic_CS>" + LS +
               "        </Horizontal_CS>" + LS +
               "    </Coordinate_Reference_System>" + LS +
               "    <Geoposition>" + LS +
               "        <BAND_INDEX>0</BAND_INDEX>" + LS +
               "        <Geoposition_Insert>" + LS +
               "            <ULXMAP>" + pixelOffsetX + "</ULXMAP>" + LS +
               "            <ULYMAP>" + pixelOffsetY + "</ULYMAP>" + LS +
               "            <XDIM>" + pixelSizeX + "</XDIM>" + LS +
               "            <YDIM>" + pixelSizeY + "</YDIM>" + LS +
               "        </Geoposition_Insert>" + LS +
               "        <Simplified_Location_Model>" + LS +
               "            <Direct_Location_Model order=\"" + geoCoding1.getPixelXFunction().getOrder() + "\">" + LS +
               "                <lc_List>" + LS +
               "                    <lc index=\"0\">" + lonCoefficients1[0] + "</lc>" + LS +
               "                    <lc index=\"1\">" + lonCoefficients1[1] + "</lc>" + LS +
               "                    <lc index=\"2\">" + lonCoefficients1[2] + "</lc>" + LS +
               "                </lc_List>" + LS +
               "                <pc_List>" + LS +
               "                    <pc index=\"0\">" + latCoefficients1[0] + "</pc>" + LS +
               "                    <pc index=\"1\">" + latCoefficients1[1] + "</pc>" + LS +
               "                    <pc index=\"2\">" + latCoefficients1[2] + "</pc>" + LS +
               "                </pc_List>" + LS +
               "            </Direct_Location_Model>" + LS +
               "            <Reverse_Location_Model order=\"" + geoCoding1.getLatFunction().getOrder() + "\">" + LS +
               "                <ic_List>" + LS +
               "                    <ic index=\"0\">" + xCoefficients1[0] + "</ic>" + LS +
               "                    <ic index=\"1\">" + xCoefficients1[1] + "</ic>" + LS +
               "                    <ic index=\"2\">" + xCoefficients1[2] + "</ic>" + LS +
               "                </ic_List>" + LS +
               "                <jc_List>" + LS +
               "                    <jc index=\"0\">" + yCoefficients1[0] + "</jc>" + LS +
               "                    <jc index=\"1\">" + yCoefficients1[1] + "</jc>" + LS +
               "                    <jc index=\"2\">" + yCoefficients1[2] + "</jc>" + LS +
               "                </jc_List>" + LS +
               "            </Reverse_Location_Model>" + LS +
               "        </Simplified_Location_Model>" + LS +
               "    </Geoposition>" + LS +
               "    <Geoposition>" + LS +
               "        <BAND_INDEX>1</BAND_INDEX>" + LS +
               "        <Geoposition_Insert>" + LS +
               "            <ULXMAP>" + pixelOffsetX + "</ULXMAP>" + LS +
               "            <ULYMAP>" + pixelOffsetY + "</ULYMAP>" + LS +
               "            <XDIM>" + pixelSizeX + "</XDIM>" + LS +
               "            <YDIM>" + pixelSizeY + "</YDIM>" + LS +
               "        </Geoposition_Insert>" + LS +
               "        <Simplified_Location_Model>" + LS +
               "            <Direct_Location_Model order=\"" + geoCoding2.getPixelXFunction().getOrder() + "\">" + LS +
               "                <lc_List>" + LS +
               "                    <lc index=\"0\">" + lonCoefficients2[0] + "</lc>" + LS +
               "                    <lc index=\"1\">" + lonCoefficients2[1] + "</lc>" + LS +
               "                    <lc index=\"2\">" + lonCoefficients2[2] + "</lc>" + LS +
               "                </lc_List>" + LS +
               "                <pc_List>" + LS +
               "                    <pc index=\"0\">" + latCoefficients2[0] + "</pc>" + LS +
               "                    <pc index=\"1\">" + latCoefficients2[1] + "</pc>" + LS +
               "                    <pc index=\"2\">" + latCoefficients2[2] + "</pc>" + LS +
               "                </pc_List>" + LS +
               "            </Direct_Location_Model>" + LS +
               "            <Reverse_Location_Model order=\"" + geoCoding2.getLatFunction().getOrder() + "\">" + LS +
               "                <ic_List>" + LS +
               "                    <ic index=\"0\">" + xCoefficients2[0] + "</ic>" + LS +
               "                    <ic index=\"1\">" + xCoefficients2[1] + "</ic>" + LS +
               "                    <ic index=\"2\">" + xCoefficients2[2] + "</ic>" + LS +
               "                </ic_List>" + LS +
               "                <jc_List>" + LS +
               "                    <jc index=\"0\">" + yCoefficients2[0] + "</jc>" + LS +
               "                    <jc index=\"1\">" + yCoefficients2[1] + "</jc>" + LS +
               "                    <jc index=\"2\">" + yCoefficients2[2] + "</jc>" + LS +
               "                </jc_List>" + LS +
               "            </Reverse_Location_Model>" + LS +
               "        </Simplified_Location_Model>" + LS +
               "    </Geoposition>" + LS +
               "    <Geoposition>" + LS +
               "        <BAND_INDEX>2</BAND_INDEX>" + LS +
               "        <Geoposition_Insert>" + LS +
               "            <ULXMAP>" + pixelOffsetX + "</ULXMAP>" + LS +
               "            <ULYMAP>" + pixelOffsetY + "</ULYMAP>" + LS +
               "            <XDIM>" + pixelSizeX + "</XDIM>" + LS +
               "            <YDIM>" + pixelSizeY + "</YDIM>" + LS +
               "        </Geoposition_Insert>" + LS +
               "        <Simplified_Location_Model>" + LS +
               "            <Direct_Location_Model order=\"" + geoCoding2.getPixelXFunction().getOrder() + "\">" + LS +
               "                <lc_List>" + LS +
               "                    <lc index=\"0\">" + lonCoefficients1[0] + "</lc>" + LS +
               "                    <lc index=\"1\">" + lonCoefficients1[1] + "</lc>" + LS +
               "                    <lc index=\"2\">" + lonCoefficients1[2] + "</lc>" + LS +
               "                </lc_List>" + LS +
               "                <pc_List>" + LS +
               "                    <pc index=\"0\">" + latCoefficients1[0] + "</pc>" + LS +
               "                    <pc index=\"1\">" + latCoefficients1[1] + "</pc>" + LS +
               "                    <pc index=\"2\">" + latCoefficients1[2] + "</pc>" + LS +
               "                </pc_List>" + LS +
               "            </Direct_Location_Model>" + LS +
               "            <Reverse_Location_Model order=\"" + geoCoding1.getLatFunction().getOrder() + "\">" + LS +
               "                <ic_List>" + LS +
               "                    <ic index=\"0\">" + xCoefficients1[0] + "</ic>" + LS +
               "                    <ic index=\"1\">" + xCoefficients1[1] + "</ic>" + LS +
               "                    <ic index=\"2\">" + xCoefficients1[2] + "</ic>" + LS +
               "                </ic_List>" + LS +
               "                <jc_List>" + LS +
               "                    <jc index=\"0\">" + yCoefficients1[0] + "</jc>" + LS +
               "                    <jc index=\"1\">" + yCoefficients1[1] + "</jc>" + LS +
               "                    <jc index=\"2\">" + yCoefficients1[2] + "</jc>" + LS +
               "                </jc_List>" + LS +
               "            </Reverse_Location_Model>" + LS +
               "        </Simplified_Location_Model>" + LS +
               "    </Geoposition>" + LS +
               "    <Geoposition>" + LS +
               "        <BAND_INDEX>3</BAND_INDEX>" + LS +
               "        <Geoposition_Insert>" + LS +
               "            <ULXMAP>" + pixelOffsetX + "</ULXMAP>" + LS +
               "            <ULYMAP>" + pixelOffsetY + "</ULYMAP>" + LS +
               "            <XDIM>" + pixelSizeX + "</XDIM>" + LS +
               "            <YDIM>" + pixelSizeY + "</YDIM>" + LS +
               "        </Geoposition_Insert>" + LS +
               "        <Simplified_Location_Model>" + LS +
               "            <Direct_Location_Model order=\"" + geoCoding1.getPixelXFunction().getOrder() + "\">" + LS +
               "                <lc_List>" + LS +
               "                    <lc index=\"0\">" + lonCoefficients1[0] + "</lc>" + LS +
               "                    <lc index=\"1\">" + lonCoefficients1[1] + "</lc>" + LS +
               "                    <lc index=\"2\">" + lonCoefficients1[2] + "</lc>" + LS +
               "                </lc_List>" + LS +
               "                <pc_List>" + LS +
               "                    <pc index=\"0\">" + latCoefficients1[0] + "</pc>" + LS +
               "                    <pc index=\"1\">" + latCoefficients1[1] + "</pc>" + LS +
               "                    <pc index=\"2\">" + latCoefficients1[2] + "</pc>" + LS +
               "                </pc_List>" + LS +
               "            </Direct_Location_Model>" + LS +
               "            <Reverse_Location_Model order=\"" + geoCoding2.getLatFunction().getOrder() + "\">" + LS +
               "                <ic_List>" + LS +
               "                    <ic index=\"0\">" + xCoefficients1[0] + "</ic>" + LS +
               "                    <ic index=\"1\">" + xCoefficients1[1] + "</ic>" + LS +
               "                    <ic index=\"2\">" + xCoefficients1[2] + "</ic>" + LS +
               "                </ic_List>" + LS +
               "                <jc_List>" + LS +
               "                    <jc index=\"0\">" + yCoefficients1[0] + "</jc>" + LS +
               "                    <jc index=\"1\">" + yCoefficients1[1] + "</jc>" + LS +
               "                    <jc index=\"2\">" + yCoefficients1[2] + "</jc>" + LS +
               "                </jc_List>" + LS +
               "            </Reverse_Location_Model>" + LS +
               "        </Simplified_Location_Model>" + LS +
               "    </Geoposition>" + LS +
               "    <Geoposition>" + LS +
               "        <BAND_INDEX>4</BAND_INDEX>" + LS +
               "        <Geoposition_Insert>" + LS +
               "            <ULXMAP>" + pixelOffsetX + "</ULXMAP>" + LS +
               "            <ULYMAP>" + pixelOffsetY + "</ULYMAP>" + LS +
               "            <XDIM>" + pixelSizeX + "</XDIM>" + LS +
               "            <YDIM>" + pixelSizeY + "</YDIM>" + LS +
               "        </Geoposition_Insert>" + LS +
               "        <Simplified_Location_Model>" + LS +
               "            <Direct_Location_Model order=\"" + geoCoding1.getPixelXFunction().getOrder() + "\">" + LS +
               "                <lc_List>" + LS +
               "                    <lc index=\"0\">" + lonCoefficients1[0] + "</lc>" + LS +
               "                    <lc index=\"1\">" + lonCoefficients1[1] + "</lc>" + LS +
               "                    <lc index=\"2\">" + lonCoefficients1[2] + "</lc>" + LS +
               "                </lc_List>" + LS +
               "                <pc_List>" + LS +
               "                    <pc index=\"0\">" + latCoefficients1[0] + "</pc>" + LS +
               "                    <pc index=\"1\">" + latCoefficients1[1] + "</pc>" + LS +
               "                    <pc index=\"2\">" + latCoefficients1[2] + "</pc>" + LS +
               "                </pc_List>" + LS +
               "            </Direct_Location_Model>" + LS +
               "            <Reverse_Location_Model order=\"" + geoCoding1.getLatFunction().getOrder() + "\">" + LS +
               "                <ic_List>" + LS +
               "                    <ic index=\"0\">" + xCoefficients1[0] + "</ic>" + LS +
               "                    <ic index=\"1\">" + xCoefficients1[1] + "</ic>" + LS +
               "                    <ic index=\"2\">" + xCoefficients1[2] + "</ic>" + LS +
               "                </ic_List>" + LS +
               "                <jc_List>" + LS +
               "                    <jc index=\"0\">" + yCoefficients1[0] + "</jc>" + LS +
               "                    <jc index=\"1\">" + yCoefficients1[1] + "</jc>" + LS +
               "                    <jc index=\"2\">" + yCoefficients1[2] + "</jc>" + LS +
               "                </jc_List>" + LS +
               "            </Reverse_Location_Model>" + LS +
               "        </Simplified_Location_Model>" + LS +
               "    </Geoposition>" + LS +
               "    <Raster_Dimensions>" + LS +
               "        <NCOLS>200</NCOLS>" + LS +
               "        <NROWS>300</NROWS>" + LS +
               "        <NBANDS>5</NBANDS>" + LS +
               "    </Raster_Dimensions>" + LS +
               dataAccess +
               footer;
    }

    private String setPixelGeoCodingAndGetExpected() throws IOException {
        final Band b1 = product.addBand("b1", ProductData.TYPE_INT8);
        final Band b2 = product.addBand("b2", ProductData.TYPE_INT8);
        final byte[] bandData = new byte[product.getSceneRasterWidth() * product.getSceneRasterHeight()];
        b1.setDataElems(bandData);
        b2.setDataElems(bandData);

        final String pixelPosEstimator = setFXYGeoCodingAndGetCore().replace(LS, LS + "        ");
        final PixelGeoCoding pixelGeoCoding = new PixelGeoCoding(b1, b2, "NOT NaN", 4);
        product.setGeoCoding(pixelGeoCoding);
        return header +
               "    <Geoposition>" + LS +
               "        <LATITUDE_BAND>" + pixelGeoCoding.getLatBand().getName() + "</LATITUDE_BAND>" + LS +
               "        <LONGITUDE_BAND>" + pixelGeoCoding.getLonBand().getName() + "</LONGITUDE_BAND>" + LS +
               "        <VALID_MASK_EXPRESSION>" + pixelGeoCoding.getValidMask() + "</VALID_MASK_EXPRESSION>" + LS +
               "        <SEARCH_RADIUS>" + pixelGeoCoding.getSearchRadius() + "</SEARCH_RADIUS>" + LS +
               "        <Pixel_Position_Estimator>" + LS +
               "        " + pixelPosEstimator + LS +
               "        </Pixel_Position_Estimator>" + LS +
               "    </Geoposition>" + LS +
               "    <Raster_Dimensions>" + LS +
               "        <NCOLS>200</NCOLS>" + LS +
               "        <NROWS>300</NROWS>" + LS +
               "        <NBANDS>2</NBANDS>" + LS +
               "    </Raster_Dimensions>" + LS +
               "    <Data_Access>" + LS +
               "        <DATA_FILE_FORMAT>ENVI</DATA_FILE_FORMAT>" + LS +
               "        <DATA_FILE_FORMAT_DESC>ENVI File Format</DATA_FILE_FORMAT_DESC>" + LS +
               "        <DATA_FILE_ORGANISATION>BAND_SEPARATE</DATA_FILE_ORGANISATION>" + LS +
               "        <Data_File>" + LS +
               "            <DATA_FILE_PATH href=\"test.data/b1.hdr\" />" + LS +
               "            <BAND_INDEX>0</BAND_INDEX>" + LS +
               "        </Data_File>" + LS +
               "        <Data_File>" + LS +
               "            <DATA_FILE_PATH href=\"test.data/b2.hdr\" />" + LS +
               "            <BAND_INDEX>1</BAND_INDEX>" + LS +
               "        </Data_File>" + LS +
               "    </Data_Access>" + LS +
               "    <Image_Interpretation>" + LS +
               "        <Spectral_Band_Info>" + LS +
               "            <BAND_INDEX>0</BAND_INDEX>" + LS +
               "            <BAND_DESCRIPTION />" + LS +
               "            <BAND_NAME>b1</BAND_NAME>" + LS +
               "            <DATA_TYPE>int8</DATA_TYPE>" + LS +
               "            <SOLAR_FLUX>0.0</SOLAR_FLUX>" + LS +
               "            <BAND_WAVELEN>0.0</BAND_WAVELEN>" + LS +
               "            <BANDWIDTH>0.0</BANDWIDTH>" + LS +
               "            <SCALING_FACTOR>1.0</SCALING_FACTOR>" + LS +
               "            <SCALING_OFFSET>0.0</SCALING_OFFSET>" + LS +
               "            <LOG10_SCALED>false</LOG10_SCALED>" + LS +
               "            <NO_DATA_VALUE_USED>false</NO_DATA_VALUE_USED>" + LS +
               "            <NO_DATA_VALUE>0.0</NO_DATA_VALUE>" + LS +
               "        </Spectral_Band_Info>" + LS +
               "        <Spectral_Band_Info>" + LS +
               "            <BAND_INDEX>1</BAND_INDEX>" + LS +
               "            <BAND_DESCRIPTION />" + LS +
               "            <BAND_NAME>b2</BAND_NAME>" + LS +
               "            <DATA_TYPE>int8</DATA_TYPE>" + LS +
               "            <SOLAR_FLUX>0.0</SOLAR_FLUX>" + LS +
               "            <BAND_WAVELEN>0.0</BAND_WAVELEN>" + LS +
               "            <BANDWIDTH>0.0</BANDWIDTH>" + LS +
               "            <SCALING_FACTOR>1.0</SCALING_FACTOR>" + LS +
               "            <SCALING_OFFSET>0.0</SCALING_OFFSET>" + LS +
               "            <LOG10_SCALED>false</LOG10_SCALED>" + LS +
               "            <NO_DATA_VALUE_USED>false</NO_DATA_VALUE_USED>" + LS +
               "            <NO_DATA_VALUE>0.0</NO_DATA_VALUE>" + LS +
               "        </Spectral_Band_Info>" + LS +
               "    </Image_Interpretation>" + LS +
               footer;
    }

    private String setPixelGeoCodingWithoutEstimatorAndGetExpected() throws IOException {
        final Band b1 = product.addBand("b1", ProductData.TYPE_INT8);
        final Band b2 = product.addBand("b2", ProductData.TYPE_INT8);
        final byte[] bandData = new byte[product.getSceneRasterWidth() * product.getSceneRasterHeight()];
        b1.setDataElems(bandData);
        b2.setDataElems(bandData);

        final PixelGeoCoding pixelGeoCoding = new PixelGeoCoding(b1, b2, null, 4, ProgressMonitor.NULL);
        product.setGeoCoding(pixelGeoCoding);
        return header +
               "    <Geoposition>" + LS +
               "        <LATITUDE_BAND>" + pixelGeoCoding.getLatBand().getName() + "</LATITUDE_BAND>" + LS +
               "        <LONGITUDE_BAND>" + pixelGeoCoding.getLonBand().getName() + "</LONGITUDE_BAND>" + LS +
               "        <SEARCH_RADIUS>" + pixelGeoCoding.getSearchRadius() + "</SEARCH_RADIUS>" + LS +
               "    </Geoposition>" + LS +
               "    <Raster_Dimensions>" + LS +
               "        <NCOLS>200</NCOLS>" + LS +
               "        <NROWS>300</NROWS>" + LS +
               "        <NBANDS>2</NBANDS>" + LS +
               "    </Raster_Dimensions>" + LS +
               "    <Data_Access>" + LS +
               "        <DATA_FILE_FORMAT>ENVI</DATA_FILE_FORMAT>" + LS +
               "        <DATA_FILE_FORMAT_DESC>ENVI File Format</DATA_FILE_FORMAT_DESC>" + LS +
               "        <DATA_FILE_ORGANISATION>BAND_SEPARATE</DATA_FILE_ORGANISATION>" + LS +
               "        <Data_File>" + LS +
               "            <DATA_FILE_PATH href=\"test.data/b1.hdr\" />" + LS +
               "            <BAND_INDEX>0</BAND_INDEX>" + LS +
               "        </Data_File>" + LS +
               "        <Data_File>" + LS +
               "            <DATA_FILE_PATH href=\"test.data/b2.hdr\" />" + LS +
               "            <BAND_INDEX>1</BAND_INDEX>" + LS +
               "        </Data_File>" + LS +
               "    </Data_Access>" + LS +
               "    <Image_Interpretation>" + LS +
               "        <Spectral_Band_Info>" + LS +
               "            <BAND_INDEX>0</BAND_INDEX>" + LS +
               "            <BAND_DESCRIPTION />" + LS +
               "            <BAND_NAME>b1</BAND_NAME>" + LS +
               "            <DATA_TYPE>int8</DATA_TYPE>" + LS +
               "            <SOLAR_FLUX>0.0</SOLAR_FLUX>" + LS +
               "            <BAND_WAVELEN>0.0</BAND_WAVELEN>" + LS +
               "            <BANDWIDTH>0.0</BANDWIDTH>" + LS +
               "            <SCALING_FACTOR>1.0</SCALING_FACTOR>" + LS +
               "            <SCALING_OFFSET>0.0</SCALING_OFFSET>" + LS +
               "            <LOG10_SCALED>false</LOG10_SCALED>" + LS +
               "            <NO_DATA_VALUE_USED>false</NO_DATA_VALUE_USED>" + LS +
               "            <NO_DATA_VALUE>0.0</NO_DATA_VALUE>" + LS +
               "        </Spectral_Band_Info>" + LS +
               "        <Spectral_Band_Info>" + LS +
               "            <BAND_INDEX>1</BAND_INDEX>" + LS +
               "            <BAND_DESCRIPTION />" + LS +
               "            <BAND_NAME>b2</BAND_NAME>" + LS +
               "            <DATA_TYPE>int8</DATA_TYPE>" + LS +
               "            <SOLAR_FLUX>0.0</SOLAR_FLUX>" + LS +
               "            <BAND_WAVELEN>0.0</BAND_WAVELEN>" + LS +
               "            <BANDWIDTH>0.0</BANDWIDTH>" + LS +
               "            <SCALING_FACTOR>1.0</SCALING_FACTOR>" + LS +
               "            <SCALING_OFFSET>0.0</SCALING_OFFSET>" + LS +
               "            <LOG10_SCALED>false</LOG10_SCALED>" + LS +
               "            <NO_DATA_VALUE_USED>false</NO_DATA_VALUE_USED>" + LS +
               "            <NO_DATA_VALUE>0.0</NO_DATA_VALUE>" + LS +
               "        </Spectral_Band_Info>" + LS +
               "    </Image_Interpretation>" + LS +
               footer;
    }

}
