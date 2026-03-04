import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.File;
import java.util.*;



public class PrivacyEngine {

    public static void main(String[] args) throws Exception {

        String dataFile = "../data/corona-dataset.xml";
        String ruleFile = "../policies/corona-policy.xml";
        String queryXPath = "/dataset/n_44/n_patient_info";
        String userRole = "INTERN";

        Document dataDoc = loadXML(dataFile);
        Document ruleDoc = loadXML(ruleFile);

        Document filteredDoc =
        processQuery(dataDoc, ruleDoc, queryXPath, userRole);
        writeXML(filteredDoc, "../data/filtered-output.xml");

        System.out.println("Filtered document written to filtered-output.xml");
    }

    private static Document loadXML(String filePath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new File(filePath));
    }

    private static void writeXML(Document doc, String filePath) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(doc), new StreamResult(new File(filePath)));
    }

    private static Document processQuery(Document dataDoc,
                                     Document ruleDoc,
                                     String queryXPath,
                                     String userRole) throws Exception {

        XPathFactory xpf = XPathFactory.newInstance();
        XPath xpath = xpf.newXPath();

        // Step 1: Extract Subtree (based on query)
        NodeList queryNodes = (NodeList) xpath.evaluate(queryXPath, dataDoc, XPathConstants.NODESET);
        //these are the subtree nodes that the client is interested in

        if (queryNodes.getLength() == 0) {
            System.out.println("No matching query nodes found.");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document outputDoc = builder.newDocument();

            // Create a root element for output and return an empty doc
            Element root = outputDoc.createElement("result");
            root.getElementsByTagName("result").item(0).setTextContent("No result corresponding to query");
            outputDoc.appendChild(root);
            return outputDoc;//empty output doc
        }

        // Step 2: Parse rules
        NodeList rules = ruleDoc.getElementsByTagNameNS(
                "http://yourcompany.com/privacy-rules", "rule");

        // Step 3: Apply rules
        for (int i = 0; i < rules.getLength(); i++) {

            Element rule = (Element) rules.item(i);

            String path = getTextContent(rule, "path");//the path to which the rule applies to 
            String actionType = ((Element) rule.getElementsByTagNameNS(
                    "http://yourcompany.com/privacy-rules", "action")
                    .item(0)).getAttribute("type");

            if (!roleMatches(rule, userRole)) {
                continue;
            }

            NodeList matchedNodes = null;

            for (int q = 0; q < queryNodes.getLength(); q++) {

                Node queryNode = queryNodes.item(q);
                
                //Not sure if this will work in all cases but for now, we'll use this
                String relative_path = path.replace(queryXPath, "/");
                matchedNodes =
                    (NodeList) xpath.evaluate(relative_path, queryNode, XPathConstants.NODESET);

                
                    for (int j = 0; j < matchedNodes.getLength(); j++) {
                    Node node = matchedNodes.item(j);

                    if (actionType.equals("refuse")) {
                        removeSubtree(node);
                    } else if (actionType.equals("mask")) {
                        maskSubtree(node);
                    }
                }
            }
        }

        // Create new empty document for output
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document outputDoc = builder.newDocument();

        // Create a root element for output
        Element root = outputDoc.createElement("result");
        outputDoc.appendChild(root);

        // Import each filtered queryNode into new document
        for (int i = 0; i < queryNodes.getLength(); i++) {
            Node filteredNode = queryNodes.item(i);

            Node importedNode = outputDoc.importNode(filteredNode, true);
            root.appendChild(importedNode);
        }

        return outputDoc;
    }

    private static boolean roleMatches(Element rule, String userRole) {

        NodeList roles = rule.getElementsByTagNameNS(
                "http://yourcompany.com/privacy-rules", "role");

        for (int i = 0; i < roles.getLength(); i++) {
            if (roles.item(i).getTextContent().equals(userRole)) {
                return true;
            }
        }
        return false;
    }

    private static String getTextContent(Element parent, String tagName) {

        NodeList nodes = parent.getElementsByTagNameNS(
                "http://yourcompany.com/privacy-rules", tagName);

        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    private static void removeSubtree(Node node) {

        Node parent = node.getParentNode();
        if (parent != null) {
            parent.removeChild(node);
        }
    }

    private static void maskSubtree(Node node) {

        if (node.getNodeType() == Node.TEXT_NODE) {
            node.setTextContent("****");
            return;
        }

        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            maskSubtree(children.item(i));
        }
    }

    //helps in debugging
    public static void printNode(Node node) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        transformer.transform(new DOMSource(node), new StreamResult(System.out));
    }
    // System.out.print("----------------------------------\n");
    // printNode(queryNode);
    // System.out.print("----------------------------------\n");
}