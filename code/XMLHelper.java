import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XMLHelper 
{
    // Check if rule applies to given role
    public boolean roleMatches(Element rule, String userRole) 
    {
        NodeList roles = rule.getElementsByTagNameNS("*", "role");

        for (int i = 0; i < roles.getLength(); i++) {
            if (roles.item(i).getTextContent().equalsIgnoreCase(userRole))
                return true;
        }
        return false;
    }

    // Get text content of a child element
    public String getChildText(Element parent, String tag) 
    {
        NodeList l = parent.getElementsByTagNameNS("*", tag);
        return (l.getLength() > 0) ? l.item(0).getTextContent() : "";
    }

    // Build full XPath-like path of a node
    public String getFullPath(Node node) 
    {
        if (node == null || node.getNodeType() == Node.DOCUMENT_NODE) return "";
        return getFullPath(node.getParentNode()) + "/" + node.getNodeName();
    }

    // Check if node is a leaf (no element children)
    public boolean isLeafElement(Node node) 
    {
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++)
        {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE)
                return false;
        }

        return true;
    }

    // Load XML file into Document
    public Document loadXML(String path) throws Exception 
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(new File(path));
    }

    // Write Document to XML file
    public void writeXML(Document doc, String path) throws Exception 
    {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.transform(new DOMSource(doc), new StreamResult(new File(path)));
    }
}
