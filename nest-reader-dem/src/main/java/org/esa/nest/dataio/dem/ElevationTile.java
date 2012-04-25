package org.esa.nest.dataio.dem;

import java.io.IOException;

/**

 */
public interface ElevationTile {

    public void dispose();

    public float getSample(int pixelX, int pixelY) throws IOException;

    public void clearCache();
}
