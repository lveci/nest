package org.esa.nest.dat.toolviews.Projects;

import junit.framework.TestCase;

import java.io.File;

import org.esa.nest.util.ResourceUtils;
import org.esa.beam.util.SystemUtils;


/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Jul 2, 2008
 * To change this template use File | Settings | File Templates.
 */
public class TestProject extends TestCase {

    private Project project = Project.instance();
    private final static File projectFolder = new File(ResourceUtils.findHomeFolder().getAbsolutePath()
            + File.separator + "testProject");
    private final static File projectFile = new File(projectFolder.getAbsolutePath()
            + File.separator + "TestProjectFile.xml");

    @Override
    public void setUp() throws Exception {
        if(!projectFolder.exists())
            projectFolder.mkdir();
    }

    @Override
    public void tearDown() throws Exception {
        SystemUtils.deleteFileTree(projectFolder);
        project = null;
    }

    public void testInitProject() {
        project.initProject(projectFile);

        File[] files = projectFolder.listFiles();
        assertEquals(files.length, 4);

        assertEquals(files[0].getName(), "Graphs");
        assertEquals(files[1].getName(), "Imported Products");
        assertEquals(files[2].getName(), "Processed Products");
        assertEquals(files[3].getName(), "ProductSets");

    }



}