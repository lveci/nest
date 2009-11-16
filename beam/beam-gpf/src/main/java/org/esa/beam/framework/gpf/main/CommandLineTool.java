package org.esa.beam.framework.gpf.main;

import com.bc.ceres.binding.ConversionException;
import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.binding.dom.DefaultDomElement;
import com.bc.ceres.binding.dom.DomElement;
import com.bc.ceres.binding.dom.Xpp3DomElement;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.ParameterDescriptorFactory;
import org.esa.beam.framework.gpf.graph.Graph;
import org.esa.beam.framework.gpf.graph.GraphException;
import org.esa.beam.framework.gpf.graph.Node;
import org.esa.beam.framework.gpf.graph.NodeSource;
import org.esa.beam.framework.gpf.operators.common.ReadOp;
import org.esa.beam.framework.gpf.operators.common.WriteOp;

import javax.media.jai.JAI;

import java.io.File;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.*;

/**
 * The common command-line tool for the GPF.
 * For usage, see {@link org/esa/beam/framework/gpf/main/CommandLineUsage.txt}.
 */
class CommandLineTool {

    private final CommandLineContext commandLineContext;
    static final String TOOL_NAME = "gpt";
    static final String DEFAULT_TARGET_FILEPATH = "./target.dim";
    static final String DEFAULT_FORMAT_NAME = ProductIO.DEFAULT_FORMAT_NAME;

    static {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }

    /**
     * Constructs a new tool.
     */
    CommandLineTool() {
        this(new DefaultCommandLineContext());
    }

    /**
     * Constructs a new tool with the given context.
     *
     * @param commandLineContext The context used to run the tool.
     */
    CommandLineTool(CommandLineContext commandLineContext) {
        this.commandLineContext = commandLineContext;
    }

    void run(String[] args) throws Exception {

        CommandLineArgs lineArgs = new CommandLineArgs(args);
        try {
            lineArgs.parseArguments();

            if (lineArgs.isHelpRequested()) {
                if (lineArgs.getOperatorName() != null) {
                    commandLineContext.print(CommandLineUsage.getUsageTextForOperator(lineArgs.getOperatorName()));
                } else if (lineArgs.getGraphFilepath() != null) {
                    commandLineContext.print(CommandLineUsage.getUsageTextForGraph(lineArgs.getGraphFilepath(),
                                                                                   commandLineContext));
                } else {
                    commandLineContext.print(CommandLineUsage.getUsageText());
                }
                return;
            }

            run(lineArgs);
        } catch (Exception e) {
            if (lineArgs.isStackTraceDump()) {
                e.printStackTrace(System.err);
            }
            throw e;
        }
    }

    private void run(CommandLineArgs lineArgs) throws ValidationException, ConversionException, IOException, GraphException {
        JAI jaiInstance = JAI.getDefaultInstance();
        jaiInstance.getTileCache().setMemoryCapacity(lineArgs.getTileCacheCapacity());
        jaiInstance.getTileScheduler().setParallelism(Runtime.getRuntime().availableProcessors());
        
        if (lineArgs.getOperatorName() != null) {
            Map<String, Object> parameters = getParameterMap(lineArgs);
            Map<String, Product> sourceProducts = getSourceProductMap(lineArgs);
            String opName = lineArgs.getOperatorName();
            Product targetProduct = createOpProduct(opName, parameters, sourceProducts);
            String filePath = lineArgs.getTargetFilepath();
            String formatName = lineArgs.getTargetFormatName();
            writeProduct(targetProduct, filePath, formatName);
        } else if (lineArgs.getGraphFilepath() != null) {
            Map<String, String> sourceNodeIdMap = getSourceNodeIdMap(lineArgs);
            Map<String, String> templateMap = new TreeMap<String, String>(sourceNodeIdMap);
            if (lineArgs.getParameterFilepath() != null) {
                templateMap.putAll(readParameterFile(lineArgs.getParameterFilepath()));
            }
            templateMap.putAll(lineArgs.getParameterMap());
            Graph graph = readGraph(lineArgs.getGraphFilepath(), templateMap);
            Node lastNode = graph.getNode(graph.getNodeCount() - 1);
            SortedMap<String, String> sourceFilepathsMap = lineArgs.getSourceFilepathMap();
            String readOperatorAlias = OperatorSpi.getOperatorAlias(ReadOp.class);
            for (Entry<String, String> entry : sourceFilepathsMap.entrySet()) {
                String sourceId = entry.getKey();
                String sourceFilepath = entry.getValue();
                String sourceNodeId = sourceNodeIdMap.get(sourceId);
                if (graph.getNode(sourceNodeId) == null) {
                    
                    DomElement parameters = new DefaultDomElement("parameters");
                    parameters.createChild("file").setValue(sourceFilepath);

                    Node sourceNode = new Node(sourceNodeId, readOperatorAlias);
                    sourceNode.setConfiguration(parameters);

                    graph.addNode(sourceNode);
                }
            }
            String writeOperatorAlias = OperatorSpi.getOperatorAlias(WriteOp.class);
            if (!lastNode.getOperatorName().equals(writeOperatorAlias)) {

                DomElement parameters = new DefaultDomElement("parameters");
                parameters.createChild("file").setValue(lineArgs.getTargetFilepath());
                parameters.createChild("formatName").setValue(lineArgs.getTargetFormatName());

                Node targetNode = new Node("WriteProduct$" + lastNode.getId(), writeOperatorAlias);
                targetNode.addSource(new NodeSource("input", lastNode.getId()));
                targetNode.setConfiguration(parameters);

                graph.addNode(targetNode);
            }

            final ProductSetData[] productSetDataList = findProductSetStacks(graph, "ProductSet-Reader");

            if(productSetDataList.length != 0) {
                replaceAllProductSets(graph, productSetDataList);
                executeGraph(graph);
            } else {
                executeGraph(graph);
            }
        }
    }

    private static ProductSetData[] findProductSetStacks(final Graph graph, final String readerName) throws GraphException {
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
                    if(p.getName().equals("fileList")) {
                        if(p.getValue() == null)
                            throw new GraphException(readerName+" fileList is empty");

                        final StringTokenizer st = new StringTokenizer(p.getValue(), SEPARATOR);
                        final int length = st.countTokens();
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

    private static void replaceAllProductSets(final Graph graph, final ProductSetData[] productSetDataList)
                                              throws GraphException {
        int cnt = 0;
        for(ProductSetData psData : productSetDataList) {

            final Node psNode = graph.getNode(psData.nodeID);
            for(String filePath : psData.fileList) {

                ReplaceProductSetWithReaders(graph, psNode, "inserted--"+psNode.getId()+"--"+ cnt++, filePath);
            }
            if(!psData.fileList.isEmpty()) {
                for(Node n : graph.getNodes()) {
                    disconnectNodeSource(n, psNode.getId());        
                }
                graph.removeNode(psNode.getId());
            }
        }
    }

    private static void ReplaceProductSetWithReaders(final Graph graph, final Node psNode, final String id, String value) {

        final Node newNode = new Node(id, OperatorSpi.getOperatorAlias(ReadOp.class));
        final Xpp3DomElement config = new Xpp3DomElement("parameters");
        final Xpp3DomElement fileParam = new Xpp3DomElement("file");
        fileParam.setValue(value);
        config.addChild(fileParam);
        newNode.setConfiguration(config);

        graph.addNode(newNode);
        switchConnections(graph, newNode, psNode);
    }

    private static void switchConnections(final Graph graph, final Node newNode, final Node oldNode) {
        for(Node n : graph.getNodes()) {
            if(isNodeSource(n, oldNode)) {
                final NodeSource ns = new NodeSource("sourceProduct", newNode.getId());
                n.addSource(ns);
            }
        }
    }

    private static void disconnectNodeSource(final Node node, final String id) {
        for (NodeSource ns : node.getSources()) {
            if (ns.getSourceNodeId().equals(id)) {
                node.removeSource(ns);
            }
        }
    }

    private static boolean isNodeSource(final Node node, final Node source) {

        final NodeSource[] sources = node.getSources();
        for (NodeSource ns : sources) {
            if (ns.getSourceNodeId().equals(source.getId())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> getParameterMap(CommandLineArgs lineArgs) throws ValidationException, ConversionException {
        HashMap<String, Object> parameters = new HashMap<String, Object>();
        PropertyContainer container = ParameterDescriptorFactory.createMapBackedOperatorPropertyContainer(lineArgs.getOperatorName(), parameters);
        Map<String, String> parameterMap = lineArgs.getParameterMap();
        for (Entry<String, String> entry : parameterMap.entrySet()) {
            String paramName = entry.getKey();
            String paramValue = entry.getValue();
            final Property model = container.getProperty(paramName);
            if (model != null) {
                model.setValueFromText(paramValue);
            } else {
               throw new RuntimeException(String.format(
                       "Parameter '%s' is not known by operator '%s'", paramName, lineArgs.getOperatorName()));
            }
        }
        return parameters;
    }

    private Map<String, Product> getSourceProductMap(CommandLineArgs lineArgs) throws IOException {
        SortedMap<File, Product> fileToProductMap = new TreeMap<File, Product>();
        SortedMap<String, Product> productMap = new TreeMap<String, Product>();
        SortedMap<String, String> sourceFilepathsMap = lineArgs.getSourceFilepathMap();
        for (Entry<String, String> entry : sourceFilepathsMap.entrySet()) {
            String sourceId = entry.getKey();
            String sourceFilepath = entry.getValue();
            Product product = addProduct(sourceFilepath, fileToProductMap);
            productMap.put(sourceId, product);
        }
        return productMap;
    }

    private Product addProduct(String sourceFilepath, Map<File, Product> fileToProductMap) throws IOException {
        File sourceFile = new File(sourceFilepath).getCanonicalFile();
        Product product = fileToProductMap.get(sourceFile);
        if (product == null) {
            String s = sourceFile.getPath();
            product = readProduct(s);
            if (product == null) {
                throw new IOException("No approriate product reader found for " + sourceFile);
            }
            fileToProductMap.put(sourceFile, product);
        }
        return product;
    }

    private static Map<String, String> getSourceNodeIdMap(CommandLineArgs lineArgs) throws IOException {
        SortedMap<File, String> fileToNodeIdMap = new TreeMap<File, String>();
        SortedMap<String, String> nodeIdMap = new TreeMap<String, String>();
        SortedMap<String, String> sourceFilepathsMap = lineArgs.getSourceFilepathMap();
        for (Entry<String, String> entry : sourceFilepathsMap.entrySet()) {
            String sourceId = entry.getKey();
            String sourceFilepath = entry.getValue();
            String nodeId = addNodeId(sourceFilepath, fileToNodeIdMap);
            nodeIdMap.put(sourceId, nodeId);
        }
        return nodeIdMap;
    }

    private static String addNodeId(String sourceFilepath, Map<File, String> fileToNodeId) throws IOException {
        File sourceFile = new File(sourceFilepath).getCanonicalFile();
        String nodeId = fileToNodeId.get(sourceFile);
        if (nodeId == null) {
            nodeId = "ReadProduct$" + fileToNodeId.size();
            fileToNodeId.put(sourceFile, nodeId);
        }
        return nodeId;
    }

    public Product readProduct(String productFilepath) throws IOException {
        return commandLineContext.readProduct(productFilepath);
    }

    public void writeProduct(Product targetProduct, String filePath, String formatName) throws IOException {
        commandLineContext.writeProduct(targetProduct, filePath, formatName);
    }

    public Graph readGraph(String filepath, Map<String, String> parameterMap) throws IOException, GraphException {
        return commandLineContext.readGraph(filepath, parameterMap);
    }

    public void executeGraph(Graph graph) throws GraphException {
        commandLineContext.executeGraph(graph);
    }

    public Map<String, String> readParameterFile(String propertiesFilepath) throws IOException {
        return commandLineContext.readParameterFile(propertiesFilepath);
    }

    private Product createOpProduct(String opName, Map<String, Object> parameters, Map<String, Product> sourceProducts) throws OperatorException {
        return commandLineContext.createOpProduct(opName, parameters, sourceProducts);
    }

    private static class ProductSetData {
        String nodeID = null;
        final ArrayList<String> fileList = new ArrayList<String>();
    }
}
