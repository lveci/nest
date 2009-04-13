package org.esa.nest.dat.toolviews.nestwwview;

import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.Polyline;
import org.esa.beam.framework.datamodel.*;
import org.esa.nest.util.GeoUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**

 */
public class ProductLayer extends RenderableLayer {
    private Product selectedProduct = null;
    private final static double million = 1000000.0;
    private final ConcurrentHashMap<String, Polyline[]> outlineTable = new ConcurrentHashMap<String, Polyline[]>();

    public void setSelectedProduct(Product product) {
        selectedProduct = product;
        if(selectedProduct != null) {
            for(String name : outlineTable.keySet()) {
                final Polyline[] lineList = outlineTable.get(name);
                final boolean highlight = name.equals(selectedProduct.getName());
                for(Polyline line : lineList) {
                    line.setHighlighted(highlight);
                }
            }
        }
    }

    public Product getSelectedProduct() {
        return selectedProduct;
    }

    public void addProduct(final Product product) throws IOException {
        final String name = product.getName();
        if(this.outlineTable.get(name) != null)
            return;

        final GeoCoding geoCoding = product.getGeoCoding();
        if (geoCoding == null) {
            final String productType = product.getProductType();
            if(productType.equals("ASA_WVW_2P") || productType.equals("ASA_WVS_1P") || productType.equals("ASA_WVI_1P")) {
                addWaveProduct(product);
            }
        } else {

            final GeoPos geoPos1 = product.getGeoCoding().getGeoPos(new PixelPos(0, 0), null);
            final GeoPos geoPos2 = product.getGeoCoding().getGeoPos(new PixelPos(product.getSceneRasterWidth(), 0), null);
            final GeoPos geoPos3 = product.getGeoCoding().getGeoPos(new PixelPos(product.getSceneRasterWidth(),
                    product.getSceneRasterHeight()), null);
            final GeoPos geoPos4 = product.getGeoCoding().getGeoPos(new PixelPos(0, product.getSceneRasterHeight()), null);

            final ArrayList<Position> positions = new ArrayList<Position>(4);
            positions.add(new Position(Angle.fromDegreesLatitude(geoPos1.getLat()),
                                       Angle.fromDegreesLongitude(geoPos1.getLon()), 0.0));
            positions.add(new Position(Angle.fromDegreesLatitude(geoPos2.getLat()),
                                       Angle.fromDegreesLongitude(geoPos2.getLon()), 0.0));
            positions.add(new Position(Angle.fromDegreesLatitude(geoPos3.getLat()),
                                       Angle.fromDegreesLongitude(geoPos3.getLon()), 0.0));
            positions.add(new Position(Angle.fromDegreesLatitude(geoPos4.getLat()),
                                       Angle.fromDegreesLongitude(geoPos4.getLon()), 0.0));
            positions.add(new Position(Angle.fromDegreesLatitude(geoPos1.getLat()),
                                       Angle.fromDegreesLongitude(geoPos1.getLon()), 0.0));

            final Polyline line = new Polyline();
            line.setFollowTerrain(true);
            line.setPositions(positions);

            addRenderable(line);
            outlineTable.put(name, new Polyline[] { line });
        }
    }

    private void addWaveProduct(final Product product) {
        final MetadataElement root = product.getMetadataRoot();
        final MetadataElement ggADS = root.getElement("GEOLOCATION_GRID_ADS");
        if(ggADS == null) return;

        final MetadataElement[] geoElemList = ggADS.getElements();
        final Polyline[] lineList = new Polyline[geoElemList.length];
        int cnt=0;
        for(MetadataElement geoElem : geoElemList) {
            final double lat = geoElem.getAttributeDouble("center_lat", 0.0) / million;
            final double lon = geoElem.getAttributeDouble("center_long", 0.0) / million;
            final double heading = geoElem.getAttributeDouble("heading", 0.0);

            final GeoUtils.LatLonHeading r1 = GeoUtils.vincenty_direct(lon, lat, 5000, heading);
            final GeoUtils.LatLonHeading corner1 = GeoUtils.vincenty_direct(r1.lon, r1.lat, 2500, heading-90.0);
            final GeoUtils.LatLonHeading corner2 = GeoUtils.vincenty_direct(r1.lon, r1.lat, 2500, heading+90.0);

            final GeoUtils.LatLonHeading r2 = GeoUtils.vincenty_direct(lon, lat, 5000, heading+180.0);
            final GeoUtils.LatLonHeading corner3 = GeoUtils.vincenty_direct(r2.lon, r2.lat, 2500, heading-90.0);
            final GeoUtils.LatLonHeading corner4 = GeoUtils.vincenty_direct(r2.lon, r2.lat, 2500, heading+90.0);

            final ArrayList<Position> positions = new ArrayList<Position>(4);
            positions.add(new Position(Angle.fromDegreesLatitude(corner1.lat), Angle.fromDegreesLongitude(corner1.lon), 0.0));
            positions.add(new Position(Angle.fromDegreesLatitude(corner2.lat), Angle.fromDegreesLongitude(corner2.lon), 0.0));
            positions.add(new Position(Angle.fromDegreesLatitude(corner4.lat), Angle.fromDegreesLongitude(corner4.lon), 0.0));
            positions.add(new Position(Angle.fromDegreesLatitude(corner3.lat), Angle.fromDegreesLongitude(corner3.lon), 0.0));
            positions.add(new Position(Angle.fromDegreesLatitude(corner1.lat), Angle.fromDegreesLongitude(corner1.lon), 0.0));
            
            final Polyline line = new Polyline();
            line.setFollowTerrain(true);
            line.setPositions(positions);

            addRenderable(line);
            lineList[cnt++] = line;
        }
        outlineTable.put(product.getName(), lineList);
    }

    public void removeProduct(final Product product) {
        removeOutline(product.getName());
    }

    private void removeOutline(String imagePath) {
        final Polyline[] lineList = this.outlineTable.get(imagePath);
        if (lineList != null) {
            for(Polyline line : lineList) {
                this.removeRenderable(line);
            }
            this.outlineTable.remove(imagePath);
        }
    }
}