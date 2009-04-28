/*
 * $Id: ProgressMonitorInputStreamObserver.java,v 1.1 2009-04-28 14:39:32 lveci Exp $
 *
 * Copyright (c) 2003 Brockmann Consult GmbH. All right reserved.
 * http://www.brockmann-consult.de
 */
package com.bc.io;

import javax.swing.ProgressMonitor;

public class ProgressMonitorInputStreamObserver implements InputStreamObserver {
    private ProgressMonitor progressMonitor;

    public ProgressMonitorInputStreamObserver(ProgressMonitor progressMonitor) {
        this.progressMonitor = progressMonitor;
    }

    public void onReadStarted(long numBytesTotal) {
        progressMonitor.setMinimum(0);
        progressMonitor.setMaximum((int) numBytesTotal);
    }

    public void onReadProgress(long numBytesRead) {
        progressMonitor.setProgress((int) numBytesRead);
    }

    public void onReadEnded() {
        progressMonitor.close();
    }

    public boolean isReadingCanceled() {
        return progressMonitor.isCanceled();
    }
}
