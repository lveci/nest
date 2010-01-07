/*
 * $Id: CachingObjectArray.java,v 1.5 2010-01-07 20:33:45 lveci Exp $
 *
 * Copyright (c) 2003 Brockmann Consult GmbH. All right reserved.
 * http://www.brockmann-consult.de
 */
package com.bc.util;

public class CachingObjectArray {

    private ObjectFactory _objectFactory;
    private ObjectArray _objectArray;
    private int minIndex, maxIndex;

    public CachingObjectArray(ObjectFactory objectFactory) {
        if (objectFactory == null) {
            throw new IllegalArgumentException("objectFactory == null");
        }
        _objectFactory = objectFactory;
    }

    public ObjectFactory getObjectFactory() {
        return _objectFactory;
    }

    public void setObjectFactory(ObjectFactory objectFactory) {
        _objectFactory = objectFactory;
    }

    public void setCachedRange(int indexMin, int indexMax) {
        if (indexMax < indexMin) {
            throw new IllegalArgumentException("indexMin < indexMax");
        }
        final ObjectArray objectArray = new ObjectArray(indexMin, indexMax);
        final ObjectArray objectArrayOld = _objectArray;
        if (objectArrayOld != null) {
            objectArray.set(objectArrayOld);
            objectArrayOld.clear();
        }
        _objectArray = objectArray;
        minIndex = _objectArray.getMinIndex();
        maxIndex = _objectArray.getMaxIndex();
    }

    public synchronized final Object getObject(final int index) throws Exception {
        if (index < minIndex || index > maxIndex) {
            return _objectFactory.createObject(index);
        } else {
            Object object = _objectArray.getObject(index);
            if (object == null) {
                object = _objectFactory.createObject(index);
                _objectArray.setObject(index, object);
            }
            return object;
        }
    }

    public final void setObject(final int index, final Object o) {
        final Object object = _objectArray.getObject(index);
        if (object == null) {
             _objectArray.setObject(index, o);
        }
    }

    public void clear() {
        if (_objectArray != null) {
            _objectArray.clear();
        }
    }

    public static interface ObjectFactory {

        Object createObject(int index) throws Exception;
    }
}
