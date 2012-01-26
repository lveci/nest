package org.jdoris.core.stacks;

import org.jdoris.core.SLCImage;
import org.jdoris.core.Orbit;
import com.bc.ceres.core.ProgressMonitor;

/**
 * Interface to selecting an optimal master for insar
 */
public interface OptimalMaster {

   public void setInput(SLCImage[] slcImages, Orbit[] orbits);

   public int estimateOptimalMaster(final ProgressMonitor pm);

   public float getModeledCoherence();

   public long getOrbitNumber();
}
