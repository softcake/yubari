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

package org.softcake.yubari.loginform.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class JNLPObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(JNLPObject.class);
    public static final String DEFAULT_RESOURCES_PROPERTIES_PATH = "/jnlp/resources/property";
    public static final String CUSTOM_RESOURCES_PROPERTIES_PATH = "/jnlp/platform-res/property";
    public static final String APPLICATION_DESC_PATH = "/jnlp/application-desc";
    private final String jnlpUrl;
    private Map<String, String> resourcesMap = new HashMap();
    private Map<String, String> mapOfJNLPTagAttributes = new HashMap();
    private String mainClass = null;
    private boolean fail = false;
    private List<String> resourcesPropertiesPaths = new ArrayList();

    public JNLPObject(String jnlpUrl) {
        this.jnlpUrl = jnlpUrl;
        this.resourcesPropertiesPaths.add("/jnlp/resources/property");
        this.resourcesPropertiesPaths.add("/jnlp/platform-res/property");
    }

    public void addResourcesPropertiesPath(String resourcesPropertiesPath) {
        this.resourcesPropertiesPaths.add(resourcesPropertiesPath);
    }

    public Map<String, String> getResources() throws Exception {
        this.check();
        return this.resourcesMap;
    }

    public String getResource(String name) throws Exception {
        this.check();
        return (String)this.resourcesMap.get(name);
    }

    public String getJnlpTagAttribute(String name) throws Exception {
        this.check();
        String value = (String)this.mapOfJNLPTagAttributes.get(name);
        return value;
    }

    public boolean setSystemProperty(String property) {
        boolean updated = false;

        try {
            String propertyValue = this.getResource(property);
            if (!ObjectUtils.isNullOrEmpty(property) && propertyValue != null) {
                System.getProperties().put(property, propertyValue);
                updated = true;
            }
        } catch (Throwable var4) {
            LOGGER.error(var4.getMessage(), var4);
        }

        return updated;
    }

    public boolean isFail() {
        return this.fail;
    }

    public boolean downloadAndParse() {
        return this.downloadAndParse(1);
    }

    public boolean downloadAndParse(int maxAttemptCount) {
        int attemptIndex = 0;
        boolean fail = false;

        while(attemptIndex < maxAttemptCount && (fail = this.downloadAndParseImpl())) {
            ++attemptIndex;
            if (fail && attemptIndex < maxAttemptCount) {
                try {
                    Thread.sleep(333L);
                } catch (Exception var5) {
                    LOGGER.error(var5.getMessage(), var5);
                }
            }
        }

        return fail;
    }

    private boolean downloadAndParseImpl() {
        this.fail = false;

        try {
            Document doc = this.getXmlDocument();
            Iterator var2 = this.resourcesPropertiesPaths.iterator();

            String resourcesPropertiesPath;
            while(var2.hasNext()) {
                resourcesPropertiesPath = (String)var2.next();

                try {
                    this.extractProperties(doc, resourcesPropertiesPath);
                } catch (Exception var9) {
                    LOGGER.error(var9.getMessage(), var9);
                }
            }

            Element documentElement = doc.getDocumentElement();
            resourcesPropertiesPath = documentElement.getNodeName();
            NamedNodeMap attributes = documentElement.getAttributes();

            for(int i = 0; i < attributes.getLength(); ++i) {
                Node item = attributes.item(i);
                String attributeName = item.getNodeName();
                String attributeValue = item.getNodeValue();
                this.mapOfJNLPTagAttributes.put(attributeName, attributeValue);
            }

            this.extractMainClass(doc, "/jnlp/application-desc");
        } catch (Throwable var10) {
            this.fail = true;
            LOGGER.error(var10.getMessage(), var10);
        }

        return this.fail;
    }

    private void extractProperties(Document doc, String resourcesPropertiesPath) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList)xPath.compile(resourcesPropertiesPath).evaluate(doc, XPathConstants.NODESET);

        for(int i = 0; i < nodeList.getLength(); ++i) {
            Node node = nodeList.item(i);
            NamedNodeMap attributes = node.getAttributes();
            String key = attributes.getNamedItem("name").getNodeValue();
            String value = attributes.getNamedItem("value").getNodeValue();
            this.resourcesMap.put(key, value);
        }

    }

    private void extractMainClass(Document doc, String mainClassPath) throws Exception {
        try {
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList)xPath.compile(mainClassPath).evaluate(doc, XPathConstants.NODESET);
            if (nodeList.getLength() > 0) {
                Node node = nodeList.item(0);
                NamedNodeMap attributes = node.getAttributes();
                this.mainClass = attributes.getNamedItem("main-class").getNodeValue();
            }
        } catch (Throwable var7) {
            LOGGER.error("Cannot extract the main class");
            LOGGER.error(var7.getMessage(), var7);
        }

    }

    private void check() throws Exception {
        if (this.isFail()) {
            throw new Exception("JNLP downloading/parsing is failed.");
        }
    }

    private Document getXmlDocument() throws Exception {
        Document xmlDocument = null;
        ByteArrayInputStream jnlpAsByteArrayInputStream = this.getJNLPAsByteArrayInputStream();
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        xmlDocument = builder.parse(jnlpAsByteArrayInputStream);
        return xmlDocument;
    }

    private ByteArrayInputStream getJNLPAsByteArrayInputStream() throws Exception {
        ByteArrayInputStream bais = this.load();
        return bais;
    }

    private ByteArrayInputStream load() throws Exception {
        InputStream is = null;
        ByteArrayInputStream bais = null;

        try {
            URL url = new URL(this.jnlpUrl);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            is = connection.getInputStream();
            byte[] byteChunk = new byte[4096];
            ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);

            int n;
            while((n = is.read(byteChunk)) > 0) {
                baos.write(byteChunk, 0, n);
            }

            bais = new ByteArrayInputStream(baos.toByteArray());
            ByteArrayInputStream var8 = bais;
            return var8;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception var15) {
                    LOGGER.error(var15.getMessage(), var15);
                }
            }

        }
    }

    public String getJnlpUrl() {
        return this.jnlpUrl;
    }

    public String getMainClass() {
        return this.mainClass;
    }
}
