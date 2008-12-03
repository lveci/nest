package org.esa.nest.dat.plugins.graphbuilder;

import com.bc.ceres.core.ProgressMonitor;
import com.thoughtworks.xstream.io.xml.xppdom.Xpp3Dom;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpiRegistry;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.operators.common.ReadOp;
import org.esa.beam.framework.gpf.operators.common.WriteOp;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.graph.*;
import org.esa.nest.util.DatUtils;
import org.esa.nest.dat.plugins.graphbuilder.GraphNode;

import java.util.*;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;

public class GraphExecuter extends Observable {

    private final GPF gpf;
    private Graph graph;
    private GraphContext graphContext;
    private final GraphProcessor processor;

    private int idCount = 0;
    private final Vector nodeList = new Vector(30);

    enum events { ADD_EVENT, REMOVE_EVENT, SELECT_EVENT }

    public GraphExecuter() {

        gpf = GPF.getDefaultInstance();
        gpf.getOperatorSpiRegistry().loadOperatorSpis();

        graph = new Graph("Graph");
        processor = new GraphProcessor();
    }

    Vector GetGraphNodes() {
        return nodeList;
    }

    void ClearGraph() {
        graph = null;
        graph = new Graph("Graph");
        nodeList.clear();
        idCount = 0;
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
    Set<String> GetOperatorList() {
        return gpf.getOperatorSpiRegistry().getAliases();
    }

    boolean isOperatorInternal(String alias) {
        final OperatorSpiRegistry registry = gpf.getOperatorSpiRegistry();
        final OperatorSpi operatorSpi = registry.getOperatorSpi(alias);
        final OperatorMetadata operatorMetadata = operatorSpi.getOperatorClass().getAnnotation(OperatorMetadata.class);
        return !(operatorMetadata != null && !operatorMetadata.internal());
    }

    GraphNode addOperator(String opName) {

        String id = opName + ++idCount;
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

        Xpp3Dom presentationXML = new Xpp3Dom("Presentation");

        for (Enumeration e = nodeList.elements(); e.hasMoreElements();)
        {
            GraphNode n = (GraphNode) e.nextElement();
            n.AssignParameters(presentationXML);
        }

        graph.setAppData("Presentation", presentationXML);
    }

    boolean IsGraphComplete() {
        for (Enumeration e = nodeList.elements(); e.hasMoreElements();)
        {
            GraphNode n = (GraphNode) e.nextElement();
            if(!n.HasSources() && !IsNodeASource(n)) {
                return false;
            }
        }
        return true;
    }

    boolean IsNodeASource(GraphNode node) {
        for (Enumeration e = nodeList.elements(); e.hasMoreElements();)
        {
            GraphNode n = (GraphNode) e.nextElement();
            if(n.FindSource(node))
                return true;
        }
        return false;
    }

    void InitGraph() throws GraphException {
        if(IsGraphComplete()) {
            AssignAllParameters();
            recreateGraphContext();

            for (Enumeration e = nodeList.elements(); e.hasMoreElements();)
            {
                GraphNode n = (GraphNode) e.nextElement();
                n.setSourceProducts(graphContext.getNodeContext(n.getNode()).getSourceProducts());
            }
        }
    }

    void recreateGraphContext() throws GraphException {
        if(graphContext != null)
            GraphProcessor.disposeGraphContext(graphContext);

        graphContext = processor.createGraphContext(graph, ProgressMonitor.NULL);
    }

    /**
     * Begins graph processing
     * @param pm The ProgressMonitor
     * @throws GraphException if cannot process graph
     */
    void executeGraph(ProgressMonitor pm) throws GraphException {
        processor.executeGraphContext(graphContext, pm);
    }

    void saveGraph() throws GraphException {

        File filePath = DatUtils.GetFilePath("Save Graph", "XML", "xml", "GraphFile", true);
        if(filePath != null)
            writeGraph(filePath.getAbsolutePath());
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

    void loadGraph(File filePath) throws GraphException {

        try {
            if(filePath == null) return;
            FileReader fileReader = new FileReader(filePath.getAbsolutePath());
            Graph graphFromFile;
            try {
                graphFromFile = GraphIO.read(fileReader, null);
            } finally {
                fileReader.close();
            }

            if(graphFromFile != null) {
                graph = graphFromFile;
                nodeList.clear();

                Xpp3Dom presentationXML;
                Xpp3Dom xml = graph.getApplicationData("Presentation");
                if(xml != null)
                    presentationXML = xml;
                else
                    presentationXML = new Xpp3Dom("Presentation");

                Node[] nodes = graph.getNodes();
                for (Node n : nodes) {
                    GraphNode newGraphNode = new GraphNode(n);
                    newGraphNode.setDisplayParameters(presentationXML.getChild(n.getId()));
                    nodeList.add(newGraphNode);
                    
                    setChanged();
                    notifyObservers(new GraphEvent(events.ADD_EVENT, newGraphNode));
                    clearChanged();
                }
                idCount = nodes.length;
            }
        } catch(IOException e) {
            throw new GraphException("Unable to load graph " + filePath);
        }
    }

    Graph getGraph() {
        return graph;
    }

    Stack<ProductSetNode> FindProductSets() {
        final String SEPARATOR = ",";
        final String SEPARATOR_ESC = "\\u002C"; // Unicode escape repr. of ','
        Stack<ProductSetNode> theReaderStack = new Stack<ProductSetNode>();

        Node[] nodes = graph.getNodes();
        for(Node n : nodes) {
            if(n.getOperatorName().equalsIgnoreCase("ProductSet-Reader")) {
                ProductSetNode productSetNode = new ProductSetNode();
                productSetNode.nodeID = n.getId();

                Xpp3Dom config = n.getConfiguration();
                Xpp3Dom[] params = config.getChildren();
                for(Xpp3Dom p : params) {
                    if(p.getName().equals("fileList")) {

                        StringTokenizer st = new StringTokenizer(p.getValue(), SEPARATOR);
                        int length = st.countTokens();
                        for (int i = 0; i < length; i++) {
                            String str = st.nextToken().replace(SEPARATOR_ESC, SEPARATOR);
                            productSetNode.fileList.add(str);
                        }
                        break;
                    }
                }
                theReaderStack.push(productSetNode);
            }
        }
        return theReaderStack;
    }

    static void ReplaceProductSetWithReader(Graph graph, String id, String value) {

        Node newNode = new Node(id, OperatorSpi.getOperatorAlias(ReadOp.class));
        Xpp3Dom config = new Xpp3Dom("parameters");
        Xpp3Dom fileParam = new Xpp3Dom("file");
        fileParam.setValue(value);
        config.addChild(fileParam);
        newNode.setConfiguration(config);

        graph.removeNode(id);
        graph.addNode(newNode);
    }

    static void IncrementWriterFiles(Graph graph, int count) {
        String countTag = "-" + count;
        Node[] nodes = graph.getNodes();
        for(Node n : nodes) {
            if(n.getOperatorName().equalsIgnoreCase(OperatorSpi.getOperatorAlias(WriteOp.class))) {
                Xpp3Dom config = n.getConfiguration();
                Xpp3Dom fileParam = config.getChild("file");
                String filePath = fileParam.getValue();
                String newPath;
                if(filePath.contains(".")) {
                    int idx = filePath.indexOf('.');
                    newPath = filePath.substring(0, idx) + countTag + filePath.substring(idx, filePath.length());
                } else {
                    newPath = filePath + countTag;
                }

                fileParam.setValue(newPath);
            }
        }
    }

    static void RestoreWriterFiles(Graph graph, int count) {
        String countTag = "-" + count;
        Node[] nodes = graph.getNodes();
        for(Node n : nodes) {
            if(n.getOperatorName().equalsIgnoreCase(OperatorSpi.getOperatorAlias(WriteOp.class))) {
                Xpp3Dom config = n.getConfiguration();
                Xpp3Dom fileParam = config.getChild("file");
                String filePath = fileParam.getValue();
                if(filePath.contains(countTag)) {
                    int idx = filePath.indexOf(countTag);
                    String newPath = filePath.substring(0, idx) + filePath.substring(idx+countTag.length(), filePath.length());
                    fileParam.setValue(newPath);
                }
            }
        }
    }

    public Vector<File> getProductsToOpenInDAT() {
        Vector<File> fileList = new Vector<File>(2);
        Node[] nodes = graph.getNodes();
        for(Node n : nodes) {
            if(n.getOperatorName().equalsIgnoreCase(OperatorSpi.getOperatorAlias(WriteOp.class))) {
                Xpp3Dom config = n.getConfiguration();
                Xpp3Dom fileParam = config.getChild("file");
                String filePath = fileParam.getValue();
                if(filePath != null && !filePath.isEmpty()) {
                    File file = new File(filePath);
                    if(file.exists())
                        fileList.add(file);
                }
            }
        }
        return fileList;
    }

    static class ProductSetNode {
        String nodeID;
        Vector<String> fileList = new Vector<String>(10);
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
