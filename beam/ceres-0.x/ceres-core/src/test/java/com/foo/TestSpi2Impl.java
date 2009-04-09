package com.foo;

import com.acme.TestSpi2;
import org.junit.Ignore;

/**
 * Created by Marco Peters.
 *
 * @author Marco Peters
 * @version $Revision: 1.1 $ $Date: 2009-04-09 17:06:19 $
 */
@Ignore
public class TestSpi2Impl implements TestSpi2 {

    public Object createAnotherService() {
        return new int[]{};
    }
}
