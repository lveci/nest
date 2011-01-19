package org.esa.beam.nest_mods;

/**
 * command line Progress monitor
 */
public class StdOutProgressMonitor {
    private int percentComplete;
    private final int percentStep = 10;
    private int lastPercentComplete = percentStep;
    private final int max;

    public StdOutProgressMonitor(final int max) {
        this.max = max;

    }

    public void worked(final int tileY) {
        percentComplete = (int)((tileY / (float)max) * 100.0f);
        if(percentComplete > lastPercentComplete) {
            System.out.print(" "+ lastPercentComplete + "%");
            lastPercentComplete = ((percentComplete / percentStep) * percentStep) + percentStep;
        }
    }

    public void done() {
        System.out.println(" 100%");
    }

}
