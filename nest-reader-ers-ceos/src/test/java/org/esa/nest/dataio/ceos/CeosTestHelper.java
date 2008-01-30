
package org.esa.nest.dataio.ceos;

import javax.imageio.stream.ImageOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Created by Marco Peters.
 *
 * @author Marco Peters
 * @version $Revision: 1.1 $ $Date: 2008-01-30 20:51:48 $
 */
public class CeosTestHelper {

    public static void writeBlanks(final ImageOutputStream ios, final int numBlanks) throws IOException {
        final char[] chars = new char[numBlanks];
        Arrays.fill(chars, ' ');
        ios.writeBytes(new String(chars));
    }
}
