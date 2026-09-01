package org.atcgroup.mcupdater.client.ui.component;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xhtmlrenderer.simple.XHTMLPanel;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class HtmlView {

    private final XHTMLPanel panel;
    private Document document;

    public HtmlView() {
        this.panel = new XHTMLPanel();
    }

    public JComponent component() {
        return panel;
    }

    public void load(InputStream is) {
        try {
            this.panel.setDocument(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            this.document = panel.getDocument();
            is.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setText(String id, String text) {
        Element element = getElement(id);
        element.setTextContent(text);
    }

    public void setVisible(String id, boolean visible) {
        Element element = getElement(id);

        String style = element.getAttribute("style");

        if (visible) {
            style = style.replaceAll(
                    "(^|;)\\s*display\\s*:\\s*none\\s*;?",
                    ""
            );
        } else {
            style += ";display:none;";
        }

        element.setAttribute("style", style);
    }

    public void addClass(String id, String className) {
        Element element = getElement(id);

        String classes = element.getAttribute("class");

        if (!classes.isBlank()) {
            classes += " ";
        }

        element.setAttribute("class", classes + className);
    }

    public void refresh() {
        SwingUtilities.invokeLater(() -> {
            panel.setDocument(document);
            panel.revalidate();
            panel.repaint();
        });
    }

    private Element getElement(String id) {
        Element element = document.getElementById(id);

        if (element == null) {
            throw new IllegalArgumentException(
                    "Element not found: #" + id
            );
        }

        return element;
    }
}