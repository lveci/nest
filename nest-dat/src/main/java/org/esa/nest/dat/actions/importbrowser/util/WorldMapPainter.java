
package org.esa.nest.dat.actions.importbrowser.util;

import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.Guardian;
import org.esa.beam.util.ProductUtils;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;

public class WorldMapPainter {

    private final static  Color _fillColor = new Color(255, 200, 200, 70);
    private Image _worldMapImage;
    private int _width;
    private int _height;
    private GeoCoding _geoCoding;

    public WorldMapPainter(final Image worldMapImage) {
        setWorldMapImage(worldMapImage);
    }

    public WorldMapPainter() {

    }

    public final void setWorldMapImage(final Image worldMapImage) {
        Guardian.assertNotNull("worldMapImage", worldMapImage);
        _worldMapImage = worldMapImage;
        _width = worldMapImage.getWidth(null);
        _height = worldMapImage.getHeight(null);
        if (_width != _height * 2) {
            throw new IllegalArgumentException("The world map image mut have the aspect ratio of 'width = 2 * height'");
        }
        _geoCoding = createGeocoding();
    }

    public BufferedImage createWorldMapImage(final GeneralPath[] geoBoundaryPaths) {
        final BufferedImage image = new BufferedImage(_width, _height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.drawImage(_worldMapImage, 0, 0, null);

        if (geoBoundaryPaths != null) {
            for (GeneralPath geoBoundaryPath : geoBoundaryPaths) {
                final GeneralPath boundaryPath = ProductUtils.convertToPixelPath(geoBoundaryPath, _geoCoding);
                g2d.setColor(_fillColor);
                g2d.fill(boundaryPath);
                g2d.setColor(Color.red);
                g2d.draw(boundaryPath);
            }
        }
        return image;
    }

    public GeoCoding getGeoCoding() {
        return _geoCoding;
    }

    private GeoCoding createGeocoding() {
        return new GeoCoding() {
            @Override
            public AffineTransform getImageToModelTransform() {
                return null;
            }

            @Override
            public boolean isCrossingMeridianAt180() {
                return false;
            }


            @Override
            public CoordinateReferenceSystem getImageCRS() {
                return null;
            }

            @Override
            public boolean canGetPixelPos() {
                return true;
            }

            @Override
            public boolean canGetGeoPos() {
                return false;
            }

            @Override
            public PixelPos getPixelPos(final GeoPos geoPos, PixelPos pixelPos) {
                if (pixelPos == null) {
                    pixelPos = new PixelPos();
                }
                pixelPos.setLocation(_width / 360.0f * (geoPos.getLon() + 180.0f),
                                     _height - (_height / 180.0f * (geoPos.getLat() + 90.0f)));
                return pixelPos;
            }

            @Override
            public GeoPos getGeoPos(final PixelPos pixelPos, final GeoPos geoPos) {
                return null;
            }

            @Override
            public Datum getDatum() {
                return Datum.WGS_84;
            }

            @Override
            public void dispose() {
            }

            @Override
            public CoordinateReferenceSystem getBaseCRS() {
                return null;
            }

            @Override
            public CoordinateReferenceSystem getModelCRS() {
                return null;
            }
        };
    }
}
