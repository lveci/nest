/*
 * $Id: CachingObjectArray.java,v 1.4 2009-11-16 17:26:48 lveci Exp $
 *
 * Copyright (c) 2003 Brockmann Consult GmbH. All right reserved.
 * http://www.brockmann-consult.de
 */
package com.bc.util;

public class CachingObjectArray {

    private ObjectFactory _objectFactory;
    private ObjectArray _objectArray;

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
    }

    public synchronized final Object getObject(int index) throws Exception {
        if (index < _objectArray.getMinIndex() || index > _objectArray.getMaxIndex()) {
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

    public final void setObject(int index, Object o) {
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
