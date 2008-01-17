package org.esa.nest.dat.plugins;

import com.bc.ceres.core.ProgressMonitor;
import com.thoughtworks.xstream.io.xml.xppdom.Xpp3Dom;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.graph.*;
import java.util.Observable;

import java.util.Set;

public class GraphExecuter extends Observable {

    private GPF gpf;
    private Graph graph;
    private int idCount = 0;

    public GraphExecuter() {

        gpf = GPF.getDefaultInstance();
        gpf.getOperatorSpiRegistry().loadOperatorSpis();

        graph = new Graph("Graph");
    }

    Node[] GetNodes() {
        return graph.getNodes();
    }

    void addOperator(String opName) {

        String id = opName + " " + ++idCount;
        Node newNode = new Node(id, opName);

        Xpp3Dom parameters = new Xpp3Dom("parameters");
        newNode.setConfiguration(parameters);

        graph.addNode(newNode);

        setChanged();
        notifyObservers(newNode);
        clearChanged();
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

     /**
     * Gets the list of operators
     * @return set of operator names
     */
    Set GetOperatorList() {

        return gpf.getOperatorSpiRegistry().getAliases();
    }

    /**
     * Begins graph processing
     *
     * @throws org.esa.beam.framework.gpf.graph.GraphException
     */
    void executeGraph() throws GraphException {
        GraphProcessor processor = new GraphProcessor();
        processor.executeGraph(graph, ProgressMonitor.NULL);
    }


  
}
