/*
 * $Id: InputStreamObserver.java,v 1.1 2009-04-28 14:39:32 lveci Exp $
 *
 * Copyright (c) 2003 Brockmann Consult GmbH. All right reserved.
 * http://www.brockmann-consult.de
 */
package com.bc.io;

public interface InputStreamObserver {
    void onReadStarted(long numBytesTotal);

    void onReadProgress(long numBytesRead);

    void onReadEnded();

    boolean isReadingCanceled();
}
