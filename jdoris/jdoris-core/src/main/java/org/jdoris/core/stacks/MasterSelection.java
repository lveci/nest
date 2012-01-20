package org.jdoris.core.stacks;

import org.jdoris.core.Baseline;
import org.jdoris.core.Orbit;
import org.jdoris.core.SLCImage;

/**
 * User: pmar@ppolabs.com
 * Date: 1/20/12
 * Time: 3:39 PM
 */
public class MasterSelection {

    private final static int BTEMP_CRITICAL = 3 * 365;
    private final static int BPERP_CRITICAL = 1200;
    private final static int DFDC_CRITICAL = 1380;

    private SLCImage[] slcImages;
    private Orbit[] orbits;
    private int numOfImages;

    private long orbitNumber;
    private float modeledCoherence;

    // TODO: function to sort input array according to modeled coherence
    // TODO: critical values for other sensors then C.band ESA

    MasterSelection(SLCImage[] slcImages, Orbit[] orbits) {
        this.slcImages = slcImages;
        this.orbits = orbits;
        this.numOfImages = slcImages.length;

        if (this.numOfImages != orbits.length) {
            throw new IllegalArgumentException("Number of elements in input arrays has to be the same!");
        }
    }

    // returns orbit number of an "optimal master"
    public long getOrbitNumber() {
        return orbitNumber;
    }

    public float getModeledCoherence() {
        return modeledCoherence;
    }

    // methods
    private float modelCoherence(float bPerp, float bTemp, float fDc, float bPerpCritical,
                                 float bTempCritical, float fDcCritical) {
        return coherenceFnc(bPerp, bPerpCritical) * coherenceFnc(bTemp, bTempCritical) * coherenceFnc(fDc, fDcCritical);
    }

    private float modelCoherence(float bPerp, float bTemp, float fDc) {
        return coherenceFnc(bPerp, BPERP_CRITICAL) * coherenceFnc(bTemp, BTEMP_CRITICAL) * coherenceFnc(fDc, DFDC_CRITICAL);
    }

    private float coherenceFnc(float value, float value_CRITICAL) {
        if (Math.abs(value) > value_CRITICAL) {
            return (float) 0.01;
        } else {
            return (1 - Math.abs(value) / value_CRITICAL);
        }
    }

    // setup cplxcontainer
    private CplxContainer[] setupCplxContainers() {
        CplxContainer[] cplxContainers = new CplxContainer[numOfImages];

        for (int i = 0; i < numOfImages; i++) {
            SLCImage slcImage = slcImages[i];
            Orbit orbit = orbits[i];
            cplxContainers[i] = new CplxContainer(slcImage.getOrbitNumber(), slcImage.getMjd(), slcImage, orbit);
        }
        return cplxContainers;
    }

    private IfgStack[] setupIfgStack(CplxContainer[] cplxContainers) {
        // construct pairs from data in containers
        IfgStack[] ifgStack = new IfgStack[numOfImages];
        IfgPair[][] ifgPair = new IfgPair[numOfImages][numOfImages];

        for (int i = 0; i < numOfImages; i++) {

            CplxContainer master = cplxContainers[i];

            for (int j = 0; j < numOfImages; j++) {

                CplxContainer slave = cplxContainers[j];
                ifgPair[i][j] = new IfgPair(master, slave);

            }

            ifgStack[i] = new IfgStack(master, ifgPair[i]);
            ifgStack[i].meanCoherence();

        }
        return ifgStack;
    }

    private void findOptimalMaster(IfgStack[] ifgStack) {

        orbitNumber = ifgStack[0].master.orbitNumber;
        modeledCoherence = ifgStack[0].meanCoherence;

        for (IfgStack anIfgStack : ifgStack) {

            long orbit = anIfgStack.master.orbitNumber;
            float coherence = anIfgStack.meanCoherence;

            if (coherence > modeledCoherence) {
                modeledCoherence = coherence;
                orbitNumber = orbit;
            }

        }
    }

    public void estimateOptimalMaster() {

        CplxContainer[] cplxContainers = setupCplxContainers();
        IfgStack[] ifgStack = setupIfgStack(cplxContainers);
        findOptimalMaster(ifgStack);

    }

    // inner classes
    private class CplxContainer {

        public long orbitNumber;
        public double dateMjd;
        public SLCImage metaData;
        public Orbit orbit;

        public CplxContainer(double dateMjd, SLCImage metaData, Orbit orbit) {
            this.dateMjd = dateMjd;
            this.metaData = metaData;
            this.orbit = orbit;
        }

        public CplxContainer(long orbitNumber, double dateMjd, SLCImage metaData, Orbit orbit) {
            this.orbitNumber = orbitNumber;
            this.dateMjd = dateMjd;
            this.metaData = metaData;
            this.orbit = orbit;
        }

        public CplxContainer(long orbitNumber, SLCImage metaData) {
            this.orbitNumber = orbitNumber;
            this.metaData = metaData;
        }
    }


    private class IfgStack {

        CplxContainer master;
        IfgPair[] master_slave;
        float meanCoherence;

        public IfgStack(CplxContainer master, IfgPair... master_slave) {
            this.master = master;
            this.master_slave = master_slave;
        }

        public void meanCoherence() {
            for (IfgPair aMaster_slave : master_slave) {
                meanCoherence += aMaster_slave.coherence;
            }
            meanCoherence /= master_slave.length;
        }

    }

    private class IfgPair {

        CplxContainer master;
        CplxContainer slave;

        float bPerp;   // perpendicular baseline
        float bTemp;   // temporal baseline
        float delft_fDc;     // doppler centroid frequency difference
        float coherence;     // modeled coherence

        private Baseline tempBaseline;

        public IfgPair(CplxContainer master, CplxContainer slave) {

            this.master = master;
            this.slave = slave;

            try {
                tempBaseline = new Baseline();
                tempBaseline.model(master.metaData, slave.metaData, master.orbit, slave.orbit);
                bPerp = (float) tempBaseline.getBperp(1, 1);

            } catch (Exception e) {
                e.printStackTrace();
            }

            bTemp = (float) (master.dateMjd - slave.dateMjd);
            delft_fDc = (float) (master.metaData.doppler.getF_DC_a0() - slave.metaData.doppler.getF_DC_a0());

            coherence = modelCoherence(bPerp, bTemp, delft_fDc);

        }

    }

}
