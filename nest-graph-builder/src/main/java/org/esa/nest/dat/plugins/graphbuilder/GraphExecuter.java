package org.esa.nest.dat.plugins.graphbuilder;

import com.bc.ceres.binding.dom.DomElement;
import com.bc.ceres.binding.dom.Xpp3DomElement;
import com.bc.ceres.core.ProgressMonitor;
import com.thoughtworks.xstream.io.xml.xppdom.Xpp3Dom;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.OperatorSpiRegistry;
import org.esa.beam.framework.gpf.OperatorUI;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.graph.*;
import org.esa.beam.framework.gpf.operators.common.ReadOp;
import org.esa.beam.framework.gpf.operators.common.WriteOp;
import org.esa.nest.gpf.ProductSetReaderOp;
import org.esa.nest.util.ResourceUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Set;
import java.util.StringTokenizer;

public class GraphExecuter extends Observable {

    private final GPF gpf;
    private Graph graph;
    private GraphContext graphContext = null;
    private final GraphProcessor processor;
    private String graphDescription = "";

    private int idCount = 0;
    private final ArrayList<GraphNode> nodeList = new ArrayList<GraphNode>();
    private final ArrayList<GraphNode> savedProductSetList = new ArrayList<GraphNode>();

    public enum events { ADD_EVENT, REMOVE_EVENT, SELECT_EVENT }

    public GraphExecuter() {

        gpf = GPF.getDefaultInstance();
        gpf.getOperatorSpiRegistry().loadOperatorSpis();

        graph = new Graph("Graph");
        processor = new GraphProcessor();
    }

    public ArrayList<GraphNode> GetGraphNodes() {
        return nodeList;
    }

    public void ClearGraph() {
        graph = null;
        graph = new Graph("Graph");
        nodeList.clear();
        idCount = 0;
    }

    public GraphNode findGraphNode(String id) {
        for(GraphNode n : nodeList) {
            if(n.getID().equals(id)) {
                return n;
            }
        }
        return null;
    }

    public GraphNode findGraphNodeByOperator(String operatorName) {
        for(GraphNode n : nodeList) {
            if(n.getOperatorName().equals(operatorName)) {
                return n;
            }
        }
        return null;
    }

    public void setSelectedNode(GraphNode node) {
        if(node == null) return;
        setChanged();
        notifyObservers(new GraphEvent(events.SELECT_EVENT, node));
        clearChanged();
    }

    /**
     * Gets the list of operators
     * @return set of operator names
     */
    public Set<String> GetOperatorList() {
        return gpf.getOperatorSpiRegistry().getAliases();
    }

    public boolean isOperatorInternal(String alias) {
        final OperatorSpiRegistry registry = gpf.getOperatorSpiRegistry();
        final OperatorSpi operatorSpi = registry.getOperatorSpi(alias);
        final OperatorMetadata operatorMetadata = operatorSpi.getOperatorClass().getAnnotation(OperatorMetadata.class);
        return !(operatorMetadata != null && !operatorMetadata.internal());
    }

    public String getOperatorCategory(String alias) {
        final OperatorSpiRegistry registry = gpf.getOperatorSpiRegistry();
        final OperatorSpi operatorSpi = registry.getOperatorSpi(alias);
        final OperatorMetadata operatorMetadata = operatorSpi.getOperatorClass().getAnnotation(OperatorMetadata.class);
        if(operatorMetadata != null)
            return operatorMetadata.category();
        return "";
    }

    public GraphNode addOperator(final String opName) {

        final String id = "" + ++idCount + '-' + opName;
        final GraphNode newGraphNode = createNewGraphNode(opName, id);

        setChanged();
        notifyObservers(new GraphEvent(events.ADD_EVENT, newGraphNode));
        clearChanged();

        return newGraphNode;
    }

    private GraphNode createNewGraphNode(final String opName, final String id) {
        final Node newNode = new Node(id, opName);

        final Xpp3DomElement parameters = new Xpp3DomElement("parameters");
        newNode.setConfiguration(parameters);

        graph.addNode(newNode);

        final GraphNode newGraphNode = new GraphNode(newNode);
        nodeList.add(newGraphNode);

        newGraphNode.setOperatorUI(CreateOperatorUI(newGraphNode.getOperatorName()));

        return newGraphNode;
    }

    private static OperatorUI CreateOperatorUI(final String operatorName) {
        final OperatorSpi operatorSpi = GPF.getDefaultInstance().getOperatorSpiRegistry().getOperatorSpi(operatorName);
        if (operatorSpi == null) {
            return null;
        }

        return operatorSpi.createOperatorUI();
    }

    public void removeOperator(final GraphNode node) {

        setChanged();
        notifyObservers(new GraphEvent(events.REMOVE_EVENT, node));
        clearChanged();

        removeNode(node);
    }

    private void removeNode(final GraphNode node) {
        // remove as a source from all nodes
        for(GraphNode n : nodeList) {
            n.disconnectOperatorSources(node.getID());
        }

        graph.removeNode(node.getID());
        nodeList.remove(node);
    }

    public void setOperatorParam(final String id, final String paramName, final String value) {
        final Node node = graph.getNode(id);
        DomElement xml = node.getConfiguration().getChild(paramName);
        if(xml == null) {
            xml = new Xpp3DomElement(paramName);
            node.getConfiguration().addChild(xml);
        }
        xml.setValue(value);
    }

    private void AssignAllParameters() {

        final Xpp3Dom presentationXML = new Xpp3Dom("Presentation");

        // save graph description
        final Xpp3Dom descXML = new Xpp3Dom("Description");
        descXML.setValue(graphDescription);
        presentationXML.addChild(descXML);

        for(GraphNode n : nodeList) {
            if(n.GetOperatorUI() != null) {
                n.AssignParameters(presentationXML);
            }
        }

        graph.setAppData("Presentation", presentationXML);
    }

    boolean IsGraphComplete() {
        int nodesWithoutSources = 0;
        for(GraphNode n : nodeList) {
            if(!n.HasSources()) {
                ++nodesWithoutSources;
                if(!IsNodeASource(n))
                    return false;
            }
        }
        return nodesWithoutSources != nodeList.size();
    }

    private boolean IsNodeASource(final GraphNode sourceNode) {
        for(GraphNode n : nodeList) {
            if(n.isNodeSource(sourceNode))
                return true;
        }
        return false;
    }

    private GraphNode[] findConnectedNodes(final GraphNode sourceNode) {
        final ArrayList<GraphNode> connectedNodes = new ArrayList<GraphNode>();
        for(GraphNode n : nodeList) {
            if(n.isNodeSource(sourceNode))
                connectedNodes.add(n);
        }
        return connectedNodes.toArray(new GraphNode[connectedNodes.size()]);
    }

    public boolean InitGraph() throws GraphException {
        if(IsGraphComplete()) {
            AssignAllParameters();
            replaceProductSetReaders();

            try {
                recreateGraphContext();
                updateGraphNodes();
            } finally {
                restoreProductSetReaders();
            }
            return true;
        }
        return false;
    }

    private void recreateGraphContext() throws GraphException {
        if(graphContext != null)
            GraphProcessor.disposeGraphContext(graphContext);

        graphContext = processor.createGraphContext(graph, ProgressMonitor.NULL);
    }

    private void updateGraphNodes() {
        if(graphContext != null) {
            for(GraphNode n : nodeList) {
                final NodeContext context = graphContext.getNodeContext(n.getNode());
                n.setSourceProducts(context.getSourceProducts());
            }
        }
    }

    public void disposeGraphContext() {
        GraphProcessor.disposeGraphContext(graphContext);
    }

    /**
     * Begins graph processing
     * @param pm The ProgressMonitor
     */
    public void executeGraph(ProgressMonitor pm) {
        processor.executeGraphContext(graphContext, pm);
    }

    void saveGraph() throws GraphException {

        final File filePath = ResourceUtils.GetFilePath("Save Graph", "XML", "xml", "Graph", true);
        if(filePath != null)
            writeGraph(filePath.getAbsolutePath());
    }

    private void writeGraph(final String filePath) throws GraphException {

        try {
            final FileWriter fileWriter = new FileWriter(filePath);

            try {
                GraphIO.write(graph, fileWriter);
            } finally {
                fileWriter.close();
            }
        } catch(IOException e) {
            throw new GraphException("Unable to write graph to " + filePath);
        }
    }

    public void loadGraph(final File filePath, final boolean addUI) throws GraphException {

        try {
            if(filePath == null) return;
            final FileReader fileReader = new FileReader(filePath.getAbsolutePath());
            Graph graphFromFile;
            try {
                graphFromFile = GraphIO.read(fileReader, null);
            } finally {
                fileReader.close();
            }

            if(graphFromFile != null) {
                graph = graphFromFile;
                nodeList.clear();

                final Xpp3Dom presentationXML = graph.getApplicationData("Presentation");
                if(presentationXML != null) {
                    // get graph description
                    final Xpp3Dom descXML = presentationXML.getChild("Description");
                    if(descXML != null && descXML.getValue() != null) {
                        graphDescription = descXML.getValue();
                    }
                }

                final Node[] nodes = graph.getNodes();
                for (Node n : nodes) {
                    final GraphNode newGraphNode = new GraphNode(n);
                    if(presentationXML != null)
                        newGraphNode.setDisplayParameters(presentationXML);
                    nodeList.add(newGraphNode);

                    if(addUI)
                        newGraphNode.setOperatorUI(CreateOperatorUI(newGraphNode.getOperatorName()));

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

    public String getGraphDescription() {
        return graphDescription;
    }

    public void setGraphDescription(final String text) {
        graphDescription = text;
    }

    private ProductSetData[] findProductSets(final String readerName) {
        final String SEPARATOR = ",";
        final String SEPARATOR_ESC = "\\u002C"; // Unicode escape repr. of ','
        final ArrayList<ProductSetData> productSetDataList = new ArrayList<ProductSetData>();

        for(Node n : graph.getNodes()) {
            if(n.getOperatorName().equalsIgnoreCase(readerName)) {
                final ProductSetData psData = new ProductSetData();
                psData.nodeID = n.getId();

                final DomElement config = n.getConfiguration();
                final DomElement[] params = config.getChildren();
                for(DomElement p : params) {
                    if(p.getName().equals("fileList") && p.getValue() != null) {

                        final StringTokenizer st = new StringTokenizer(p.getValue(), SEPARATOR);
                        int length = st.countTokens();
                        for (int i = 0; i < length; i++) {
                            final String str = st.nextToken().replace(SEPARATOR_ESC, SEPARATOR);
                            psData.fileList.add(str);
                        }
                        break;
                    }
                }
                productSetDataList.add(psData);
            }
        }
        return productSetDataList.toArray(new ProductSetData[productSetDataList.size()]);
    }

    private void replaceProductSetReaders() {
        final ProductSetData[] productSetDataList =
                findProductSets(OperatorSpi.getOperatorAlias(ProductSetReaderOp.class));
        savedProductSetList.clear();

        int cnt = 0;
        for(ProductSetData psData : productSetDataList) {
            final GraphNode sourceNode = findGraphNode(psData.nodeID);
            for(String filePath : psData.fileList) {

                replaceProductSetWithReaders(sourceNode, "inserted--"+sourceNode.getID()+"--"+ cnt++, filePath);
            }
            if(!psData.fileList.isEmpty()) {
                removeNode(sourceNode);
                savedProductSetList.add(sourceNode);
            }
        }
    }

    private void restoreProductSetReaders() {
        for(GraphNode multiSrcNode : savedProductSetList) {

            final ArrayList<GraphNode> nodesToRemove = new ArrayList<GraphNode>();
            for(GraphNode n : nodeList) {
                final String id = n.getID();
                if(id.startsWith("inserted") && id.contains(multiSrcNode.getID())) {

                    switchConnections(n, multiSrcNode.getID());
                    nodesToRemove.add(n);
                }
            }
            for(GraphNode r : nodesToRemove) {
                removeNode(r);
            }

            nodeList.add(multiSrcNode);
            graph.addNode(multiSrcNode.getNode());
        }
    }

    private void replaceProductSetWithReaders(final GraphNode sourceNode, final String id, final String value) {

        GraphNode newReaderNode = createNewGraphNode(OperatorSpi.getOperatorAlias(ReadOp.class), id);
        newReaderNode.setOperatorUI(null);
        newReaderNode.getNode().getConfiguration();
        final DomElement config = newReaderNode.getNode().getConfiguration();
        final DomElement fileParam = new Xpp3DomElement("file");
        fileParam.setValue(value);
        config.addChild(fileParam);

        switchConnections(sourceNode, newReaderNode.getID());
    }

    private void switchConnections(final GraphNode oldNode, final String newNodeID) {
        final GraphNode[] connectedNodes = findConnectedNodes(oldNode);
        for(GraphNode node : connectedNodes) {
            node.connectOperatorSource(newNodeID);
        }
    }

    public ArrayList<File> getProductsToOpenInDAT() {
        final ArrayList<File> fileList = new ArrayList<File>(2);
        final Node[] nodes = graph.getNodes();
        for(Node n : nodes) {
            if(n.getOperatorName().equalsIgnoreCase(OperatorSpi.getOperatorAlias(WriteOp.class))) {
                final DomElement config = n.getConfiguration();
                final DomElement fileParam = config.getChild("file");
                final String filePath = fileParam.getValue();
                if(filePath != null && !filePath.isEmpty()) {
                    final File file = new File(filePath);
                    if(file.exists())
                        fileList.add(file);
                }
            }
        }
        return fileList;
    }

    private static class ProductSetData {
        String nodeID = null;
        final ArrayList<String> fileList = new ArrayList<String>(10);
    }

    public static class GraphEvent {

        private final events  eventType;
        private final Object  data;

        GraphEvent(events type, Object d) {
            eventType = type;
            data = d;
        }

        public Object getData() {
            return data;
        }

        public events getEventType() {
            return eventType;
        }
    }
}
