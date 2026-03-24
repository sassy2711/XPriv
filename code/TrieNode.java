import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

// Trie node used to store privacy rules hierarchically
public class TrieNode 
{
    // Map of child nodes (key = XML tag name or "*")
    private Map<String, TrieNode> children = new HashMap<>();

    // Action for this node: "clear", "mask", or "refuse"
    public String action = null;

    // Priority of the rule (higher overrides lower)
    public int priority = -1;

    // Insert a rule path into the Trie
    public void insert(String path, String action, int newPriority) 
    {
        String[] segments = path.split("/");
        TrieNode current = this;

        // Traverse/create nodes for each segment
        for (String segment : segments) 
        {
            if (segment.isEmpty()) 
                continue;
            
            current = current.children.computeIfAbsent(segment, k -> new TrieNode());
        }

        // Apply rule only if priority is higher
        if (newPriority >= current.priority) 
        {
            current.action = action;
            current.priority = newPriority;
        }
    }

    // Get child node (supports wildcard "*")
    public TrieNode getChild(String segment) 
    {
        if (children.containsKey(segment)) 
            return children.get(segment);
        
        return children.get("*"); // fallback to wildcard rule
    }

    // Build Trie from policy rules
    public static TrieNode buildTrie(Document ruleDoc, String userRole, XMLHelper xmlHelper) 
    {
        TrieNode root = new TrieNode();

        NodeList rules = ruleDoc.getElementsByTagNameNS("*", "rule");

        for (int i = 0; i < rules.getLength(); i++) 
        {
            Element rule = (Element) rules.item(i);

            // Only include rules matching user role
            if (xmlHelper.roleMatches(rule, userRole)) 
            {
                String path = xmlHelper.getChildText(rule, "path");

                int priority = Integer.parseInt(rule.getAttribute("priority"));

                String action = ((Element) rule.getElementsByTagNameNS("*", "action").item(0))
                        .getAttribute("type");

                root.insert(path, action, priority);
            }
        }

        return root;
    }
}