package org.esa.nest.db;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.File;
import java.sql.SQLException;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.util.TestUtils;

/**

 */
public class TestProductDao extends TestCase {

    private ProductDao db;

    public TestProductDao(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        super.setUp();

        db = new ProductDao("productDB");
        final boolean connected = db.connect();
        if(!connected) {
            throw new IOException("Unable to connect to database\n"+db.getLastSQLException().getMessage());   
        }
    }

    public void tearDown() throws Exception {
        super.tearDown();

        db.disconnect();
    }

    public void testAddAll() throws IOException, SQLException {
        final File folder1 = new File(TestUtils.rootPathASAR);
        if(!folder1.exists()) return;

        recurseProcessFolder(folder1, db);
    }

    public static void recurseProcessFolder(final File folder, final ProductDao db) throws SQLException {
        for(File file : folder.listFiles()) {

            if(file.isDirectory()) {
                recurseProcessFolder(file, db);
            } else {
                if(TestUtils.isNotProduct(file))
                    continue;

                if(db.pathExistsInDB(file))
                    continue;

                final ProductReader reader = ProductIO.getProductReaderForFile(file);
                if(reader != null) {
                    Product sourceProduct = null;
                    try {
                        sourceProduct = reader.readProductNodes(file, null);
                    } catch(Exception e) {
                        System.out.println("Unable to read "+file.getAbsolutePath());
                    }
                    if(sourceProduct != null) {
                        //System.out.println("Adding "+file.getAbsolutePath());

                        db.saveProduct(sourceProduct);
                    }
                }
            }
        }
    }

    public void testListAll() throws IOException, SQLException {

        final ProductEntry[] list = db.getProductEntryList();
        for(ProductEntry entry : list) {
            System.out.println(entry.getId() + " " + entry.getPath());
        }
    }

}
