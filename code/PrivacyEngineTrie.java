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
    int priority = -1;

    void insert(String path, String action, int newPriority) {
        String[] segments = path.split("/");
        PrivacyTrieNode current = this;
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            current = current.children.computeIfAbsent(segment, k -> new PrivacyTrieNode());
        }
        if (newPriority >= current.priority) {
            current.action = action;
            current.priority = newPriority;
        }
    }

    PrivacyTrieNode getChild(String segment) {
        if (children.containsKey(segment)) return children.get(segment);
        // Supports wildcard rules in the Trie
        return children.get("*");
    }
}

public class PrivacyEngineTrie {

    public static void main(String[] args) throws Exception {
        String dataFile = "../data/corona-dataset.xml";
        String ruleFile = "../policies/corona-policy.xml";
        String queryXPath = "/dataset/*/n_patient_info"; // Example path
        String userRole = "MANAGER";

        Document dataDoc = loadXML(dataFile);
        Document ruleDoc = loadXML(ruleFile);

        Document filteredDoc = processQuery(dataDoc, ruleDoc, queryXPath, userRole);
        writeXML(filteredDoc, "../data/filtered-output.xml");

        System.out.println("Processing complete.");
    }

    public static Document processQuery(Document dataDoc, Document ruleDoc, String queryXPath, String userRole) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        PrivacyTrieNode rootRule = buildTrie(ruleDoc, userRole);

        NodeList queryNodes = (NodeList) xpath.evaluate(queryXPath, dataDoc, XPathConstants.NODESET);
        Document outputDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element resultRoot = outputDoc.createElement("result");
        outputDoc.appendChild(resultRoot);

        for (int i = 0; i < queryNodes.getLength(); i++) {
            Node sourceNode = queryNodes.item(i);
            
            // Start resolution from the root of the path
            String fullPath = getFullPath(sourceNode);
            ResolvedState state = resolvePathState(rootRule, fullPath);

            // Even if action is 'refuse', we call filterNode because children might override it
            Node filtered = filterNode(sourceNode, outputDoc, state.node, state.action, state.priority);
            if (filtered != null) {
                resultRoot.appendChild(filtered);
            }
        }
        return outputDoc;
    }

    private static Node filterNode(Node source, Document destDoc, PrivacyTrieNode ruleNode, String inheritedAction, int inheritedPriority) {
        String currentAction = inheritedAction;
        int currentPriority = inheritedPriority;

        // Check if this specific node has a priority override
        if (ruleNode != null && ruleNode.action != null) {
            if (ruleNode.priority >= currentPriority) {
                currentAction = ruleNode.action;
                currentPriority = ruleNode.priority;
            }
        }

        Node newNode = null;
        boolean hasAllowedChildren = false;
        List<Node> filteredChildren = new ArrayList<>();

        // Process Children first to see if this branch is "saved"
        NodeList children = source.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                PrivacyTrieNode nextTrie = (ruleNode != null) ? ruleNode.getChild(child.getNodeName()) : null;
                Node filteredChild = filterNode(child, destDoc, nextTrie, currentAction, currentPriority);
                if (filteredChild != null) {
                    filteredChildren.add(filteredChild);
                    hasAllowedChildren = true;
                }
            } else if (child.getNodeType() == Node.TEXT_NODE && "clear".equals(currentAction)) {
                // Only keep text if the current node itself is clear
                filteredChildren.add(destDoc.importNode(child, true));
            }
        }

        // Logic for returning the node
        if ("refuse".equals(currentAction)) {
            if (hasAllowedChildren) {
                // "Ghost" node: Refused itself, but contains allowed children (like n_residental_info)
                newNode = destDoc.createElement(source.getNodeName());
                for (Node fc : filteredChildren) newNode.appendChild(fc);
                return newNode;
            } else {
                return null; // Truly refused
            }
        } else if ("mask".equals(currentAction)) {
            newNode = destDoc.createElement(source.getNodeName());
            if (isLeafElement(source)) {
                newNode.setTextContent("*****");
            } else {
                for (Node fc : filteredChildren) newNode.appendChild(fc);
            }
            return newNode;
        } else {
            // "clear"
            newNode = destDoc.importNode(source, false);
            for (Node fc : filteredChildren) newNode.appendChild(fc);
            return newNode;
        }
    }

    // --- Helper Logic ---

    private static class ResolvedState {
        PrivacyTrieNode node;
        String action;
        int priority;
        ResolvedState(PrivacyTrieNode n, String a, int p) { node = n; action = a; priority = p; }
    }

    private static ResolvedState resolvePathState(PrivacyTrieNode root, String path) {
        PrivacyTrieNode current = root;
        String action = "clear";
        int priority = -1;
        for (String seg : path.split("/")) {
            if (seg.isEmpty()) continue;
            if (current != null) {
                current = current.getChild(seg);
                if (current != null && current.action != null && current.priority >= priority) {
                    action = current.action;
                    priority = current.priority;
                }
            }
        }
        return new ResolvedState(current, action, priority);
    }

    private static PrivacyTrieNode buildTrie(Document ruleDoc, String userRole) {
        PrivacyTrieNode root = new PrivacyTrieNode();
        NodeList rules = ruleDoc.getElementsByTagNameNS("*", "rule");
        for (int i = 0; i < rules.getLength(); i++) {
            Element rule = (Element) rules.item(i);
            if (roleMatches(rule, userRole)) {
                String path = getChildText(rule, "path");
                int priority = Integer.parseInt(rule.getAttribute("priority"));
                String action = ((Element)rule.getElementsByTagNameNS("*", "action").item(0)).getAttribute("type");
                root.insert(path, action, priority);
            }
        }
        return root;
    }

    private static boolean roleMatches(Element rule, String userRole) {
        NodeList roles = rule.getElementsByTagNameNS("*", "role");
        for (int i = 0; i < roles.getLength(); i++) 
            if (roles.item(i).getTextContent().equalsIgnoreCase(userRole)) return true;
        return false;
    }

    private static String getChildText(Element parent, String tag) {
        NodeList l = parent.getElementsByTagNameNS("*", tag);
        return (l.getLength() > 0) ? l.item(0).getTextContent() : "";
    }

    private static String getFullPath(Node node) {
        if (node == null || node.getNodeType() == Node.DOCUMENT_NODE) return "";
        return getFullPath(node.getParentNode()) + "/" + node.getNodeName();
    }

    private static boolean isLeafElement(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) return false;
        return true;
    }

    private static Document loadXML(String path) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(new File(path));
    }

    private static void writeXML(Document doc, String path) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.transform(new DOMSource(doc), new StreamResult(new File(path)));
    }
}