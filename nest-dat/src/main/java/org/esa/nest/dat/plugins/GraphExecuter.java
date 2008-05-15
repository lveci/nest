package org.esa.nest.dat.plugins;

import com.bc.ceres.core.ProgressMonitor;
import com.thoughtworks.xstream.io.xml.xppdom.Xpp3Dom;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.gpf.graph.*;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.util.DatUtils;

import java.util.*;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class GraphExecuter extends Observable {

    private final GPF gpf;
    private Graph graph;
    private int idCount = 0;
    private final Vector nodeList = new Vector(30);

    enum events { ADD_EVENT, REMOVE_EVENT, SELECT_EVENT }

    public GraphExecuter() {

        gpf = GPF.getDefaultInstance();
        gpf.getOperatorSpiRegistry().loadOperatorSpis();

        graph = new Graph("Graph");
    }

    Vector GetGraphNodes() {
        return nodeList;
    }

    void ClearGraph() {
        graph = null;
        graph = new Graph("Graph");
        nodeList.clear();
    }

    GraphNode findGraphNode(String id) {    
        for (Enumeration e = nodeList.elements(); e.hasMoreElements();)
        {
            GraphNode n = (GraphNode) e.nextElement();
            if(n.getID().equals(id)) {
                return n;
            }
        }
        return null;
    }

    void setSelectedNode(GraphNode node) {
        if(node == null) return;
        setChanged();
        notifyObservers(new GraphEvent(events.SELECT_EVENT, node));
        clearChanged();
    }

    /**
     * Gets the list of operators
     * @return set of operator names
     */
    Set GetOperatorList() {
        return gpf.getOperatorSpiRegistry().getAliases();
    }

    GraphNode addOperator(String opName) {

        String id = opName + ' ' + ++idCount;
        Node newNode = new Node(id, opName);

        Xpp3Dom parameters = new Xpp3Dom("parameters");
        newNode.setConfiguration(parameters);

        graph.addNode(newNode);

        GraphNode newGraphNode = new GraphNode(newNode);
        nodeList.add(newGraphNode);

        setChanged();
        notifyObservers(new GraphEvent(events.ADD_EVENT, newGraphNode));
        clearChanged();

        return newGraphNode;
    }

    void removeOperator(GraphNode node) {

        setChanged();
        notifyObservers(new GraphEvent(events.REMOVE_EVENT, node));
        clearChanged();

        // remove as a source from all nodes
        for (Enumeration e = nodeList.elements(); e.hasMoreElements();)
        {
            GraphNode n = (GraphNode) e.nextElement();
            n.disconnectOperatorSources(node);
        }

        graph.removeNode(node.getID());
        nodeList.remove(node);
    }

    void setOperatorParam(String id, String paramName, String value) {

        Xpp3Dom xml = new Xpp3Dom(paramName);
        xml.setValue(value);

        Node node = graph.getNode(id);
        node.getConfiguration().addChild(xml);
    }

    void AssignAllParameters() {

        for (Enumeration e = nodeList.elements(); e.hasMoreElements();)
        {
            GraphNode n = (GraphNode) e.nextElement();
            n.AssignParameters();
        }
    }
    
    /**
     * Begins graph processing
     * @param pm The ProgressMonitor
     * @throws GraphException if cannot process graph
     */
    void executeGraph(ProgressMonitor pm) throws GraphException {
        AssignAllParameters();
        GraphProcessor processor = new GraphProcessor();
        processor.executeGraph(graph, pm);
    }

    void saveGraph() throws GraphException {

        AssignAllParameters();

        String filePath = DatUtils.GetFilePath("Save Graph", "XML", "xml", "Graph File", true);
        if(filePath != null)
            writeGraph(filePath);
    }

    void writeGraph(String filePath) throws GraphException {

        try {
            FileWriter fileWriter = new FileWriter(filePath);

            try {
                GraphIO.write(graph, fileWriter);
            } finally {
                fileWriter.close();
            }
        } catch(IOException e) {
            throw new GraphException("Unable to write graph to " + filePath);
        }
    }

    void loadGraph() throws GraphException {

        String filePath = DatUtils.GetFilePath("Load Graph", "XML", "xml", "Graph File", false);
        try {
            if(filePath == null) return;
            FileReader fileReader = new FileReader(filePath);
            Graph graphFromFile;
            try {
                graphFromFile = GraphIO.read(fileReader, null);
            } finally {
                fileReader.close();
            }

            if(graphFromFile != null) {
                graph = graphFromFile;
                nodeList.clear();

                Node[] nodes = graph.getNodes();
                for (Node n : nodes) {
                    GraphNode newGraphNode = new GraphNode(n);
                    nodeList.add(newGraphNode);
                    
                    setChanged();
                    notifyObservers(new GraphEvent(events.ADD_EVENT, newGraphNode));
                    clearChanged();
                }
            }
        } catch(IOException e) {
            throw new GraphException("Unable to load graph " + filePath);
        }
    }

    static class GraphEvent {

        final events  eventType;
        final Object  data;

        GraphEvent(events type, Object d) {
            eventType = type;
            data = d;
        }
    }
}
