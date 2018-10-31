package mtas.codec;

import mtas.analysis.token.MtasTokenString;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;

public class MtasCodecPostingsFormat extends PostingsFormat {
  private static final Log log = LogFactory
      .getLog(MtasCodecPostingsFormat.class);

  public static final int VERSION_START = 3;
  public static final int VERSION_CURRENT = 3;

  static final int MTAS_OBJECT_HAS_PARENT = 1;
  static final int MTAS_OBJECT_HAS_POSITION_RANGE = 2;
  static final int MTAS_OBJECT_HAS_POSITION_SET = 4;
  static final int MTAS_OBJECT_HAS_OFFSET = 8;
  static final int MTAS_OBJECT_HAS_REALOFFSET = 16;
  static final int MTAS_OBJECT_HAS_PAYLOAD = 32;
  public static final int MTAS_STORAGE_BYTE = 0;

  public static final int MTAS_STORAGE_SHORT = 1;
  public static final int MTAS_STORAGE_INTEGER = 2;
  public static final int MTAS_STORAGE_LONG = 3;
  public static final String MTAS_TMP_FIELD_EXTENSION = "mtas.field.temporary";
  public static final String MTAS_TMP_OBJECT_EXTENSION = "mtas.object.temporary";
  public static final String MTAS_TMP_DOCS_EXTENSION = "mtas.docs.temporary";
  public static final String MTAS_TMP_DOC_EXTENSION = "mtas.doc.temporary";
  public static final String MTAS_TMP_DOCS_CHAINED_EXTENSION = "mtas.docs.chained.temporary";
  public static final String MTAS_FIELDINFO_ATTRIBUTE_PREFIX_SINGLE_POSITION = "mtas.prefix.single.position";
  public static final String MTAS_FIELDINFO_ATTRIBUTE_PREFIX_MULTIPLE_POSITION = "mtas.prefix.multiple.position";
  public static final String MTAS_FIELDINFO_ATTRIBUTE_PREFIX_SET_POSITION = "mtas.prefix.set.position";
  public static final String MTAS_FIELDINFO_ATTRIBUTE_PREFIX_INTERSECTION = "mtas.prefix.intersection";
  public static final String MTAS_OBJECT_EXTENSION = "mtas.object";
  public static final String MTAS_TERM_EXTENSION = "mtas.term";
  public static final String MTAS_FIELD_EXTENSION = "mtas.field";
  public static final String MTAS_PREFIX_EXTENSION = "mtas.prefix";
  public static final String MTAS_DOC_EXTENSION = "mtas.doc";
  public static final String MTAS_INDEX_DOC_ID_EXTENSION = "mtas.index.doc.id";
  public static final String MTAS_INDEX_OBJECT_ID_EXTENSION = "mtas.index.object.id";
  public static final String MTAS_INDEX_OBJECT_POSITION_EXTENSION = "mtas.index.object.position";
  public static final String MTAS_INDEX_OBJECT_PARENT_EXTENSION = "mtas.index.object.parent";

  public static final String MTAS_INDEX_TERM_PREFIX_POSITION_EXTENSION = "mtas.index.term.prefix.position";

  private String delegateCodecName = null;
  private PostingsFormat delegatePostingsFormat = null;

  public MtasCodecPostingsFormat() {
    this(MtasCodec.MTAS_CODEC_NAME);
  }

  public MtasCodecPostingsFormat(PostingsFormat delegate) {
    super(MtasCodec.MTAS_CODEC_NAME);
    delegateCodecName = delegate.getName();
    delegatePostingsFormat = delegate;
    // preload to prevent NoClassDefFoundErrors
    try {
      Class.forName("mtas.codec.payload.MtasPayloadDecoder");
      Class.forName("mtas.codec.payload.MtasBitInputStream");
      Class.forName("mtas.analysis.token.MtasPosition");
      Class.forName("mtas.analysis.token.MtasOffset");
      Class.forName("mtas.codec.tree.MtasRBTree");
      Class.forName("mtas.codec.MtasTerms");
      Class.forName("mtas.codec.util.CodecInfo");
      Class.forName("mtas.codec.tree.MtasTreeNodeId");
    } catch (ClassNotFoundException e) {
      log.error(e);
    }
  }

  public MtasCodecPostingsFormat(String codecName) {
    super(codecName);
    delegateCodecName = codecName;
    delegatePostingsFormat = null;
    // preload to prevent NoClassDefFoundErrors
    try {
      Class.forName("mtas.codec.payload.MtasPayloadDecoder");
      Class.forName("mtas.codec.payload.MtasBitInputStream");
      Class.forName("mtas.analysis.token.MtasPosition");
      Class.forName("mtas.analysis.token.MtasOffset");
      Class.forName("mtas.codec.tree.MtasRBTree");
      Class.forName("mtas.codec.MtasTerms");
      Class.forName("mtas.codec.util.CodecInfo");
      Class.forName("mtas.codec.tree.MtasTreeNodeId");
    } catch (ClassNotFoundException e) {
      log.error(e);
    }
  }

  @Override
  public final FieldsProducer fieldsProducer(SegmentReadState state)
      throws IOException {
    return new MtasFieldsProducer(state, getName());
  }

  @Override
  public final FieldsConsumer fieldsConsumer(SegmentWriteState state)
      throws IOException {
    if (delegatePostingsFormat != null) {
      return new MtasFieldsConsumer(
          delegatePostingsFormat.fieldsConsumer(state), state, getName(),
          delegatePostingsFormat.getName());
    } else {
      PostingsFormat pf = Codec.forName(delegateCodecName).postingsFormat();
      return pf.fieldsConsumer(state);
    }
  }

  public static MtasTokenString getToken(IndexInput inObject, IndexInput inTerm,
      Long ref) throws IOException {
    MtasTokenString token = null;
    try {
      inObject.seek(ref);
      token = new MtasTokenString(null, "");
      token.setId(inObject.readVInt());
      token.setTokenRef(ref);
      int objectFlags = inObject.readVInt();
      int[] positions = null;
      if ((objectFlags & MTAS_OBJECT_HAS_PARENT) == MTAS_OBJECT_HAS_PARENT) {
        int parentId = inObject.readVInt();
        token.setParentId(parentId);
      }
      if ((objectFlags
          & MTAS_OBJECT_HAS_POSITION_RANGE) == MTAS_OBJECT_HAS_POSITION_RANGE) {
        int positionStart = inObject.readVInt();
        int positionEnd = positionStart + inObject.readVInt();
        token.addPositionRange(positionStart, positionEnd);
      } else if ((objectFlags
          & MTAS_OBJECT_HAS_POSITION_SET) == MTAS_OBJECT_HAS_POSITION_SET) {
        int size = inObject.readVInt();
        int tmpPrevious = 0;
        positions = new int[size];
        for (int t = 0; t < size; t++) {
          int position = tmpPrevious + inObject.readVInt();
          tmpPrevious = position;
          positions[t] = position;
        }
        token.addPositions(positions);
      } else {
        int position = inObject.readVInt();
        token.addPosition(position);
      }
      if ((objectFlags & MTAS_OBJECT_HAS_OFFSET) == MTAS_OBJECT_HAS_OFFSET) {
        int offsetStart = inObject.readVInt();
        int offsetEnd = offsetStart + inObject.readVInt();
        token.setOffset(offsetStart, offsetEnd);
      }
      if ((objectFlags
          & MTAS_OBJECT_HAS_REALOFFSET) == MTAS_OBJECT_HAS_REALOFFSET) {
        int realOffsetStart = inObject.readVInt();
        int realOffsetEnd = realOffsetStart + inObject.readVInt();
        token.setRealOffset(realOffsetStart, realOffsetEnd);
      }
      if ((objectFlags & MTAS_OBJECT_HAS_PAYLOAD) == MTAS_OBJECT_HAS_PAYLOAD) {
        int length = inObject.readVInt();
        byte[] mtasPayload = new byte[length];
        inObject.readBytes(mtasPayload, 0, length);
        token.setPayload(new BytesRef(mtasPayload));
      }
      Long termRef = inObject.readVLong();
      inTerm.seek(termRef);
      token.setTermRef(termRef);
      token.setValue(inTerm.readString());
    } catch (Exception e) {
      throw new IOException(e);
    }
    return token;
  }

  public static String getTerm(IndexInput inTerm, Long ref) throws IOException {
    try {
      inTerm.seek(ref);
      return inTerm.readString();
    } catch (Exception e) {
      throw new IOException(e);
    }
  }
}
