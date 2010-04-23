package org.esa.nest.db;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.File;
import java.sql.*;
import java.awt.*;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.util.TestUtils;

/**

 */
public class TestProductDao extends TestCase {

    private ProductDB db;

    public TestProductDao(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        super.setUp();

        db = ProductDB.instance();
        final boolean connected = db.connect();
        if(!connected) {
            throw new IOException("Unable to connect to database\n"+db.getLastSQLException().getMessage());   
        }
    }

    public void tearDown() throws Exception {
        super.tearDown();

        db.disconnect();
        ProductDB.deleteInstance();
    }

    public void testAddAll() throws IOException, SQLException {
        final File folder1 = new File(TestUtils.rootPathASAR);
        if(!folder1.exists()) return;

        recurseProcessFolder(folder1, db);
    }

    public static void recurseProcessFolder(final File folder, final ProductDB db) throws SQLException {
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
                        System.out.println("Adding "+file.getAbsolutePath());

                        db.saveProduct(sourceProduct);
                        sourceProduct.dispose();
                    }
                }
            }
        }
    }

    public void testListAll() throws SQLException {

        final ProductEntry[] list = db.getProductEntryList();
        for(ProductEntry entry : list) {
            System.out.println(entry.getId() + " " + entry.getFile());
        }
    }

    public void testGetAllMissions() throws SQLException {
        System.out.println("Missions:");
        final String[] missions = db.getAllMissions();
        for(String str : missions) {
            System.out.println(str);
        }
    }

    public void testGetENVISATProductTypes() throws SQLException {
        System.out.println("ENVISAT productTypes:");
        final String[] productTypes = db.getProductTypes(new String[] { "ENVISAT" });
        for(String str : productTypes) {
            System.out.println(str);
        }
    }

    public void testGetAllProductTypes() throws SQLException {
        System.out.println("All productTypes:");
        final String[] productTypes = db.getAllProductTypes();
        for(String str : productTypes) {
            System.out.println(str);
        }
    }

    public void testSelect() throws SQLException {
        final String strGetProductsWhere = "SELECT * FROM APP.PRODUCTS WHERE MISSION='ENVISAT'";

        //final Statement queryStatement = db.getDBConnection().createStatement();
        //final ResultSet results = queryStatement.executeQuery(strGetProductsWhere);
    }

    public void testRectIntersect() {
        Rectangle.Float a = new Rectangle.Float(-10, 10, 100, 100);
        Rectangle.Float b = new Rectangle.Float(-20, 20, 50, 50);

        boolean r1 = a.intersects(b);
        boolean r2 = b.intersects(a);

        System.out.println();
    }

}
