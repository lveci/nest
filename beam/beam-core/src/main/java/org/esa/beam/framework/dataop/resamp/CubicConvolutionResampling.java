/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.beam.framework.dataop.resamp;


import javax.vecmath.Color4b;

final class CubicConvolutionResampling implements Resampling {

    public String getName() {
        return "CUBIC_CONVOLUTION";
    }

    public final Index createIndex() {
        return new Index(4, 1);
    }

    public final void computeIndex(final float x,
                                   final float y,
                                   final int width,
                                   final int height,
                                   final Index index) {
        index.x = x;
        index.y = y;
        index.width = width;
        index.height = height;

        final int i0 = (int) Math.floor(x);
        final int j0 = (int) Math.floor(y);

        index.i0 = i0;
        index.j0 = j0;
        /*
        float di = x - i0;
        float dj = y - j0;

        final int iMax = width - 1;
        final int jMax = height - 1;

        index.i[0] = Index.crop(i0 - 1, iMax);
        index.i[1] = Index.crop(i0, iMax);
        index.i[2] = Index.crop(i0 + 1, iMax);
        index.i[3] = Index.crop(i0 + 2, iMax);

        index.j[0] = Index.crop(j0 - 1, jMax);
        index.j[1] = Index.crop(j0, jMax);
        index.j[2] = Index.crop(j0 + 1, jMax);
        index.j[3] = Index.crop(j0 + 2, jMax);

        index.ki[0] = di;
        index.kj[0] = dj;
        */

        final float di = x - (i0 + 0.5f);
        final float dj = y - (j0 + 0.5f);

        final int iMax = width - 1;
        if (di >= 0) {
            index.i[0] = Index.crop(i0 - 1, iMax);
            index.i[1] = Index.crop(i0, iMax);
            index.i[2] = Index.crop(i0 + 1, iMax);
            index.i[3] = Index.crop(i0 + 2, iMax);
            index.ki[0] = di;
        } else {
            index.i[0] = Index.crop(i0 - 2, iMax);
            index.i[1] = Index.crop(i0 - 1, iMax);
            index.i[2] = Index.crop(i0, iMax);
            index.i[3] = Index.crop(i0 + 1, iMax);
            index.ki[0] = di + 1;
        }

        final int jMax = height - 1;
        if (dj >= 0) {
            index.j[0] = Index.crop(j0 - 1, jMax);
            index.j[1] = Index.crop(j0, jMax);
            index.j[2] = Index.crop(j0 + 1, jMax);
            index.j[3] = Index.crop(j0 + 2, jMax);
            index.kj[0] = dj;
        } else {
            index.j[0] = Index.crop(j0 - 2, jMax);
            index.j[1] = Index.crop(j0 - 1, jMax);
            index.j[2] = Index.crop(j0, jMax);
            index.j[3] = Index.crop(j0 + 1, jMax);
            index.kj[0] = dj + 1;
        }
    }

    public final float resample(final Raster raster,
                                final Index index) throws Exception {

        final double[][] v = new double[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                v[j][i] = raster.getSample(index.i[i], index.j[j]);
                if(Double.isNaN(v[j][i])) {
                    return raster.getSample(index.i0, index.j0);
                }
            }
        }

        final double muX = index.ki[0];
        final double muY = index.kj[0];

        final double tmpV0 = interpolationCubic(v[0][0], v[0][1], v[0][2], v[0][3], muX);
        final double tmpV1 = interpolationCubic(v[1][0], v[1][1], v[1][2], v[1][3], muX);
        final double tmpV2 = interpolationCubic(v[2][0], v[2][1], v[2][2], v[2][3], muX);
        final double tmpV3 = interpolationCubic(v[3][0], v[3][1], v[3][2], v[3][3], muX);
        return (float)interpolationCubic(tmpV0, tmpV1, tmpV2, tmpV3, muY);
    }

    private static double interpolationCubic(
            final double y0, final double y1, final double y2, final double y3, final double t) {

        final double c0 = 0.5*(-t + 2*t*t - t*t*t);
        final double c1 = 0.5*(2 - 5*t*t + 3*t*t*t);
        final double c2 = 0.5*(t + 4*t*t - 3*t*t*t);
        final double c3 = 0.5*(-t*t + t*t*t);
        final double sum = c0 + c1 + c2 + c3;
        return (c0*y0 + c1*y1 + c2*y2 + c3*y3)/sum;
    }

    @Override
    public String toString() {
        return "Cubic convolution resampling";
    }
}
