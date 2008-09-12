package org.esa.nest.dataio;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.dataio.envisat.EnvisatOrbitReader;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Sep 4, 2008
 * To change this template use File | Settings | File Templates.
 */
public class ApplyOrbitFile {

    EnvisatOrbitReader reader;
    Product product;
    int abs_orbit;

    File vorPath = new File("P:\\nest\\nest\\ESA Data\\Orbits\\Doris\\vor");

    public ApplyOrbitFile(Product prod) {
        product = prod;

        MetadataElement absRoot = product.getMetadataRoot().getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);

        abs_orbit = absRoot.getAttributeInt(AbstractMetadata.ABS_ORBIT, 0);

        reader = new EnvisatOrbitReader();

        FindOrbitFile(reader, vorPath, product.getStartTime().getAsDate());
    }

    public static File FindOrbitFile(EnvisatOrbitReader reader, File path, Date productDate) {

        File[] list = path.listFiles();
        for(File f : list) {
            if(f.isDirectory()) {
                File foundFile = FindOrbitFile(reader, f, productDate);
                if(foundFile != null)
                    return foundFile;
            }

            try {
                reader.readProduct(f);

                Date startDate = reader.getSensingStart();
                Date stopDate = reader.getSensingStop();
                if (productDate.after(startDate) && productDate.before(stopDate)) {
                    return f;

                }
            } catch (IOException e) {
                
            }
        }

        return null;
    }

}
