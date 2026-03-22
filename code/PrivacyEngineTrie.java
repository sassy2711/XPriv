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
    int priority = -1; // Tracks the importance of the stored action

    /**
     * Inserts a path into the Trie. 
     * If a rule already exists at this node, it is only overwritten 
     * if the newPriority is >= the existing priority.
     */
    void insert(String path, String action, int newPriority) {
        String[] segments = path.split("/");
        PrivacyTrieNode current = this;
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            current = current.children.computeIfAbsent(segment, k -> new PrivacyTrieNode());
        }
        
        // Priority Logic: Higher (or equal) priority takes precedence
        if (newPriority >= current.priority) {
            current.action = action;
            current.priority = newPriority;
        }
    }

    PrivacyTrieNode getChild(String segment) {
        if (children.containsKey(segment)) return children.get(segment);
        return children.get("*"); 
    }
}

public class PrivacyEngineTrie {

    private static class PrivacyState {
        PrivacyTrieNode trieNode;
        String action;
        PrivacyState(PrivacyTrieNode node, String action) {
            this.trieNode = node;
            this.action = action;
        }
    }

    public static void main(String[] args) throws Exception {
        // Update these paths to match your local file structure
        String dataFile = "../data/corona-dataset.xml";
        String ruleFile = "../policies/corona-policy.xml";
        String queryXPath = "/dataset/*/n_patient_info";
        String userRole = "INTERN";

        Document dataDoc = loadXML(dataFile);
        Document ruleDoc = loadXML(ruleFile);

        Document filteredDoc = processQuery(dataDoc, ruleDoc, queryXPath, userRole);
        writeXML(filteredDoc, "../data/filtered-output.xml");

        System.out.println("Filtered document written successfully with Priority Logic.");
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
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(doc), new StreamResult(new File(filePath)));
    }

    public static Document processQuery(Document dataDoc, Document ruleDoc, 
                                       String queryXPath, String userRole) throws Exception {
        
        XPath xpath = XPathFactory.newInstance().newXPath();

        // 1. Build Rule Trie - Applying Priorities here
        PrivacyTrieNode rootRule = new PrivacyTrieNode();
        NodeList rules = ruleDoc.getElementsByTagNameNS("*", "rule");
        
        for (int i = 0; i < rules.getLength(); i++) {
            Element rule = (Element) rules.item(i);
            
            if (roleMatches(rule, userRole)) {
                String path = getChildText(rule, "path");
                
                // Get priority attribute, default to 0 if missing
                String prioAttr = rule.getAttribute("priority");
                int priority = (prioAttr != null && !prioAttr.isEmpty()) ? Integer.parseInt(prioAttr) : 0;
                
                Element actionEl = (Element) rule.getElementsByTagNameNS("*", "action").item(0);
                String actionType = actionEl.getAttribute("type");
                
                rootRule.insert(path, actionType, priority);
            }
        }

        // 2. Execute Query and Filter
        NodeList queryNodes = (NodeList) xpath.evaluate(queryXPath, dataDoc, XPathConstants.NODESET);
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document outputDoc = factory.newDocumentBuilder().newDocument();
        Element resultRoot = outputDoc.createElement("result");
        outputDoc.appendChild(resultRoot);

        for (int i = 0; i < queryNodes.getLength(); i++) {
            Node sourceNode = queryNodes.item(i);
            
            // Resolve initial state based on path (Inheritance)
            PrivacyState state = resolvePathState(rootRule, getFullPath(sourceNode));

            if (!"refuse".equals(state.action)) {
                Node filtered = filterNode(sourceNode, outputDoc, state.trieNode, state.action);
                if (filtered != null) resultRoot.appendChild(filtered);
            }
        }

        return outputDoc;
    }

    private static Node filterNode(Node source, Document destDoc, PrivacyTrieNode ruleNode, String currentAction) {
        // Priority is already pre-resolved in the Trie during insertion
        String effectiveAction = (ruleNode != null && ruleNode.action != null) ? ruleNode.action : currentAction;

        if ("refuse".equals(effectiveAction)) return null;

        Node newNode = destDoc.importNode(source, false);

        if ("mask".equals(effectiveAction)) {
            if (isLeafElement(source)) {
                newNode.setTextContent("*****");
                return newNode;
            }
        }

        NodeList children = source.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                PrivacyTrieNode nextTrie = (ruleNode != null) ? ruleNode.getChild(child.getNodeName()) : null;
                Node filteredChild = filterNode(child, destDoc, nextTrie, effectiveAction);
                if (filteredChild != null) newNode.appendChild(filteredChild);
            } else if (child.getNodeType() == Node.TEXT_NODE && !"mask".equals(effectiveAction)) {
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
                    action = current.action; 
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
        String path = getFullPath(node.getParentNode()) + "/" + node.getNodeName();
        return path;
    }

    private static boolean isLeafElement(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) return false;
        }
        return true;
    }
}