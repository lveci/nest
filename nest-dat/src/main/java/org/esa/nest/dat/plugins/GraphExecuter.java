package org.esa.nest.dat.plugins;

import com.bc.ceres.core.ProgressMonitor;
import com.thoughtworks.xstream.io.xml.xppdom.Xpp3Dom;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.graph.*;

import java.util.Set;

public class GraphExecuter {

    private GPF gpf;
    Graph graph;
    String id = "myGraph";

    public GraphExecuter() {

        gpf = GPF.getDefaultInstance();
        gpf.getOperatorSpiRegistry().loadOperatorSpis();

        graph = new Graph(id);
    }

    public void addOperator(String opName, String id) {

        Node newNode = new Node(id, opName);

        Xpp3Dom parameters = new Xpp3Dom("parameters");
        newNode.setConfiguration(parameters);

        graph.addNode(newNode);
    }

    public void addOperatorSource(String id, String sourceName, String sourceID) {
        Node node = graph.getNode(id);
        NodeSource ns = new NodeSource(sourceName, sourceID);
       
        node.addSource(ns);
    }

    public void setOperatorParam(String id, String paramName, String value) {

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
    public void executeGraph() throws GraphException {
        GraphProcessor processor = new GraphProcessor();
        processor.executeGraph(graph, ProgressMonitor.NULL);
    }



}
