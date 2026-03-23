import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.*;

public class DynamicKAnonymity {

    public static void main(String[] args) throws Exception {
        int k = 2; // Threshold
        File inputFile = new File("../data/filtered-output.xml");
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(inputFile);
        
        // 1. Identify the 'Record' level (the immediate children of the root)
        Element root = doc.getDocumentElement();
        NodeList records = root.getChildNodes();
        
        // 2. Count occurrences of QI combinations
        Map<String, Integer> fingerprintCounts = new HashMap<>();
        List<Element> recordElements = new ArrayList<>();

        for (int i = 0; i < records.getLength(); i++) {
            if (records.item(i) instanceof Element) {
                Element record = (Element) records.item(i);
                recordElements.add(record);
                String fp = generateFingerprint(record);
                fingerprintCounts.put(fp, fingerprintCounts.getOrDefault(fp, 0) + 1);
            }
        }

        // 3. Anonymize records that fall below k
        for (Element record : recordElements) {
            String fp = generateFingerprint(record);
            if (fingerprintCounts.get(fp) < k) {
                // Apply Generalization: Here we mask the 'Religion' or 'Age' equivalent
                // Since schema is unknown, we target the last 2 child nodes for suppression
                suppressSpecificInfo(record);
            }
            // Always remove ID tags if they look like IDs (numeric/unique)
            stripPotentialIdentifiers(record);
        }

        // 4. Output the result
        saveXML(doc, "../data/anonymized_output.xml");
    }

    private static String generateFingerprint(Element record) {
        // Concatenates all leaf node values to create a unique signature
        StringBuilder sb = new StringBuilder();
        NodeList children = record.getElementsByTagName("*");
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getChildNodes().getLength() == 1) { // It's a leaf
                sb.append(node.getTextContent()).append("|");
            }
        }
        return sb.toString();
    }

    private static void suppressSpecificInfo(Element record) {
        NodeList children = record.getChildNodes();
        // Schema-agnostic suppression: Mask the last few leaf nodes
        int count = 0;
        for (int i = children.getLength() - 1; i >= 0; i--) {
            if (children.item(i) instanceof Element && count < 2) {
                children.item(i).setTextContent("*****");
                count++;
            }
        }
    }

    private static void stripPotentialIdentifiers(Element record) {
        // Heuristic: remove nodes containing "id" in the name
        NodeList children = record.getElementsByTagName("*");
        for (int i = 0; i < children.getLength(); i++) {
            Element el = (Element) children.item(i);
            if (el.getTagName().toLowerCase().contains("id")) {
                el.setTextContent("REDACTED");
            }
        }
    }

    private static void saveXML(Document doc, String filePath) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(doc), new StreamResult(new File(filePath)));
    }
}