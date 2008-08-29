package org.esa.nest.dat.toolviews.Projects;

import com.jidesoft.icons.IconsFactory;
import com.jidesoft.swing.JideScrollPane;
import com.jidesoft.swing.JideSplitPane;
import org.esa.beam.framework.ui.application.support.AbstractToolView;
import org.esa.beam.visat.toolviews.lm.LayersToolView;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.util.Enumeration;
import java.util.Observer;
import java.util.Vector;

/**
 * The tool window which displays the current project
 */
public class ProjectsToolView extends AbstractToolView implements Observer {

    public static final String ID = ProjectsToolView.class.getName();

    private ProjectTree projectTree;
    private DefaultMutableTreeNode rootNode;
    private Project project = Project.instance();

    public ProjectsToolView() {
        Project.instance().addObserver(this);
    }

    @Override
    public JComponent createControl() {
        final JScrollPane layerScrollPane = new JideScrollPane(createTree());
        layerScrollPane.setPreferredSize(new Dimension(320, 480));
        layerScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        layerScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        layerScrollPane.setBorder(null);
        layerScrollPane.setViewportBorder(null);

        final JideSplitPane splitPane = new JideSplitPane(JideSplitPane.VERTICAL_SPLIT);
        splitPane.addPane(layerScrollPane);

        return splitPane;
    }

    private ProjectTree createTree() {
        rootNode = new DefaultMutableTreeNode("");
        projectTree = new ProjectTree(false);//rootNode);
        projectTree.populateTree(rootNode);
        projectTree.setRootVisible(false);
        projectTree.setShowsRootHandles(true);
        DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) projectTree.getCellRenderer();
        renderer.setLeafIcon(IconsFactory.getImageIcon(LayersToolView.class, "/org/esa/beam/resources/images/icons/RsBandAsSwath16.gif"));
        renderer.setClosedIcon(IconsFactory.getImageIcon(LayersToolView.class, "/org/esa/beam/resources/images/icons/RsGroupClosed16.gif"));
        renderer.setOpenIcon(IconsFactory.getImageIcon(LayersToolView.class, "/org/esa/beam/resources/images/icons/RsGroupOpen16.gif"));
        return projectTree;
    }

    private static void PopulateNode(Vector<ProjectSubFolder> subFolders, DefaultMutableTreeNode treeNode) {

        for (Enumeration e = subFolders.elements(); e.hasMoreElements();)
        {
            ProjectSubFolder folder = (ProjectSubFolder)e.nextElement();

            final DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(folder);
            treeNode.add(folderNode);

            Vector<ProjectFile> fileList = folder.getFileList();
            for (Enumeration file = fileList.elements(); file.hasMoreElements();)
            {
                final DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(file.nextElement());
                folderNode.add(fileNode);
            }

            Vector<ProjectSubFolder> moreFolders = folder.getSubFolders();
            if(!moreFolders.isEmpty())
                PopulateNode(moreFolders, folderNode);
        }
    }

    /**
     Implements the functionality of Observer participant of Observer Design Pattern to define a one-to-many
     dependency between a Subject object and any number of Observer objects so that when the
     Subject object changes state, all its Observer objects are notified and updated automatically.

     Defines an updating interface for objects that should be notified of changes in a subject.
     * @param subject The Observerable subject
     * @param data optional data
     */
    public void update(java.util.Observable subject, java.lang.Object data) {

        rootNode.removeAllChildren();

        ProjectSubFolder projectFolders = project.getProjectSubFolders();
        if(projectFolders == null) {
            projectTree.setRootVisible(false);
            projectTree.populateTree(rootNode);
        } else {
            rootNode.setUserObject(project.getProjectSubFolders());
            projectTree.setRootVisible(true);

            Vector<ProjectSubFolder> subFolders = project.getProjectSubFolders().getSubFolders();
            PopulateNode(subFolders, rootNode);
            projectTree.populateTree(rootNode);
        }
    }
}