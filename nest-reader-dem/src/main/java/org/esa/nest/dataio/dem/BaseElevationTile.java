/*
 * Copyright (C) 2012 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.dataio.dem;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.dataop.dem.ElevationModel;

public class BaseElevationTile implements ElevationTile {

    private Product product;
    protected final float noDataValue;
    private LineArray.LineFactory objectFactory;
    private LineArray objectArray;
    //private int minIndex, maxIndex;

    public BaseElevationTile(final ElevationModel dem, final Product product) {
        this.product = product;
        noDataValue = dem.getDescriptor().getNoDataValue();
        objectFactory = getLineFactory();
        setCachedRange(0, product.getSceneRasterHeight());

        //System.out.println("Dem Tile "+product.getName());
    }

    private void setCachedRange(final int indexMin, final int indexMax) {
        if (indexMax < indexMin) {
            throw new IllegalArgumentException("indexMin < indexMax");
        }
        final LineArray objArray = new LineArray(indexMin, indexMax);
        final LineArray objArrayOld = objectArray;
        if (objArrayOld != null) {
            objArray.set(objArrayOld);
            objArrayOld.clear();
        }
        objectArray = objArray;
        //minIndex = objArray.getMinIndex();
        //maxIndex = objArray.getMaxIndex();
    }

    public void clearCache() {
        if (objectArray != null) {
            objectArray.clear();
        }
    }

    public final float getSample(final int pixelX, final int pixelY) throws Exception {

        //if (pixelY < minIndex || pixelY > maxIndex) {
        //    final float[] line = objectFactory.createObject(pixelY);
        //    return line[pixelX];
        //}
        float[] line = objectArray.getObject(pixelY);
        if (line == null) {
            line = objectFactory.createObject(pixelY);
            objectArray.setObject(pixelY, line);
        }
        return line[pixelX];
    }

    public void dispose() {
        clearCache();
        if (product != null) {
            product.dispose();
            product = null;
        }
    }

    private LineArray.LineFactory getLineFactory() {
        final Band band = product.getBandAt(0);
        final int width = product.getSceneRasterWidth();
        return new LineArray.LineFactory() {
            public float[] createObject(final int index) throws Exception {
                final float[] line = band.readPixels(0, index, width, 1, new float[width], ProgressMonitor.NULL);
                addGravitationalModel(index, line);
                return line;
            }
        };
    }

    protected void addGravitationalModel(final int index, final float[] line) {
    }
}