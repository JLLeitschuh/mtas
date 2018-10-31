package mtas.search.spans;

import mtas.codec.util.CodecInfo;
import mtas.codec.util.CodecInfo.IndexDoc;
import mtas.search.spans.util.MtasSpans;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.SpanCollector;

import java.io.IOException;

public class MtasSpanMatchAllSpans extends MtasSpans {
  private MtasSpanMatchAllQuery query;
  private String field;
  private int minPosition;
  private int maxPosition;
  private int currentStartPosition;
  private int currentEndPosition;
  private int docId;
  private CodecInfo mtasCodecInfo;

  public MtasSpanMatchAllSpans(MtasSpanMatchAllQuery query,
      CodecInfo mtasCodecInfo, String field) {
    super();
    this.query = query;
    this.mtasCodecInfo = mtasCodecInfo;
    this.field = field;
    minPosition = NO_MORE_POSITIONS;
    maxPosition = NO_MORE_POSITIONS;
    currentStartPosition = NO_MORE_POSITIONS;
    currentEndPosition = NO_MORE_POSITIONS;
    docId = -1;
  }

  @Override
  public int nextStartPosition() throws IOException {
    if (currentStartPosition < minPosition) {
      currentStartPosition = minPosition;
      currentEndPosition = currentStartPosition + 1;
    } else {
      currentStartPosition++;
      currentEndPosition = currentStartPosition + 1;
      if (currentStartPosition > maxPosition) {
        currentStartPosition = NO_MORE_POSITIONS;
        currentEndPosition = NO_MORE_POSITIONS;
      }
    }
    return currentStartPosition;
  }

  @Override
  public int startPosition() {
    return currentStartPosition;
  }

  @Override
  public int endPosition() {
    return currentEndPosition;
  }

  @Override
  public int width() {
    return 0;
  }

  @Override
  public void collect(SpanCollector collector) throws IOException {
    // do nothing
  }

  @Override
  public int docID() {
    return docId;
  }

  @Override
  public int nextDoc() throws IOException {
    IndexDoc indexDoc = mtasCodecInfo.getNextDoc(field, docId);
    if (indexDoc != null) {
      docId = indexDoc.docId;
      minPosition = indexDoc.minPosition;
      maxPosition = indexDoc.maxPosition;
      currentStartPosition = -1;
      currentEndPosition = -1;
    } else {
      docId = NO_MORE_DOCS;
      minPosition = NO_MORE_POSITIONS;
      maxPosition = NO_MORE_POSITIONS;
      currentStartPosition = NO_MORE_POSITIONS;
      currentEndPosition = NO_MORE_POSITIONS;
    }
    return docId;
  }

  @Override
  public int advance(int target) throws IOException {
    IndexDoc indexDoc = mtasCodecInfo.getNextDoc(field, (target - 1));
    if (indexDoc != null) {
      docId = indexDoc.docId;
      minPosition = indexDoc.minPosition;
      maxPosition = indexDoc.maxPosition;
      currentStartPosition = -1;
      currentEndPosition = -1;
    } else {
      docId = NO_MORE_DOCS;
      minPosition = NO_MORE_POSITIONS;
      maxPosition = NO_MORE_POSITIONS;
      currentStartPosition = NO_MORE_POSITIONS;
      currentEndPosition = NO_MORE_POSITIONS;
    }
    return docId;
  }

  @Override
  public long cost() {
    return 0;
  }

  @Override
  public float positionsCost() {
    return 0;
  }

  @Override
  public TwoPhaseIterator asTwoPhaseIterator() {
    if (!query.twoPhaseIteratorAllowed()) {
      return null;
    } else {
      // TODO
      return null;
    }
  }

}
