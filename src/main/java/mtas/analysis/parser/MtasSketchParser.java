package mtas.analysis.parser;

import mtas.analysis.token.MtasToken;
import mtas.analysis.token.MtasTokenCollection;
import mtas.analysis.token.MtasTokenIdFactory;
import mtas.analysis.util.LineReader;
import mtas.analysis.util.MtasConfigException;
import mtas.analysis.util.Configuration;
import mtas.analysis.util.MtasParserException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final public class MtasSketchParser extends MtasBasicParser {
  private static final Log log = LogFactory.getLog(MtasSketchParser.class);

  private MtasParserType<MtasParserMapping<?>> wordType = null;
  private HashMap<Integer, MtasParserType<MtasParserMapping<?>>> wordAnnotationTypes = new HashMap<>();
  private HashMap<String, MtasParserType<MtasParserMapping<?>>> groupTypes = new HashMap<>();

  public MtasSketchParser(Configuration config) {
    super(config);
    autorepair = true;
    try {
      initParser();
    } catch (MtasConfigException e) {
      log.error(e);
    }
  }

  @Override
  protected void initParser() throws MtasConfigException {
    super.initParser();
    if (config != null) {

      // always word, no mappings
      wordType = new MtasParserType<>(MAPPING_TYPE_WORD, null, false);

      for (int i = 0; i < config.numChildren(); i++) {
        Configuration current = config.child(i);
        if (current.getName().equals("mappings")) {
          for (int j = 0; j < current.numChildren(); j++) {
            if (current.child(j).getName().equals("mapping")) {
              Configuration mapping = current.child(j);
              String typeMapping = mapping.getAttr("type");
              if (typeMapping == null) {
                continue;
              }

              String nameMapping = mapping.getAttr("name");
              if (typeMapping.equals(MAPPING_TYPE_WORD)) {
                MtasSketchParserMappingWord m = new MtasSketchParserMappingWord();
                m.processConfig(mapping);
                wordType.addItem(m);
              } else if (typeMapping.equals(MAPPING_TYPE_WORD_ANNOTATION)
                && (nameMapping != null)) {
                MtasSketchParserMappingWordAnnotation m = new MtasSketchParserMappingWordAnnotation();
                m.processConfig(mapping);
                if (wordAnnotationTypes
                  .containsKey(Integer.parseInt(nameMapping))) {
                  wordAnnotationTypes.get(Integer.parseInt(nameMapping))
                                     .addItem(m);
                } else {
                  MtasParserType<MtasParserMapping<?>> t = new MtasParserType<>(
                    typeMapping, nameMapping, false);
                  t.addItem(m);
                  wordAnnotationTypes.put(Integer.parseInt(nameMapping), t);
                }
              } else if (typeMapping.equals(MAPPING_TYPE_GROUP)
                && (nameMapping != null)) {
                MtasSketchParserMappingGroup m = new MtasSketchParserMappingGroup();
                m.processConfig(mapping);
                if (groupTypes.containsKey(nameMapping)) {
                  groupTypes.get(nameMapping).addItem(m);
                } else {
                  MtasParserType<MtasParserMapping<?>> t = new MtasParserType<>(
                    typeMapping, nameMapping, false);
                  t.addItem(m);
                  groupTypes.put(nameMapping, t);
                }
              } else {
                throw new MtasConfigException("unknown mapping type "
                  + typeMapping + " or missing name");
              }
            }
          }
        }
      }
    }
  }

  @Override
  public MtasTokenCollection createTokenCollection(Reader reader)
    throws MtasParserException, MtasConfigException {
    AtomicInteger position = new AtomicInteger(0);
    Integer unknownAncestors = 0;

    Map<String, Set<Integer>> idPositions = new HashMap<>();
    Map<String, Integer[]> idOffsets = new HashMap<>();

    Map<String, Map<Integer, Set<String>>> updateList = createUpdateList();
    Map<String, List<MtasParserObject>> currentList = createCurrentList();

    tokenCollection = new MtasTokenCollection();
    MtasTokenIdFactory mtasTokenIdFactory = new MtasTokenIdFactory();
    try (LineReader br = new LineReader(reader)) {
      String line;
      int currentOffset;
      int previousOffset = br.getPosition();
      MtasParserType tmpCurrentType;
      MtasParserObject currentObject;
      Pattern groupPattern = Pattern.compile("^<([^\\/>]+)\\/>$");
      Pattern groupStartPattern = Pattern.compile("^<([^>\\/\\s][^>\\s]*)(|\\s[^>]+)>$");
      Pattern groupEndPattern = Pattern.compile("^<\\/([^>\\s]+)>$");
      Pattern attributePattern = Pattern.compile("([^\\s]+)=\"([^\"]*)\"");
      while ((line = br.readLine()) != null) {
        currentOffset = br.getPosition();
        // group
        if (line.trim().matches("^<[^>]*>$")) {
          Matcher matcherGroupStart = groupStartPattern.matcher(line.trim());
          Matcher matcherGroupEnd = groupEndPattern.matcher(line.trim());
          Matcher matcherGroup = groupPattern.matcher(line.trim());
          if (matcherGroup.find()) {
            // full group, ignore
          } else if (matcherGroupStart.find()) {
            // start group
            // System.out.println("Start "+matcherGroupStart.group(1)+" -
            // "+matcherGroupStart.group(2));
            if ((currentList.get(MAPPING_TYPE_WORD).isEmpty())
              && (currentList.get(MAPPING_TYPE_RELATION).isEmpty())
              && (currentList.get(MAPPING_TYPE_GROUP_ANNOTATION).isEmpty())
              && (tmpCurrentType = groupTypes
              .get(matcherGroupStart.group(1))) != null) {
              currentObject = new MtasParserObject(tmpCurrentType);
              currentObject.setUnknownAncestorNumber(unknownAncestors);
              currentObject.setRealOffsetStart(previousOffset);
              String attributeText = matcherGroupStart.group(2).trim();
              if (!attributeText.equals("")) {
                Matcher matcherAttribute = attributePattern
                  .matcher(attributeText);
                currentObject.objectAttributes = new HashMap<String, String>();
                while (matcherAttribute.find()) {
                  currentObject.objectAttributes.put(matcherAttribute.group(1),
                    matcherAttribute.group(2));
                }
              }
              if (!prevalidateObject(currentObject, currentList)) {
                unknownAncestors++;
              } else {
                currentList.get(MAPPING_TYPE_GROUP).add(currentObject);
                unknownAncestors = 0;
              }
            }
          } else if (matcherGroupEnd.find()) {
            // end group
            if (!currentList.get(MAPPING_TYPE_GROUP).isEmpty()) {
              if ((tmpCurrentType = groupTypes
                .get(matcherGroupEnd.group(1))) != null) {
                currentObject = currentList.get(MAPPING_TYPE_GROUP)
                                           .remove(currentList.get(MAPPING_TYPE_GROUP).size() - 1);
                assert unknownAncestors == 0 : "error in administration "
                  + currentObject.getType().getName();
                // ignore text: should not occur
                currentObject.setRealOffsetEnd(currentOffset - 1);
                idPositions.put(currentObject.getId(),
                  currentObject.getPositions());
                idOffsets.put(currentObject.getId(), currentObject.getOffset());
                currentObject.updateMappings(idPositions, idOffsets);
                unknownAncestors = currentObject.getUnknownAncestorNumber();
                computeMappingsFromObject(mtasTokenIdFactory, currentObject,
                  currentList, updateList);
              }
            }
          }
        } else {
          if ((currentList.get(MAPPING_TYPE_RELATION).isEmpty())
            && (currentList.get(MAPPING_TYPE_GROUP_ANNOTATION).isEmpty())
            && (currentList.get(MAPPING_TYPE_WORD).isEmpty())
            && (currentList.get(MAPPING_TYPE_WORD_ANNOTATION).isEmpty())
            && (wordType != null)) {
            // start word
            currentObject = new MtasParserObject(wordType);
            currentObject.setOffsetStart(previousOffset);
            currentObject.setRealOffsetStart(previousOffset);
            currentObject.setUnknownAncestorNumber(unknownAncestors);
            if (!prevalidateObject(currentObject, currentList)) {
              unknownAncestors++;
            } else {
              int p = position.getAndIncrement();
              currentObject.addPosition(p);
              currentList.get(MAPPING_TYPE_WORD).add(currentObject);
              unknownAncestors = 0;
            }
            if ((currentList.get(MAPPING_TYPE_RELATION).isEmpty())
              && (currentList.get(MAPPING_TYPE_GROUP_ANNOTATION).isEmpty())
              && (!currentList.get(MAPPING_TYPE_WORD).isEmpty())) {
              // start and finish word annotations
              String[] items = line.split("\t");
              for (int i = 0; i < items.length; i++) {
                if ((tmpCurrentType = wordAnnotationTypes.get(i)) != null) {
                  // start word annotation
                  currentObject = new MtasParserObject(tmpCurrentType);
                  currentObject.setRealOffsetStart(previousOffset);
                  currentObject.addPositions(currentList.get(MAPPING_TYPE_WORD)
                                                        .get((currentList.get(MAPPING_TYPE_WORD).size() - 1))
                                                        .getPositions());
                  currentObject.setUnknownAncestorNumber(unknownAncestors);
                  if (!prevalidateObject(currentObject, currentList)) {
                    unknownAncestors++;
                  } else {
                    currentList.get(MAPPING_TYPE_WORD_ANNOTATION)
                               .add(currentObject);
                    unknownAncestors = 0;
                  }
                  // finish word annotation
                  if (unknownAncestors > 0) {
                    unknownAncestors--;
                  } else {
                    currentObject = currentList
                      .get(MAPPING_TYPE_WORD_ANNOTATION).remove(
                        currentList.get(MAPPING_TYPE_WORD_ANNOTATION).size()
                          - 1);
                    assert unknownAncestors == 0 : "error in administration "
                      + currentObject.getType().getName();
                    currentObject.setText(items[i]);
                    currentObject.setRealOffsetEnd(currentOffset - 1);
                    idPositions.put(currentObject.getId(),
                      currentObject.getPositions());
                    idOffsets.put(currentObject.getId(),
                      currentObject.getOffset());
                    // offset always null, so update later with word (should be
                    // possible)
                    if ((currentObject.getId() != null)
                      && (!currentList.get(MAPPING_TYPE_WORD).isEmpty())) {
                      currentList.get(MAPPING_TYPE_WORD)
                                 .get((currentList.get(MAPPING_TYPE_WORD).size() - 1))
                                 .addUpdateableIdWithOffset(currentObject.getId());
                    }
                    currentObject.updateMappings(idPositions, idOffsets);
                    unknownAncestors = currentObject.getUnknownAncestorNumber();
                    computeMappingsFromObject(mtasTokenIdFactory, currentObject,
                      currentList, updateList);
                  }
                }
              }
            }
            // finish word
            if (unknownAncestors > 0) {
              unknownAncestors--;
            } else {
              currentObject = currentList.get(MAPPING_TYPE_WORD)
                                         .remove(currentList.get(MAPPING_TYPE_WORD).size() - 1);
              assert unknownAncestors == 0 : "error in administration "
                + currentObject.getType().getName();
              currentObject.setText(null);
              currentObject.setOffsetEnd(currentOffset - 1);
              currentObject.setRealOffsetEnd(currentOffset - 1);
              // update ancestor groups with position and offset
              for (MtasParserObject currentGroup : currentList
                .get(MAPPING_TYPE_GROUP)) {
                currentGroup.addPositions(currentObject.getPositions());
                currentGroup.addOffsetStart(currentObject.getOffsetStart());
                currentGroup.addOffsetEnd(currentObject.getOffsetEnd());
              }
              idPositions.put(currentObject.getId(),
                currentObject.getPositions());
              idOffsets.put(currentObject.getId(), currentObject.getOffset());
              currentObject.updateMappings(idPositions, idOffsets);
              unknownAncestors = currentObject.getUnknownAncestorNumber();
              computeMappingsFromObject(mtasTokenIdFactory, currentObject,
                currentList, updateList);
            }
          }
        }
        previousOffset = br.getPosition();
      }
    } catch (IOException e) {
      log.debug(e);
      throw new MtasParserException(e.getMessage());
    }
    // update tokens with offset
    for (Entry<Integer, Set<String>> updateItem : updateList
      .get(UPDATE_TYPE_OFFSET).entrySet()) {
      for (String refId : updateItem.getValue()) {
        Integer[] refOffset = idOffsets.get(refId);
        if (refOffset != null) {
          tokenCollection.get(updateItem.getKey()).addOffset(refOffset[0],
            refOffset[1]);
        }
      }
    }
    // update tokens with position
    for (Entry<Integer, Set<String>> updateItem : updateList
      .get(UPDATE_TYPE_POSITION).entrySet()) {
      for (String refId : updateItem.getValue()) {
        MtasToken token = tokenCollection.get(updateItem.getKey());
        token.addPositions(idPositions.get(refId));
      }
    }
    // final check
    tokenCollection.check(autorepair, makeunique);
    return tokenCollection;
  }

  @Override
  public String printConfig() {
    StringBuilder text = new StringBuilder();
    text.append("=== CONFIGURATION ===\n");
    text.append("type: " + wordAnnotationTypes.size() + " x wordAnnotation");
    text.append(printConfigTypes(wordAnnotationTypes));
    text.append("=== CONFIGURATION ===\n");
    return text.toString();
  }

  private String printConfigTypes(
    HashMap<?, MtasParserType<MtasParserMapping<?>>> types) {
    StringBuilder text = new StringBuilder();
    for (Entry<?, MtasParserType<MtasParserMapping<?>>> entry : types
      .entrySet()) {
      text.append("- " + entry.getKey() + ": " + entry.getValue().items.size()
        + " mapping(s)\n");
      for (int i = 0; i < entry.getValue().items.size(); i++) {
        text.append("\t" + entry.getValue().items.get(i) + "\n");
      }
    }
    return text.toString();
  }

  private class MtasSketchParserMappingWord
    extends MtasParserMapping<MtasSketchParserMappingWord> {
    public MtasSketchParserMappingWord() {
      super();
      this.position = SOURCE_OWN;
      this.realOffset = SOURCE_OWN;
      this.offset = SOURCE_OWN;
      this.type = MAPPING_TYPE_WORD;
    }

    @Override
    protected MtasSketchParserMappingWord self() {
      return this;
    }
  }

  private class MtasSketchParserMappingWordAnnotation
    extends MtasParserMapping<MtasSketchParserMappingWordAnnotation> {
    public MtasSketchParserMappingWordAnnotation() {
      super();
      this.position = SOURCE_OWN;
      this.realOffset = SOURCE_OWN;
      this.offset = SOURCE_ANCESTOR_WORD;
      this.type = MAPPING_TYPE_WORD_ANNOTATION;
    }

    @Override
    protected MtasSketchParserMappingWordAnnotation self() {
      return this;
    }
  }

  private class MtasSketchParserMappingGroup
    extends MtasParserMapping<MtasSketchParserMappingGroup> {
    public MtasSketchParserMappingGroup() {
      super();
      this.position = SOURCE_OWN;
      this.realOffset = SOURCE_OWN;
      this.offset = SOURCE_OWN;
      this.type = MAPPING_TYPE_GROUP;
    }

    @Override
    protected MtasSketchParserMappingGroup self() {
      return this;
    }
  }
}
