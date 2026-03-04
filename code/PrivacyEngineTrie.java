import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.*;
import java.util.*;

class PrivacyTrieNode {
    Map<String, PrivacyTrieNode> children = new HashMap<>();
    String action = null;

    void insert(String path, String action) {
        String[] segments = path.split("/");
        PrivacyTrieNode current = this;
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            current = current.children.computeIfAbsent(segment, k -> new PrivacyTrieNode());
        }
        current.action = action;
    }

    PrivacyTrieNode getChild(String segment) {
        if (children.containsKey(segment)) return children.get(segment);
        return children.get("*"); 
    }
}

public class PrivacyEngineTrie {

    // Helper class to track inheritance
    private static class PrivacyState {
        PrivacyTrieNode trieNode;
        String action;
        PrivacyState(PrivacyTrieNode node, String action) {
            this.trieNode = node;
            this.action = action;
        }
    }

    public static void main(String[] args) throws Exception {

        String dataFile = "../data/corona-dataset.xml";
        String ruleFile = "../policies/corona-policy.xml";
        String queryXPath = "/dataset/*/n_patient_info";
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

    public static Document processQuery(Document dataDoc, Document ruleDoc, 
                                       String queryXPath, String userRole) throws Exception {
        
        XPath xpath = XPathFactory.newInstance().newXPath();

        // 1. Build Rule Trie
        PrivacyTrieNode rootRule = new PrivacyTrieNode();
        NodeList rules = ruleDoc.getElementsByTagNameNS("*", "rule");
        for (int i = 0; i < rules.getLength(); i++) {
            Element rule = (Element) rules.item(i);
            if (roleMatches(rule, userRole)) {
                String path = getChildText(rule, "path");
                Element actionEl = (Element) rule.getElementsByTagNameNS("*", "action").item(0);
                rootRule.insert(path, actionEl.getAttribute("type"));
            }
        }

        NodeList queryNodes = (NodeList) xpath.evaluate(queryXPath, dataDoc, XPathConstants.NODESET);
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document outputDoc = factory.newDocumentBuilder().newDocument();
        Element resultRoot = outputDoc.createElement("result");
        outputDoc.appendChild(resultRoot);

        for (int i = 0; i < queryNodes.getLength(); i++) {
            Node sourceNode = queryNodes.item(i);
            
            // 2. Resolve initial state for the query result (Inheritance from ancestors)
            PrivacyState state = resolvePathState(rootRule, getFullPath(sourceNode));

            // 3. If parent is refused, skip. If masked, children will be masked.
            if (!"refuse".equals(state.action)) {
                Node filtered = filterNode(sourceNode, outputDoc, state.trieNode, state.action);
                if (filtered != null) resultRoot.appendChild(filtered);
            }
        }

        return outputDoc;
    }

    private static Node filterNode(Node source, Document destDoc, PrivacyTrieNode ruleNode, String currentAction) {
        // Update action if a specific rule exists for this node
        String effectiveAction = (ruleNode != null && ruleNode.action != null) ? ruleNode.action : currentAction;

        if ("refuse".equals(effectiveAction)) return null;

        Node newNode = destDoc.importNode(source, false);

        // STICKY MASKING: If this node or any ancestor is masked
        if ("mask".equals(effectiveAction)) {
            // If it's a leaf (text container), mask it
            if (isLeafElement(source)) {
                newNode.setTextContent("*****");
                return newNode;
            }
        }

        NodeList children = source.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                // Find next trie node, or null if we've moved past defined rules
                PrivacyTrieNode nextTrie = (ruleNode != null) ? ruleNode.getChild(child.getNodeName()) : null;
                
                Node filteredChild = filterNode(child, destDoc, nextTrie, effectiveAction);
                if (filteredChild != null) newNode.appendChild(filteredChild);
            } else if (child.getNodeType() == Node.TEXT_NODE && !"mask".equals(effectiveAction)) {
                // Only copy real text if not masked
                newNode.appendChild(destDoc.importNode(child, true));
            }
        }
        return newNode;
    }

    private static PrivacyState resolvePathState(PrivacyTrieNode root, String path) {
        PrivacyTrieNode current = root;
        String action = "clear";
        String[] segments = path.split("/");
        
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            if (current != null) {
                current = current.getChild(segment);
                if (current != null && current.action != null) {
                    action = current.action; // Rule found at this level
                }
            }
            if ("refuse".equals(action)) break; 
        }
        return new PrivacyState(current, action);
    }

    // --- Helper Utilities ---
    private static boolean roleMatches(Element rule, String userRole) {
        NodeList roles = rule.getElementsByTagNameNS("*", "role");
        for (int i = 0; i < roles.getLength(); i++) {
            if (roles.item(i).getTextContent().equalsIgnoreCase(userRole)) return true;
        }
        return false;
    }

    private static String getChildText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagNameNS("*", tagName);
        return (list.getLength() > 0) ? list.item(0).getTextContent() : "";
    }

    private static String getFullPath(Node node) {
        if (node == null || node.getNodeType() == Node.DOCUMENT_NODE) return "";
        return getFullPath(node.getParentNode()) + "/" + node.getNodeName();
    }

    private static boolean isLeafElement(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) return false;
        }
        return true;
    }
}