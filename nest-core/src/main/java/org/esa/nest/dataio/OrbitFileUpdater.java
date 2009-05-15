package org.esa.nest.dataio;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.dataio.envisat.EnvisatOrbitReader;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.util.Settings;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Sep 4, 2008
 * To change this template use File | Settings | File Templates.
 */
public class OrbitFileUpdater {

    private final EnvisatOrbitReader reader;
    private final Product product;
    private final int abs_orbit;
    private final File orbitFile;

    public enum OrbitType { DORIS_VOR, DORIS_POR }

    public OrbitFileUpdater(Product prod, OrbitType orbitType) throws IOException {
        product = prod;

        final MetadataElement absRoot = product.getMetadataRoot().getElement(AbstractMetadata.ABSTRACT_METADATA_ROOT);
        abs_orbit = absRoot.getAttributeInt(AbstractMetadata.ABS_ORBIT, 0);

        reader = new EnvisatOrbitReader();

        String orbitPath = "";
        if(orbitType == OrbitType.DORIS_VOR)
            orbitPath = Settings.instance().get("dorisVOROrbitPath");
        else if(orbitType == OrbitType.DORIS_POR)
            orbitPath = Settings.instance().get("dorisPOROrbitPath");

        final Date startDate = product.getStartTime().getAsDate();
        int month = startDate.getMonth()+1;
        String folder = String.valueOf(startDate.getYear() + 1900);
        if(month < 10)
            folder +='0';
        folder += month;
        orbitPath += File.separator + folder;
        
        orbitFile = FindOrbitFile(reader, new File(orbitPath), startDate);

        if(orbitFile == null)
            throw new IOException("Unable to find suitable orbit file");
    }

    private static File FindOrbitFile(EnvisatOrbitReader reader, File path, Date productDate) throws IOException {

        final File[] list = path.listFiles();
        if(list == null) return null;
        
        for(File f : list) {
            if(f.isDirectory()) {
                final File foundFile = FindOrbitFile(reader, f, productDate);
                if(foundFile != null)
                    return foundFile;
            }

            reader.readProduct(f);

            final Date startDate = reader.getSensingStart();
            final Date stopDate = reader.getSensingStop();
            if (productDate.after(startDate) && productDate.before(stopDate)) {
                return f;
            }
        }

        return null;
    }

    public void updateStateVector() throws IOException {

        if(orbitFile == null) return;

        final Date startDate = product.getStartTime().getAsDate();

        reader.readOrbitData();
        EnvisatOrbitReader.OrbitVector orb = reader.getOrbitVector(0);//startDate);

        System.out.println("absOrbit " + orb.absOrbit);
    }


}
