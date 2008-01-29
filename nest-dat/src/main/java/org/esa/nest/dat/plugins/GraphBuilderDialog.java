package org.esa.nest.dat.plugins;

import com.bc.ceres.core.Assert;
import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.binding.ValueContainerFactory;
import com.bc.ceres.binding.ValueContainer;
import com.bc.ceres.binding.swing.SwingBindingContext;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.ui.tool.ToolButtonFactory;
import org.esa.beam.framework.gpf.graph.GraphException;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.ui.ParametersPane;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.framework.gpf.annotations.ParameterDescriptorFactory;
import org.esa.nest.dat.DatContext;
import org.esa.nest.dat.util.DatUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Set;
import java.util.Observer;

/**
 *  Provides the User Interface for creating, loading and saving Graphs
 */
public class GraphBuilderDialog implements Observer {

    private static final ImageIcon processIcon = DatUtils.LoadIcon("org/esa/nest/icons/Gears24.gif");
    private static final ImageIcon saveIcon = DatUtils.LoadIcon("org/esa/nest/icons/Save24b.gif");
    private static final ImageIcon loadIcon = DatUtils.LoadIcon("org/esa/nest/icons/Open24b.gif");
    private JPanel mainPanel;

    private JPanel progressPanel;
    private JProgressBar progressBar;
    private JFrame mainFrame;

    private final GraphExecuter graphEx;

    //TabbedPanel
    private JTabbedPane tabbedPanel;
    private static final ImageIcon OpIcon = UIUtils.loadImageIcon("icons/Gears20.gif");

    public GraphBuilderDialog() {

         graphEx = new GraphExecuter();
         graphEx.addObserver(this);
    }

    public JFrame getFrame() {
        if (mainFrame == null) {
            mainFrame = new JFrame("Graph Builder");
            mainFrame.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
            initUI();

            mainFrame.setBounds(new Rectangle(200, 100, 500, 600));
        }
        return mainFrame;
    }

    private JComponent CreateOpTab(GraphNode node) {

        String operatorName = node.getNode().getOperatorName();
        final OperatorSpi operatorSpi = GPF.getDefaultInstance().getOperatorSpiRegistry().getOperatorSpi(operatorName);
        if (operatorSpi == null) {
            throw new IllegalArgumentException("operatorName");
        }

        ValueContainerFactory factory = new ValueContainerFactory(new ParameterDescriptorFactory());
        ValueContainer valueContainer = factory.createMapBackedValueContainer(operatorSpi.getOperatorClass(), node.getParameterMap());
        SwingBindingContext context = new SwingBindingContext(valueContainer);

        ParametersPane parametersPane = new ParametersPane(context);
        return new JScrollPane(parametersPane.createPanel());
    }

    private void initUI() {
        mainPanel = new JPanel(new BorderLayout(4, 4));

        // north panel
        final JPanel northPanel = new JPanel(new BorderLayout(4, 4));

        GraphPanel graphPanel = new GraphPanel(graphEx);
        graphPanel.setBackground(Color.WHITE);
        graphPanel.setPreferredSize(new Dimension(500,500));
        JScrollPane scrollPane = new JScrollPane(graphPanel);
        scrollPane.setPreferredSize(new Dimension(300,300));
        northPanel.add(scrollPane, BorderLayout.CENTER);

        mainPanel.add(northPanel, BorderLayout.NORTH);

        // mid panel
        final JPanel midPanel = new JPanel(new BorderLayout(4, 4));
        tabbedPanel = new JTabbedPane();
        midPanel.add(tabbedPanel, BorderLayout.CENTER);

        mainPanel.add(midPanel, BorderLayout.CENTER);

        // south panel
        final JPanel southPanel = new JPanel(new BorderLayout(4, 4));
        final JPanel buttonPanel = new JPanel();
        initButtonPanel(buttonPanel);
        southPanel.add(buttonPanel, BorderLayout.CENTER);

        // progress Bar
        progressBar = new JProgressBar();
        progressBar.setName(getClass().getName() + "progressBar");
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressPanel = new JPanel();
        progressPanel.setLayout(new BorderLayout());
        progressPanel.add(progressBar);
        progressPanel.setVisible(false);
        southPanel.add(progressPanel, BorderLayout.SOUTH);
        // todo progressPanel

        mainPanel.add(southPanel, BorderLayout.SOUTH);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        mainFrame.add(mainPanel);
    }

    private void initButtonPanel(final JPanel panel) {
        panel.setLayout(new GridBagLayout());
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        JButton processButton = CreateButton("processButton", "Process", processIcon, panel);
        processButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                DoProcessing();
            }
        });

        JButton saveButton = CreateButton("saveButton", "Save", saveIcon, panel);
        saveButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                SaveGraph();
            }
        });

        JButton loadButton = CreateButton("loadButton", "Load", loadIcon, panel);
        loadButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                LoadGraph();
            }
        });

        gbc.weightx = 0;
        panel.add(loadButton, gbc);
        panel.add(saveButton, gbc);
        panel.add(processButton, gbc);
    }

    JButton CreateButton(String name, String text, ImageIcon icon, JPanel panel) {
        JButton button = new JButton();
        button.setName(getClass().getName() + name);
        button = new JButton();
        button.setIcon(icon);
        button.setBackground(panel.getBackground());
        button.setText(text);
        button.setActionCommand(name);
        return button;
    }

    private void DoProcessing() {

        try {
            graphEx.executeGraph();
        } catch(GraphException e) {
            throw new OperatorException(e);
        }
    }

     private void SaveGraph() {

        //try {
            graphEx.saveGraph();
        //} catch(GraphException e) {
        //    throw new OperatorException(e);
        //}
    }

     private void LoadGraph() {

        //try {
            graphEx.loadGraph();
        //} catch(GraphException e) {
        //    throw new OperatorException(e);
        //}
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

        GraphExecuter.GraphEvent event = (GraphExecuter.GraphEvent)data;
        GraphNode node = (GraphNode)event.data;
        String opID = node.getNode().getId();
        if(event.eventType == GraphExecuter.events.ADD_EVENT) {

            tabbedPanel.addTab(opID, OpIcon, CreateOpTab(node), opID + " Operator");
        } else if(event.eventType == GraphExecuter.events.REMOVE_EVENT) {

            int index = tabbedPanel.indexOfTab(opID);
            tabbedPanel.remove(index);
        } else if(event.eventType == GraphExecuter.events.SELECT_EVENT) {

            int index = tabbedPanel.indexOfTab(opID);
            tabbedPanel.setSelectedIndex(index);
        }
    }





    /**
     * A {@link com.bc.ceres.core.ProgressMonitor} which uses a
     * Swing's {@link javax.swing.ProgressMonitor} to display progress.
     */
    private class ProgressBarProgressMonitor implements ProgressMonitor {

        private final JProgressBar progressBar;
        private final JLabel messageLabel;

        private double currentWork;
        private double totalWork;

        private int totalWorkUI;
        private int currentWorkUI;
        private int lastWorkUI;
        private boolean cancelRequested;

        public ProgressBarProgressMonitor(JProgressBar progressBar, JLabel messageLabel) {
            this.progressBar = progressBar;
            this.messageLabel = messageLabel;
        }

        /**
         * Notifies that the main task is beginning.  This must only be called once
         * on a given progress monitor instance.
         *
         * @param name      the name (or description) of the main task
         * @param totalWork the total number of work units into which
         *                  the main task is been subdivided. If the value is <code>UNKNOWN</code>
         *                  the implementation is free to indicate progress in a way which
         *                  doesn't require the total number of work units in advance.
         */
        public void beginTask(String name, int totalWork) {
            Assert.notNull(name, "name");
            currentWork = 0.0;
            this.totalWork = totalWork;
            currentWorkUI = 0;
            lastWorkUI = 0;
            totalWorkUI = totalWork;
            if (messageLabel != null) {
                messageLabel.setText(name);
            }
            cancelRequested = false;
            setDescription(name);
            setVisibility(true);
            progressBar.setMaximum(totalWork);
            //toggleUpdateButton(stopCommand);

        }

        /**
         * Notifies that the work is done; that is, either the main task is completed
         * or the user canceled it. This method may be called more than once
         * (implementations should be prepared to handle this case).
         */
        public void done() {
            runInUI(new Runnable() {
                public void run() {
                    if (progressBar != null) {
                        progressBar.setValue(progressBar.getMaximum());
                        setVisibility(false);
                        //toggleUpdateButton(updateCommand);

                        mainPanel.setCursor(Cursor.getDefaultCursor());
                    }
                }
            });
        }


        /**
         * Internal method to handle scaling correctly. This method
         * must not be called by a client. Clients should
         * always use the method </code>worked(int)</code>.
         *
         * @param work the amount of work done
         */
        public void internalWorked(double work) {
            currentWork += work;
            currentWorkUI = (int) (totalWorkUI * currentWork / totalWork);
            if (currentWorkUI > lastWorkUI) {
                runInUI(new Runnable() {
                    public void run() {
                        if (progressBar != null) {
                            int progress = progressBar.getMinimum() + currentWorkUI;
                            progressBar.setValue(progress);
                            setVisibility(true);
                            //toggleUpdateButton(stopCommand);
                        }
                        lastWorkUI = currentWorkUI;
                    }
                });
            }
        }

        /**
         * Returns whether cancelation of current operation has been requested.
         * Long-running operations should poll to see if cancelation
         * has been requested.
         *
         * @return <code>true</code> if cancellation has been requested,
         *         and <code>false</code> otherwise
         *
         * @see #setCanceled(boolean)
         */
        public boolean isCanceled() {
            return cancelRequested;
        }

        /**
         * Sets the cancel state to the given value.
         *
         * @param canceled <code>true</code> indicates that cancelation has
         *                 been requested (but not necessarily acknowledged);
         *                 <code>false</code> clears this flag
         *
         * @see #isCanceled()
         */
        public void setCanceled(boolean canceled) {
            cancelRequested = canceled;
            if (canceled) {
                done();
            }
        }

        /**
         * Sets the task name to the given value. This method is used to
         * restore the task label after a nested operation was executed.
         * Normally there is no need for clients to call this method.
         *
         * @param name the name (or description) of the main task
         *
         * @see #beginTask(String, int)
         */
        public void setTaskName(final String name) {
            runInUI(new Runnable() {
                public void run() {
                    if (messageLabel != null) {
                        messageLabel.setText(name);
                    }
                }
            });
        }

        /**
         * Notifies that a subtask of the main task is beginning.
         * Subtasks are optional; the main task might not have subtasks.
         *
         * @param name the name (or description) of the subtask
         */
        public void setSubTaskName(final String name) {
            setVisibility(true);
            messageLabel.setText(name);
            //toggleUpdateButton(stopCommand);
        }

        /**
         * Notifies that a given number of work unit of the main task
         * has been completed. Note that this amount represents an
         * installment, as opposed to a cumulative amount of work done
         * to date.
         *
         * @param work the number of work units just completed
         */
        public void worked(int work) {
            internalWorked(work);
        }

        ////////////////////////////////////////////////////////////////////////
        // Stuff to be performed in Swing's event-dispatching thread

        private void runInUI(Runnable task) {
            if (SwingUtilities.isEventDispatchThread()) {
                task.run();
            } else {
                SwingUtilities.invokeLater(task);
            }
        }

        private void setDescription(final String description) {
            //statusLabel.setText(description);
        }

        private void setVisibility(final boolean visible) {
            progressPanel.setVisible(visible);
           // statusLabel.setVisible(visible);
        }
    }

}
