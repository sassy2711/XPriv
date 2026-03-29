import org.w3c.dom.*; // DOM classes for XML handling
import javax.xml.parsers.*; // For parsing XML documents
import javax.xml.xpath.*; // For XPath queries
import java.util.*;

// Helper class to store resolved state
class ResolvedState 
{
    TrieNode node;
    String action;
    int priority;

    ResolvedState(TrieNode n, String a, int p) 
    {
        node = n;
        action = a;
        priority = p;
    }
}

public class PrivacyEngine
{
    private static XMLHelper xmlHelper;

    public static void main(String[] args) throws Exception 
    {
        xmlHelper = new XMLHelper();

        // Input dataset XML
        String dataFile = "../data/corona-dataset.xml";

        // Policy rules XML
        String ruleFile = "../policies/corona-policy.xml";

        // XPath query to select nodes
        String queryXPath = "/dataset/*/n_patient_info/n_residental_info";

        // User role for filtering
        String userRole = "MANAGER";

        // Load XML documents
        Document dataDoc = xmlHelper.loadXML(dataFile);
        Document ruleDoc = xmlHelper.loadXML(ruleFile);

        // Process query with privacy filtering
        Document filteredDoc = processQuery(dataDoc, ruleDoc, queryXPath, userRole);

        // Write output XML
        xmlHelper.writeXML(filteredDoc, "../data/filtered-output.xml");

        System.out.println("Processing complete.");
    }

    // Main processing function
    public static Document processQuery(Document dataDoc, Document ruleDoc, String queryXPath, String userRole) throws Exception 
    {
        XPath xpath = XPathFactory.newInstance().newXPath();

        // Build Trie from rules
        TrieNode rootRule = TrieNode.buildTrie(ruleDoc, userRole, xmlHelper);

        // Evaluate XPath query on data
        NodeList queryNodes = (NodeList) xpath.evaluate(queryXPath, dataDoc, XPathConstants.NODESET);
        //this is the subset of nodes the query is interested in

        // Create output document
        Document outputDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element resultRoot = outputDoc.createElement("result");
        outputDoc.appendChild(resultRoot);

        // Process each node returned by XPath
        for (int i = 0; i < queryNodes.getLength(); i++) 
        {
            Node sourceNode = queryNodes.item(i);

            // Get full path of node
            String fullPath = xmlHelper.getFullPath(sourceNode);

            // Resolve rule state (action + priority)
            ResolvedState state = resolvePathState(rootRule, fullPath);

            // Filter node based on rules
            Node filtered = filterNode(sourceNode, outputDoc, state.node, state.action, state.priority);

            // Add to result if not null
            if (filtered != null) 
            {
                resultRoot.appendChild(filtered);
            }
        }

        return outputDoc;
    }

    // Recursive function to filter nodes based on rules
    private static Node filterNode(Node source, Document destDoc, TrieNode ruleNode, String inheritedAction, int inheritedPriority) {
    //take in the source node, and the rule to be applied to this source node
        String currentAction = inheritedAction;
        int currentPriority = inheritedPriority;

        // Override with rule at this node if higher priority
        if (ruleNode != null && ruleNode.action != null) 
        {
            if (ruleNode.priority >= currentPriority) 
            {
                currentAction = ruleNode.action;
                currentPriority = ruleNode.priority;
            }
        }

        Node newNode = null;

        boolean hasAllowedChildren = false; // tracks if any child survives
        List<Node> filteredChildren = new ArrayList<>();

        // Process children first (bottom-up approach)
        NodeList children = source.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) 
        {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) 
            {
                // Move to next Trie node
                TrieNode nextTrie = (ruleNode != null) ? ruleNode.getChild(child.getNodeName()) : null;

                // Recursively filter child
                Node filteredChild = filterNode(child, destDoc, nextTrie, currentAction, currentPriority);

                if (filteredChild != null) 
                {
                    filteredChildren.add(filteredChild);
                    hasAllowedChildren = true;
                }

            } 
            
            else if (child.getNodeType() == Node.TEXT_NODE && "clear".equals(currentAction)) 
            {
                // Keep text only if action is "clear"
                filteredChildren.add(destDoc.importNode(child, true));
            }
        }

        // Apply action logic
        if ("refuse".equals(currentAction)) 
        {
            if (hasAllowedChildren) 
            {
                // Keep node as "ghost" if children are allowed
                newNode = destDoc.createElement(source.getNodeName());
                
                for (Node fc : filteredChildren) 
                    newNode.appendChild(fc);
                
                return newNode;
            } 
            
            else 
            {
                return null; // completely remove node
            }

        } 
        
        else if ("mask".equals(currentAction))
        {
            newNode = destDoc.createElement(source.getNodeName());

            // Mask leaf node content
            if (xmlHelper.isLeafElement(source)) 
            {
                newNode.setTextContent("*****");
            } 
            
            else 
            {
                for (Node fc : filteredChildren) 
                    newNode.appendChild(fc);
            }

            return newNode;
        } 
        
        else 
        {
            // "clear" => keep node as is
            newNode = destDoc.importNode(source, false);
       
            for (Node fc : filteredChildren) 
                newNode.appendChild(fc);
        
            return newNode;
        }
    }

    // Resolve action and priority for a given path
    private static ResolvedState resolvePathState(TrieNode root, String path) {
    //finds the highest priority rule applicable to the node
        TrieNode current = root;
        String action = "clear"; // default
        int priority = -1;

        for (String seg : path.split("/")) 
        {
            if (seg.isEmpty()) continue;

            if (current != null) 
            {
                current = current.getChild(seg);

                // Update action if higher priority rule found
                if (current != null && current.action != null && current.priority >= priority) 
                {
                    action = current.action;
                    priority = current.priority;
                }
            }
        }

        return new ResolvedState(current, action, priority);
    } 
}
