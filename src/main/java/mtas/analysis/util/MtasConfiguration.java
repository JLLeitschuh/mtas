package mtas.analysis.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.util.ResourceLoader;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class MtasConfiguration {
  private static final Log log = LogFactory.getLog(MtasConfiguration.class);

  private static final String CONFIGURATIONS_MTAS = "mtas";
  private static final String CONFIGURATIONS_CONFIGURATIONS = "configurations";
  private static final String CONFIGURATIONS_CONFIGURATION = "configuration";
  private static final String CONFIGURATIONS_CONFIGURATION_NAME = "name";
  private static final String TOKENIZER_CONFIGURATION_FILE = "file";

  public String name = null;
  public HashMap<String, String> attributes = new HashMap<>();
  public List<MtasConfiguration> children = new ArrayList<>();
  public MtasConfiguration parent = null;

  public MtasConfiguration() {
  }

  private static HashMap<String, HashMap<String, String>> readConfigurations(
    ResourceLoader resourceLoader, String configFile, String className) throws IOException {
    InputStream reader = resourceLoader.openResource(configFile);
    HashMap<String, HashMap<String, String>> configs = null;
    try {
      XMLStreamReader streamReader = XMLInputFactory.newInstance().createXMLStreamReader(reader);
      try {
        configs = readConfigurations(streamReader, className);
      } finally {
        //noinspection ThrowFromFinallyBlock
        streamReader.close();
      }
    } catch (XMLStreamException e) {
      log.debug(e);
    }
    return configs;
  }

  private static HashMap<String, HashMap<String, String>> readConfigurations(XMLStreamReader streamReader,
                                                                             String className)
    throws IOException, XMLStreamException {
    HashMap<String, HashMap<String, String>> configs = null;
    String currentElement = null;
    ArrayList<String> currentElements = new ArrayList<>();
    QName qname;
    boolean skipCurrentConfigurations = false;

    for (int event = streamReader.getEventType(); ; event = streamReader.next()) {
      switch (event) {
        case XMLStreamConstants.START_DOCUMENT:
          if (!streamReader.getCharacterEncodingScheme().equals("UTF-8")) {
            throw new IOException("XML not UTF-8 encoded");
          }
          break;
        case XMLStreamConstants.END_DOCUMENT:
          break;
        case XMLStreamConstants.SPACE:
          break;
        case XMLStreamConstants.START_ELEMENT:
          // get data
          qname = streamReader.getName();
          if (configs == null) {
            if (qname.getLocalPart().equals(CONFIGURATIONS_MTAS)) {
              configs = new HashMap<>();
            } else {
              throw new IOException("no Mtas Configurations File");
            }
          } else if (currentElement != null
            && currentElement.equals(CONFIGURATIONS_MTAS)) {
            if (qname.getLocalPart().equals(CONFIGURATIONS_CONFIGURATIONS)) {
              skipCurrentConfigurations = true;
              if (className != null) {
                for (int i = 0; i < streamReader.getAttributeCount(); i++) {
                  if (streamReader.getAttributeLocalName(i).equals("type")) {
                    if (streamReader.getAttributeValue(i).equals(className)) {
                      skipCurrentConfigurations = false;
                    }
                  }
                }
              }
            } else {
              throw new IOException("unexpected " + qname.getLocalPart());
            }
          } else if (currentElement != null
            && currentElement.equals(CONFIGURATIONS_CONFIGURATIONS)
            && !skipCurrentConfigurations) {
            if (qname.getLocalPart().equals(CONFIGURATIONS_CONFIGURATION)) {
              String configurationName = null;
              HashMap<String, String> configurationValues = new HashMap<>();
              for (int i = 0; i < streamReader.getAttributeCount(); i++) {
                if (streamReader.getAttributeLocalName(i)
                                .equals(CONFIGURATIONS_CONFIGURATION_NAME)) {
                  configurationName = streamReader.getAttributeValue(i);
                } else {
                  configurationValues.put(
                    streamReader.getAttributeLocalName(i),
                    streamReader.getAttributeValue(i));
                }
              }
              if (configurationName == null) {
                throw new IOException("configuration without "
                  + CONFIGURATIONS_CONFIGURATION_NAME);
              }
              configs.put(configurationName, configurationValues);
            } else {
              throw new IOException("unexpected tag " + qname.getLocalPart());
            }
          }
          currentElement = qname.getLocalPart();
          currentElements.add(currentElement);
          break;
        case XMLStreamConstants.END_ELEMENT:
          if (currentElement != null
            && currentElement.equals(CONFIGURATIONS_CONFIGURATIONS)) {
            skipCurrentConfigurations = false;
          }
          int i = currentElements.size();
          currentElements.remove(i - 1);
          if (i > 1) {
            currentElement = currentElements.get(i - 2);
          } else {
            currentElement = null;
          }
          break;
        case XMLStreamConstants.CHARACTERS:
          break;
      }
      if (!streamReader.hasNext()) {
        break;
      }
    }
    return configs;
  }

  public static HashMap<String, MtasConfiguration> readMtasTokenizerConfigurations(
    ResourceLoader resourceLoader, String configFile) throws IOException {
    HashMap<String, HashMap<String, String>> configs = readConfigurations(
      resourceLoader, configFile, MtasTokenizerFactory.class.getName());
    if (configs == null) {
      throw new IOException("no configurations");
    } else {
      HashMap<String, MtasConfiguration> result = new HashMap<>();
      for (Entry<String, HashMap<String, String>> entry : configs.entrySet()) {
        HashMap<String, String> config = entry.getValue();
        if (config.containsKey(TOKENIZER_CONFIGURATION_FILE)) {
          result.put(entry.getKey(), readConfiguration(resourceLoader
            .openResource(config.get(TOKENIZER_CONFIGURATION_FILE))));
        } else {
          throw new IOException("configuration " + entry.getKey() + " has no "
            + TOKENIZER_CONFIGURATION_FILE);
        }
      }
      return result;
    }
  }

  public static MtasConfiguration readConfiguration(InputStream reader)
    throws IOException {
    MtasConfiguration currentConfig = null;
    // parse xml
    XMLInputFactory factory = XMLInputFactory.newInstance();
    try {
      XMLStreamReader streamReader = factory.createXMLStreamReader(reader);
      QName qname;
      try {
        int event = streamReader.getEventType();
        while (true) {
          switch (event) {
            case XMLStreamConstants.START_DOCUMENT:
              if (!streamReader.getCharacterEncodingScheme().equals("UTF-8")) {
                throw new IOException("XML not UTF-8 encoded");
              }
              break;
            case XMLStreamConstants.END_DOCUMENT:
            case XMLStreamConstants.SPACE:
              break;
            case XMLStreamConstants.START_ELEMENT:
              // get data
              qname = streamReader.getName();
              if (currentConfig == null) {
                if (qname.getLocalPart().equals("mtas")) {
                  currentConfig = new MtasConfiguration();
                } else {
                  throw new IOException("no Mtas Configuration");
                }
              } else {
                MtasConfiguration parentConfig = currentConfig;
                currentConfig = new MtasConfiguration();
                parentConfig.children.add(currentConfig);
                currentConfig.parent = parentConfig;
                currentConfig.name = qname.getLocalPart();
                for (int i = 0; i < streamReader.getAttributeCount(); i++) {
                  currentConfig.attributes.put(
                    streamReader.getAttributeLocalName(i),
                    streamReader.getAttributeValue(i));
                }
              }
              break;
            case XMLStreamConstants.END_ELEMENT:
              //noinspection ConstantConditions
              if (currentConfig.parent == null) {
                return currentConfig;
              } else {
                currentConfig = currentConfig.parent;
              }
              break;
            case XMLStreamConstants.CHARACTERS:
              break;
          }
          if (!streamReader.hasNext()) {
            break;
          }
          event = streamReader.next();
        }
      } finally {
        streamReader.close();
      }
    } catch (XMLStreamException e) {
      log.debug(e);
    }
    return null;
  }

  public String toString() {
    return toString(0);
  }

  private String toString(int indent) {
    String text = "";
    if (name != null) {
      text += (indent > 0 ? String.format("%" + indent + "s", "") : "")
        + "name: " + name + "\n";
    }
    if (attributes != null) {
      for (Entry<String, String> entry : attributes.entrySet()) {
        text += (indent > 0 ? String.format("%" + indent + "s", "") : "") + entry.getKey()
          + ":" + entry.getValue() + "\n";
      }
    }
    if (children != null) {
      for (MtasConfiguration child : children) {
        text += (indent > 0 ? String.format("%" + indent + "s", "") : "")
          + child.toString(indent + 2);
      }
    }
    return text;
  }
}
