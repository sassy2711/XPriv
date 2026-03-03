package privacyEngine.code.helpers;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.*;

public class JsonToXmlConverter {

    public static void main(String[] args) throws Exception {

        String jsonFilePath = "corona-dataset.json";
        String outputXmlPath = "corona-dataset.xml";

        JSONObject jsonObject = loadJson(jsonFilePath);

        Document xmlDoc = createXmlDocument();
        Element root = xmlDoc.createElement("dataset");
        xmlDoc.appendChild(root);

        convertJsonToXml(jsonObject, xmlDoc, root);

        writeXml(xmlDoc, outputXmlPath);

        System.out.println("Conversion complete. Output written to " + outputXmlPath);
    }

    private static JSONObject loadJson(String filePath) throws Exception {
        FileInputStream fis = new FileInputStream(filePath);
        JSONTokener tokener = new JSONTokener(fis);
        return new JSONObject(tokener);
    }

    private static Document createXmlDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.newDocument();
    }

    private static void convertJsonToXml(Object json,
                                     Document doc,
                                     Element parent) {

    if (json instanceof JSONObject) {

        JSONObject jsonObject = (JSONObject) json;

        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {

            String key = keys.next();
            Object value = jsonObject.get(key);

            // Always prefix
            String safeKey = "n_" + key.replaceAll("[^A-Za-z0-9_.-]", "_");

            Element child = doc.createElement(safeKey);
            parent.appendChild(child);

            convertJsonToXml(value, doc, child);
        }

    } else if (json instanceof JSONArray) {

        JSONArray array = (JSONArray) json;

        for (int i = 0; i < array.length(); i++) {

            Object value = array.get(i);

            Element arrayElement = doc.createElement("item");
            parent.appendChild(arrayElement);

            convertJsonToXml(value, doc, arrayElement);
        }

    } else {
        parent.setTextContent(String.valueOf(json));
    }
}

    private static void writeXml(Document doc, String outputPath) throws Exception {

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();

        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(outputPath));

        transformer.transform(source, result);
    }
}