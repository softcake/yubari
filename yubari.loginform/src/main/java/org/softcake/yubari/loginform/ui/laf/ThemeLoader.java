/*
 * Copyright 2018 softcake.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.softcake.yubari.loginform.ui.laf;

import org.softcake.yubari.loginform.util.ImageFactory;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.plaf.ColorUIResource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ThemeLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThemeLoader.class);

    public ThemeLoader() {
    }

    public List<Theme> loadThemes(InputStream themeXML) {
        ArrayList themes = new ArrayList();

        try {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
            Document themeDocument = documentBuilder.parse(themeXML);
            NodeList themeNodes = themeDocument.getElementsByTagName("theme");

            for(int i = 0; i < themeNodes.getLength(); ++i) {
                Node themeNode = themeNodes.item(i);
                themes.add(this.createTheme(themeNode));
            }
        } catch (Exception var9) {
            LOGGER.error(var9.getMessage(), var9);
        }

        return themes;
    }

    private Theme createTheme(Node themeNode) {
        Theme theme = new Theme();
        NamedNodeMap attributes = themeNode.getAttributes();
        Node namedItem = attributes.getNamedItem("id");
        theme.setId(namedItem.getNodeValue());
        NodeList nodes = themeNode.getChildNodes();
        List<Style> styles = new ArrayList();
        List<CustomUI> customUIs = new ArrayList();

        for(int i = 0; i < nodes.getLength(); ++i) {
            Node node = nodes.item(i);
            if ("style".equals(node.getNodeName())) {
                styles.add(this.createStyle(node));
            }

            if ("ui".equals(node.getNodeName())) {
                customUIs.add(this.createCustomUI(node));
            }
        }

        theme.setCustomUIs(customUIs);
        theme.setStyles(styles);
        return theme;
    }

    private CustomUI createCustomUI(Node node) {
        CustomUI customUI = new CustomUI();
        NamedNodeMap attributes = node.getAttributes();
        Node id = attributes.getNamedItem("id");
        Node clazz = attributes.getNamedItem("class");
        customUI.setId(id.getNodeValue());
        customUI.setUIClass(clazz.getNodeValue());
        return customUI;
    }

    private Style createStyle(Node node) {
        Style style = new Style();
        NamedNodeMap attributes = node.getAttributes();
        Node namedItem = attributes.getNamedItem("id");
        style.setId(namedItem.getNodeValue());
        Map<String, Object> propertyMap = new HashMap();
        NodeList propertyNodes = node.getChildNodes();

        for(int i = 0; i < propertyNodes.getLength(); ++i) {
            Node propertyNode = propertyNodes.item(i);
            String id;
            String value;
            if ("color".equals(propertyNode.getNodeName())) {
                id = propertyNode.getAttributes().getNamedItem("id").getNodeValue();
                value = propertyNode.getAttributes().getNamedItem("value").getNodeValue();
                propertyMap.put(id, new ColorUIResource(Color.decode(value)));
            } else {
                String left;
                String bottom;
                if ("font".equals(propertyNode.getNodeName())) {
                    id = propertyNode.getAttributes().getNamedItem("id").getNodeValue();
                    value = propertyNode.getAttributes().getNamedItem("name").getNodeValue();
                    left = propertyNode.getAttributes().getNamedItem("style").getNodeValue();
                    bottom = propertyNode.getAttributes().getNamedItem("size").getNodeValue();
                    Font font = new Font(value, Integer.parseInt(left), Integer.parseInt(bottom));
                    propertyMap.put(id, font);
                } else if ("icon".equals(propertyNode.getNodeName())) {
                    id = propertyNode.getAttributes().getNamedItem("id").getNodeValue();
                    value = propertyNode.getAttributes().getNamedItem("value").getNodeValue();
                    propertyMap.put(id, ImageFactory.getIcon(value));
                } else if ("boolean".equals(propertyNode.getNodeName())) {
                    id = propertyNode.getAttributes().getNamedItem("id").getNodeValue();
                    value = propertyNode.getAttributes().getNamedItem("value").getNodeValue();
                    propertyMap.put(id, Boolean.parseBoolean(value));
                } else if ("int".equals(propertyNode.getNodeName())) {
                    id = propertyNode.getAttributes().getNamedItem("id").getNodeValue();
                    value = propertyNode.getAttributes().getNamedItem("value").getNodeValue();
                    propertyMap.put(id, Integer.parseInt(value));
                } else if ("insets".equals(propertyNode.getNodeName())) {
                    id = propertyNode.getAttributes().getNamedItem("id").getNodeValue();
                    value = propertyNode.getAttributes().getNamedItem("top").getNodeValue();
                    left = propertyNode.getAttributes().getNamedItem("left").getNodeValue();
                    bottom = propertyNode.getAttributes().getNamedItem("bottom").getNodeValue();
                    String right = propertyNode.getAttributes().getNamedItem("right").getNodeValue();
                    Insets insets = new Insets(Integer.parseInt(value), Integer.parseInt(left), Integer.parseInt(bottom), Integer.parseInt(right));
                    propertyMap.put(id, insets);
                } else if ("javafx_id".equals(propertyNode.getNodeName())) {
                    id = propertyNode.getAttributes().getNamedItem("id").getNodeValue();
                    value = propertyNode.getAttributes().getNamedItem("value").getNodeValue();
                    propertyMap.put(id, value);
                }
            }
        }

        style.setPropertyMap(propertyMap);
        return style;
    }
}
