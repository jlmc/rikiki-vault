package io.github.jlmc.rikikivault.gui.viewer;

import io.github.jlmc.rikikivault.gui.support.Messages;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

final class XmlFileViewer implements FileViewer {

    @Override
    public boolean supports(String fileName) {
        return fileName.toLowerCase().endsWith(".xml");
    }

    @Override
    public ViewerResult view(byte[] content, String fileName) {
        String raw = TextFileViewer.tryDecodeUtf8(content);
        if (raw == null) {
            return new ViewerResult.UnsupportedViewerResult(Messages.get("fileViewer.unsupported"));
        }
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new StreamSource(new StringReader(raw)), new StreamResult(writer));
            return new ViewerResult.TextViewerResult(writer.toString());
        } catch (Exception e) {
            return new ViewerResult.TextViewerResult(raw);
        }
    }
}
