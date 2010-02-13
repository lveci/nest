package org.esa.beam.framework.datamodel;

import javax.media.jai.PixelAccessor;
import java.awt.image.Raster;
import java.awt.Rectangle;

/**
 * todo - add API doc
*
* @author Marco Peters
* @version $Revision: 1.2 $ $Date: 2010-02-12 22:18:10 $
* @since BEAM 4.5
*/
interface StxOp {
    String getName();

    void accumulateDataUByte(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor, Raster maskTile, Rectangle r, String unit);

    void accumulateDataShort(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor, Raster maskTile, Rectangle r, String unit);

    void accumulateDataUShort(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor, Raster maskTile, Rectangle r, String unit);

    void accumulateDataInt(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor, Raster maskTile, Rectangle r, String unit);

    void accumulateDataFloat(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor, Raster maskTile, Rectangle r, String unit);

    void accumulateDataDouble(PixelAccessor dataAccessor, Raster dataTile, PixelAccessor maskAccessor, Raster maskTile, Rectangle r, String unit);

}
