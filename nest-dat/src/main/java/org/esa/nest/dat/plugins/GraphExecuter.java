package org.esa.nest.dat.plugins;

import com.bc.ceres.core.ProgressMonitor;
import com.thoughtworks.xstream.io.xml.xppdom.Xpp3Dom;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.graph.*;
import org.esa.beam.util.io.FileUtils;
import org.esa.beam.util.io.BeamFileFilter;
import org.esa.beam.visat.SharedApp;
import org.esa.nest.dat.util.DatUtils;

import java.util.*;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;

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

    GraphNode findGraphNode(String id) {    
        for (Enumeration e = nodeList.elements(); e.hasMoreElements();)
        {
            GraphNode n = (GraphNode) e.nextElement();
            if(n.getNode().getId().equals(id)) {
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

    void addOperator(String opName) {

        String id = opName + " " + ++idCount;
        Node newNode = new Node(id, opName);

        Xpp3Dom parameters = new Xpp3Dom("parameters");
        newNode.setConfiguration(parameters);

        graph.addNode(newNode);

        GraphNode newGraphNode = new GraphNode(newNode);
        nodeList.add(newGraphNode);

        setChanged();
        notifyObservers(new GraphEvent(events.ADD_EVENT, newGraphNode));
        clearChanged();
    }

    void removeOperator(GraphNode node) {

        setChanged();
        notifyObservers(new GraphEvent(events.REMOVE_EVENT, node));
        clearChanged();

        graph.removeNode(node.getNode().getId());
        nodeList.remove(node);
    }

    void addOperatorSource(GraphNode source, GraphNode target) {
        Node srcNode = source.getNode();
        NodeSource ns = new NodeSource("sourceProduct", srcNode.getId());

        target.getNode().addSource(ns);
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
            Map<String, Object> parameterMap = n.getParameterMap();
            Set keys = parameterMap.keySet();                           // The set of keys in the map.
            for (Object key : keys) {
                Object value = parameterMap.get(key);                   // Get the value for that key.

                Xpp3Dom xml = new Xpp3Dom((String)key);
                xml.setValue(value.toString());

                n.getNode().getConfiguration().addChild(xml);
            }
        }
    }

    /**
     * Begins graph processing
     *
     * @throws GraphException
     */
    void executeGraph() throws GraphException {
        AssignAllParameters();
        GraphProcessor processor = new GraphProcessor();
        processor.executeGraph(graph, ProgressMonitor.NULL);
    }

    void saveGraph() {
        try {
            String filePath = DatUtils.GetFilePath("Save Graph", "XML", "xml", "Graph File", true);
            if(filePath == null) return;
            FileWriter fileWriter = new FileWriter(filePath);
            try {
                GraphIO.write(graph, fileWriter);
            } finally {
                fileWriter.close();
            }
        } catch(IOException e) {

        }
    }

    void loadGraph() {
        try {
            String filePath = DatUtils.GetFilePath("Load Graph", "XML", "xml", "Graph File", false);
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

        }
    }

    class GraphEvent {

        final events  eventType;
        final Object  data;

        GraphEvent(events type, Object d) {
            eventType = type;
            data = d;
        }
    }
}
