package org.esa.nest.dat.plugins;

import com.bc.ceres.core.ProgressMonitor;
import com.thoughtworks.xstream.io.xml.xppdom.Xpp3Dom;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.graph.*;

import java.util.*;

public class GraphExecuter extends Observable {

    private final GPF gpf;
    private final Graph graph;
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

    void addOperatorSource(String id, String sourceName, String sourceID) {
        Node node = graph.getNode(id);
        NodeSource ns = new NodeSource(sourceName, sourceID);
       
        node.addSource(ns);
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


    class GraphEvent {

        events  eventType;
        Object  data;

        GraphEvent(events type, Object d) {
            eventType = type;
            data = d;
        }
    }
}
