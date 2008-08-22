package org.esa.nest.dat.toolviews.Projects;

import junit.framework.TestCase;

import java.io.File;

import org.esa.nest.util.DatUtils;
import org.esa.beam.util.SystemUtils;


/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Jul 2, 2008
 * To change this template use File | Settings | File Templates.
 */
public class TestProject extends TestCase {

    private Project project = Project.instance();
    private File projectFolder = new File(DatUtils.findHomeFolder().getAbsolutePath()
            + File.separator + "testProject");
    private File projectFile = new File(projectFolder.getAbsolutePath()
            + File.separator + "TestProjectFile.xml");

    public void setUp() throws Exception {
        if(!projectFolder.exists())
            projectFolder.mkdir();
    }

    public void tearDown() throws Exception {
        SystemUtils.deleteFileTree(projectFolder);
    }

    public void testInitProject() {
        project.initProject(projectFile);

        File[] files = projectFolder.listFiles();
        assertEquals(files.length, 3);

        assertEquals(files[0].getName(), "Graphs");
        assertEquals(files[1].getName(), "Processed Products");
        assertEquals(files[2].getName(), "ProductSets");

    }



}