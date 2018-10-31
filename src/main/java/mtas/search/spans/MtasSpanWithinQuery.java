package mtas.search.spans;

import mtas.search.spans.util.MtasMaximumExpandSpanQuery;
import mtas.search.spans.util.MtasSpanQuery;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.SpanWithinQuery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MtasSpanWithinQuery extends MtasSpanQuery {
  private SpanWithinQuery baseQuery;
  private MtasSpanQuery smallQuery;
  private MtasSpanQuery bigQuery;
  private int leftBoundaryBigMinimum;
  private int leftBoundaryBigMaximum;
  private int rightBoundaryBigMaximum;
  private int rightBoundaryBigMinimum;
  private boolean autoAdjustBigQuery;

  String field;

  public MtasSpanWithinQuery(MtasSpanQuery q1, MtasSpanQuery q2) {
    this(q1, q2, 0, 0, 0, 0, true);
  }

  public MtasSpanWithinQuery(MtasSpanQuery q1, MtasSpanQuery q2,
      int leftMinimum, int leftMaximum, int rightMinimum, int rightMaximum,
      boolean adjustBigQuery) {
    super(null, null);
    bigQuery = q1;
    smallQuery = q2;
    leftBoundaryBigMinimum = leftMinimum;
    leftBoundaryBigMaximum = leftMaximum;
    rightBoundaryBigMinimum = rightMinimum;
    rightBoundaryBigMaximum = rightMaximum;
    autoAdjustBigQuery = adjustBigQuery;
    // recompute width
    Integer minimumWidth = null;
    Integer maximumWidth = null;
    if (bigQuery != null) {
      maximumWidth = bigQuery.getMaximumWidth();
      maximumWidth = (maximumWidth != null)
          ? maximumWidth + rightBoundaryBigMaximum + leftBoundaryBigMaximum
          : null;
    }
    if (smallQuery != null) {
      if (smallQuery.getMaximumWidth() != null && (maximumWidth == null
          || smallQuery.getMaximumWidth() < maximumWidth)) {
        maximumWidth = smallQuery.getMaximumWidth();
      }
      minimumWidth = smallQuery.getMinimumWidth();
    }
    setWidth(minimumWidth, maximumWidth);
    // compute field
    if (bigQuery != null && bigQuery.getField() != null) {
      field = bigQuery.getField();
    } else if (smallQuery != null && smallQuery.getField() != null) {
      field = smallQuery.getField();
    } else {
      field = null;
    }
    if (field != null) {
      baseQuery = new SpanWithinQuery(new MtasMaximumExpandSpanQuery(bigQuery,
          leftBoundaryBigMinimum, leftBoundaryBigMaximum,
          rightBoundaryBigMinimum, rightBoundaryBigMaximum), smallQuery);
    } else {
      baseQuery = null;
    }
  }

  @Override
  public MtasSpanQuery rewrite(IndexReader reader) throws IOException {
    MtasSpanQuery newBigQuery = bigQuery.rewrite(reader);
    MtasSpanQuery newSmallQuery = smallQuery.rewrite(reader);

    if (newBigQuery == null || newBigQuery instanceof MtasSpanMatchNoneQuery
        || newSmallQuery == null
        || newSmallQuery instanceof MtasSpanMatchNoneQuery) {
      return new MtasSpanMatchNoneQuery(field);
    }

    if (newSmallQuery.getMinimumWidth() != null
        && newBigQuery.getMaximumWidth() != null
        && newSmallQuery.getMinimumWidth() > (newBigQuery.getMaximumWidth()
            + leftBoundaryBigMaximum + rightBoundaryBigMaximum)) {
      return new MtasSpanMatchNoneQuery(field);
    }

    if (autoAdjustBigQuery) {
      if (newBigQuery instanceof MtasSpanRecurrenceQuery) {
        MtasSpanRecurrenceQuery recurrenceQuery = (MtasSpanRecurrenceQuery) newBigQuery;
        if (recurrenceQuery.getIgnoreQuery() == null
            && recurrenceQuery.getQuery() instanceof MtasSpanMatchAllQuery) {
          rightBoundaryBigMaximum += leftBoundaryBigMaximum
              + recurrenceQuery.getMaximumRecurrence();
          rightBoundaryBigMinimum += leftBoundaryBigMinimum
              + recurrenceQuery.getMinimumRecurrence();
          leftBoundaryBigMaximum = 0;
          leftBoundaryBigMinimum = 0;
          newBigQuery = new MtasSpanMatchAllQuery(field);
          // System.out.println("REPLACE WITH " + newBigQuery + " (["
          // + leftBoundaryMinimum + "," + leftBoundaryMaximum + "],["
          // + rightBoundaryMinimum + "," + rightBoundaryMaximum + "])");
          return new MtasSpanWithinQuery(newBigQuery, newSmallQuery,
              leftBoundaryBigMinimum, leftBoundaryBigMaximum,
              rightBoundaryBigMinimum, rightBoundaryBigMaximum,
              autoAdjustBigQuery).rewrite(reader);
        }
      } else if (newBigQuery instanceof MtasSpanMatchAllQuery) {
        if (leftBoundaryBigMaximum > 0) {
          rightBoundaryBigMaximum += leftBoundaryBigMaximum;
          rightBoundaryBigMinimum += leftBoundaryBigMinimum;
          leftBoundaryBigMaximum = 0;
          leftBoundaryBigMinimum = 0;
          // System.out.println("REPLACE WITH " + newBigQuery + " (["
          // + leftBoundaryMinimum + "," + leftBoundaryMaximum + "],["
          // + rightBoundaryMinimum + "," + rightBoundaryMaximum + "])");
          return new MtasSpanWithinQuery(newBigQuery, newSmallQuery,
              leftBoundaryBigMinimum, leftBoundaryBigMaximum,
              rightBoundaryBigMinimum, rightBoundaryBigMaximum,
              autoAdjustBigQuery).rewrite(reader);
        }
      } else if (newBigQuery instanceof MtasSpanSequenceQuery) {
        MtasSpanSequenceQuery sequenceQuery = (MtasSpanSequenceQuery) newBigQuery;
        if (sequenceQuery.getIgnoreQuery() == null) {
          List<MtasSpanSequenceItem> items = sequenceQuery.getItems();
          List<MtasSpanSequenceItem> newItems = new ArrayList<>();
          int newLeftBoundaryMinimum = 0;
          int newLeftBoundaryMaximum = 0;
          int newRightBoundaryMinimum = 0;
          int newRightBoundaryMaximum = 0;
          for (int i = 0; i < items.size(); i++) {
            // first item
            if (i == 0) {
              if (items.get(i).getQuery() instanceof MtasSpanMatchAllQuery) {
                newLeftBoundaryMaximum++;
                if (!items.get(i).isOptional()) {
                  newLeftBoundaryMinimum++;
                }
              } else if (items.get(i)
                  .getQuery() instanceof MtasSpanRecurrenceQuery) {
                MtasSpanRecurrenceQuery msrq = (MtasSpanRecurrenceQuery) items
                    .get(i).getQuery();
                if (msrq.getQuery() instanceof MtasSpanMatchAllQuery) {
                  newLeftBoundaryMaximum += msrq.getMaximumRecurrence();
                  if (!items.get(i).isOptional()) {
                    newLeftBoundaryMinimum += msrq.getMinimumRecurrence();
                  }
                } else {
                  newItems.add(items.get(i));
                }
              } else {
                newItems.add(items.get(i));
              }
              // last item
            } else if (i == (items.size() - 1)) {
              if (items.get(i).getQuery() instanceof MtasSpanMatchAllQuery) {
                newRightBoundaryMaximum++;
                if (!items.get(i).isOptional()) {
                  newRightBoundaryMinimum++;
                }
              } else if (items.get(i)
                  .getQuery() instanceof MtasSpanRecurrenceQuery) {
                MtasSpanRecurrenceQuery msrq = (MtasSpanRecurrenceQuery) items
                    .get(i).getQuery();
                if (msrq.getQuery() instanceof MtasSpanMatchAllQuery) {
                  newRightBoundaryMaximum += msrq.getMaximumRecurrence();
                  if (!items.get(i).isOptional()) {
                    newRightBoundaryMinimum += msrq.getMinimumRecurrence();
                  }
                } else {
                  newItems.add(items.get(i));
                }
              } else {
                newItems.add(items.get(i));
              }
              // other items
            } else {
              newItems.add(items.get(i));
            }
          }
          leftBoundaryBigMaximum += newLeftBoundaryMaximum;
          leftBoundaryBigMinimum += newLeftBoundaryMinimum;
          rightBoundaryBigMaximum += newRightBoundaryMaximum;
          rightBoundaryBigMinimum += newRightBoundaryMinimum;
          if (newItems.isEmpty()) {
            rightBoundaryBigMaximum = Math.max(0,
                rightBoundaryBigMaximum + leftBoundaryBigMaximum - 1);
            rightBoundaryBigMinimum = Math.max(0,
                rightBoundaryBigMinimum + leftBoundaryBigMinimum - 1);
            leftBoundaryBigMaximum = 0;
            leftBoundaryBigMinimum = 0;
            newItems.add(new MtasSpanSequenceItem(
                new MtasSpanMatchAllQuery(field), false));
          }
          if (!items.equals(newItems) || newLeftBoundaryMaximum > 0
              || newRightBoundaryMaximum > 0) {
            newBigQuery = (new MtasSpanSequenceQuery(newItems, null, null))
                .rewrite(reader);
            // System.out.println("REPLACE WITH " + newBigQuery + " (["
            // + leftBoundaryMinimum + "," + leftBoundaryMaximum + "],["
            // + rightBoundaryMinimum + "," + rightBoundaryMaximum + "])");
            return new MtasSpanWithinQuery(newBigQuery, newSmallQuery,
                leftBoundaryBigMinimum, leftBoundaryBigMaximum,
                rightBoundaryBigMinimum, rightBoundaryBigMaximum,
                autoAdjustBigQuery).rewrite(reader);
          }
        }
      }
    }

    if (!newBigQuery.equals(bigQuery) || !newSmallQuery.equals(smallQuery)) {
      return (new MtasSpanWithinQuery(newBigQuery, newSmallQuery,
          leftBoundaryBigMinimum, leftBoundaryBigMaximum,
          rightBoundaryBigMinimum, rightBoundaryBigMaximum, autoAdjustBigQuery))
              .rewrite(reader);
    } else if (newBigQuery.equals(newSmallQuery)) {
      return newBigQuery;
    } else {
      baseQuery = (SpanWithinQuery) baseQuery.rewrite(reader);
      return super.rewrite(reader);
    }
  }

  @Override
  public String getField() {
    return field;
  }

  @Override
  public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores, float boost)
      throws IOException {
    return baseQuery.createWeight(searcher, needsScores, boost);
  }

  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    buffer.append(this.getClass().getSimpleName() + "([");
    if (smallQuery != null) {
      buffer.append(smallQuery.toString(smallQuery.getField()));
    } else {
      buffer.append("null");
    }
    buffer.append(",");
    if (bigQuery != null) {
      buffer.append(bigQuery.toString(bigQuery.getField()));
    } else {
      buffer.append("null");
    }
    buffer.append(
        "],[" + leftBoundaryBigMinimum + "," + leftBoundaryBigMaximum + "],["
            + rightBoundaryBigMinimum + "," + rightBoundaryBigMaximum + "])");
    return buffer.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    final MtasSpanWithinQuery that = (MtasSpanWithinQuery) obj;
    return baseQuery.equals(that.baseQuery)
        && leftBoundaryBigMinimum == that.leftBoundaryBigMinimum
        && leftBoundaryBigMaximum == that.leftBoundaryBigMaximum
        && rightBoundaryBigMinimum == that.rightBoundaryBigMinimum
        && rightBoundaryBigMaximum == that.rightBoundaryBigMaximum;
  }

  @Override
  public int hashCode() {
    int h = Integer.rotateLeft(classHash(), 1);
    h ^= smallQuery.hashCode();
    h = Integer.rotateLeft(h, 1);
    h ^= bigQuery.hashCode();
    h = Integer.rotateLeft(h, leftBoundaryBigMinimum) + leftBoundaryBigMinimum;
    h ^= 2;
    h = Integer.rotateLeft(h, leftBoundaryBigMaximum) + leftBoundaryBigMaximum;
    h ^= 3;
    h = Integer.rotateLeft(h, rightBoundaryBigMinimum)
        + rightBoundaryBigMinimum;
    h ^= 5;
    h = Integer.rotateLeft(h, rightBoundaryBigMaximum)
        + rightBoundaryBigMaximum;
    return h;
  }

  @Override
  public void disableTwoPhaseIterator() {
    super.disableTwoPhaseIterator();
    bigQuery.disableTwoPhaseIterator();
    smallQuery.disableTwoPhaseIterator();
  }
  
  @Override
  public boolean isMatchAllPositionsQuery() {
    return false;
  }
}
