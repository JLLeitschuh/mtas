package mtas.search.spans;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;

import mtas.search.spans.util.MtasSpanQuery;

/**
 * The Class MtasSpanRecurrenceQuery.
 */
public class MtasSpanRecurrenceQuery extends MtasSpanQuery
    implements Cloneable {

  /** The clause. */
  private MtasSpanQuery query;

  /** The minimum recurrence. */
  private int minimumRecurrence;

  /** The maximum recurrence. */
  private int maximumRecurrence;

  /** The ignore clause. */
  private MtasSpanQuery ignoreQuery;

  /** The maximum ignore length. */
  private Integer maximumIgnoreLength;

  /** The field. */
  private String field;

  /**
   * Instantiates a new mtas span recurrence query.
   *
   * @param query
   *          the clause
   * @param minimumRecurrence
   *          the minimum recurrence
   * @param maximumRecurrence
   *          the maximum recurrence
   * @param ignoreQuery
   *          the ignore
   * @param maximumIgnoreLength
   *          the maximum ignore length
   */
  public MtasSpanRecurrenceQuery(MtasSpanQuery query, int minimumRecurrence,
      int maximumRecurrence, MtasSpanQuery ignoreQuery,
      Integer maximumIgnoreLength) {
    super(null, null);
    field = query.getField();
    this.query = query;
    if (field != null && ignoreQuery != null) {
      if (ignoreQuery.getField() == null
          || field.equals(ignoreQuery.getField())) {
        this.ignoreQuery = ignoreQuery;
        this.maximumIgnoreLength = maximumIgnoreLength==null?1:maximumIgnoreLength;
      } else {
        throw new IllegalArgumentException(
            "ignore must have same field as clauses");
      }
    } else {
      this.ignoreQuery = null;
      this.maximumIgnoreLength = null;
    }
    setRecurrence(minimumRecurrence, maximumRecurrence);
  }

  /**
   * Gets the clause.
   *
   * @return the clause
   */
  public MtasSpanQuery getQuery() {
    return query;
  }

  public MtasSpanQuery getIgnoreQuery() {
    return ignoreQuery;
  }

  public Integer getMaximumIgnoreLength() {
    return maximumIgnoreLength;
  }

  public int getMinimumRecurrence() {
    return minimumRecurrence;
  }

  public int getMaximumRecurrence() {
    return maximumRecurrence;
  }

  public void setRecurrence(int minimumRecurrence, int maximumRecurrence) {
    if (minimumRecurrence > maximumRecurrence) {
      throw new IllegalArgumentException(
          "minimumRecurrence > maximumRecurrence");
    } else if (minimumRecurrence < 1) {
      throw new IllegalArgumentException("minimumRecurrence < 1 not supported");
    } else if (query == null) {
      throw new IllegalArgumentException("no clause");
    }
    this.minimumRecurrence = minimumRecurrence;
    this.maximumRecurrence = maximumRecurrence;
    // set minimum/maximum
    Integer minimum = null, maximum = null;
    if (query.getMinimumWidth() != null) {
      minimum = minimumRecurrence * query.getMinimumWidth();
    }
    if (query.getMaximumWidth() != null) {
      maximum = maximumRecurrence * query.getMaximumWidth();
      if (ignoreQuery != null && maximumIgnoreLength != null) {
        if (ignoreQuery.getMaximumWidth() != null) {
          maximum += (maximumRecurrence - 1) * maximumIgnoreLength
              * ignoreQuery.getMaximumWidth();
        } else {
          maximum = null;
        }
      }
    }
    setWidth(minimum, maximum);
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.lucene.search.spans.SpanQuery#getField()
   */
  @Override
  public String getField() {
    return field;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.apache.lucene.search.Query#rewrite(org.apache.lucene.index.IndexReader)
   */
  @Override
  public MtasSpanQuery rewrite(IndexReader reader) throws IOException {
    MtasSpanQuery newQuery = query.rewrite(reader);
    if (maximumRecurrence == 1) {
      return newQuery;
    } else {
      MtasSpanQuery newIgnoreQuery = (ignoreQuery != null)
          ? ignoreQuery.rewrite(reader) : null;
      if (newQuery instanceof MtasSpanRecurrenceQuery) {
        // TODO: for now too difficult, possibly merge later
      }
      if (newQuery != query
          || (newIgnoreQuery != null && newIgnoreQuery != ignoreQuery)) {
        return new MtasSpanRecurrenceQuery(newQuery, minimumRecurrence,
            maximumRecurrence, newIgnoreQuery, maximumIgnoreLength)
                .rewrite(reader);
      } else {
        return super.rewrite(reader);
      }
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.lucene.search.Query#toString(java.lang.String)
   */
  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    buffer.append(this.getClass().getSimpleName() + "([");
    buffer.append(query.toString(query.getField()));
    buffer.append("," + minimumRecurrence + "," + maximumRecurrence);
    buffer.append(", ");
    buffer.append(ignoreQuery);
    buffer.append("])");
    return buffer.toString();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.lucene.search.Query#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    final MtasSpanRecurrenceQuery other = (MtasSpanRecurrenceQuery) obj;
    return query.equals(other.query)
        && minimumRecurrence == other.minimumRecurrence
        && maximumRecurrence == other.maximumRecurrence
        && ((ignoreQuery == null && other.ignoreQuery == null)
            || ignoreQuery != null && other.ignoreQuery != null
                && ignoreQuery.equals(other.ignoreQuery));
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.lucene.search.Query#hashCode()
   */
  @Override
  public int hashCode() {
    int h = this.getClass().getSimpleName().hashCode();
    h = (h * 7) ^ query.hashCode();
    h = (h * 11) ^ minimumRecurrence;
    h = (h * 13) ^ maximumRecurrence;
    return h;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.apache.lucene.search.spans.SpanQuery#createWeight(org.apache.lucene.
   * search.IndexSearcher, boolean)
   */
  @Override
  public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores)
      throws IOException {
    SpanWeight subWeight = query.createWeight(searcher, false);
    SpanWeight ignoreWeight = null;
    if (ignoreQuery != null) {
      ignoreWeight = ignoreQuery.createWeight(searcher, false);
    }
    return new SpanRecurrenceWeight(subWeight, ignoreWeight,
        maximumIgnoreLength, searcher,
        needsScores ? getTermContexts(subWeight) : null);
  }

  /**
   * The Class SpanRecurrenceWeight.
   */
  public class SpanRecurrenceWeight extends SpanWeight {

    /** The sub weight. */
    final SpanWeight subWeight;

    /** The ignore weight. */
    final SpanWeight ignoreWeight;

    /** The maximum ignore length. */
    final Integer maximumIgnoreLength;

    /**
     * Instantiates a new span recurrence weight.
     *
     * @param subWeight
     *          the sub weight
     * @param ignoreWeight
     *          the ignore weight
     * @param maximumIgnoreLength
     *          the maximum ignore length
     * @param searcher
     *          the searcher
     * @param terms
     *          the terms
     * @throws IOException
     *           Signals that an I/O exception has occurred.
     */
    public SpanRecurrenceWeight(SpanWeight subWeight, SpanWeight ignoreWeight,
        Integer maximumIgnoreLength, IndexSearcher searcher,
        Map<Term, TermContext> terms) throws IOException {
      super(MtasSpanRecurrenceQuery.this, searcher, terms);
      this.subWeight = subWeight;
      this.ignoreWeight = ignoreWeight;
      this.maximumIgnoreLength = maximumIgnoreLength;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.lucene.search.spans.SpanWeight#extractTermContexts(java.util.
     * Map)
     */
    @Override
    public void extractTermContexts(Map<Term, TermContext> contexts) {
      subWeight.extractTermContexts(contexts);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.lucene.search.spans.SpanWeight#getSpans(org.apache.lucene.
     * index.LeafReaderContext,
     * org.apache.lucene.search.spans.SpanWeight.Postings)
     */
    @Override
    public Spans getSpans(LeafReaderContext context, Postings requiredPostings)
        throws IOException {
      if (field == null) {
        return null;
      } else {
        Terms terms = context.reader().terms(field);
        if (terms == null) {
          return null; // field does not exist
        }
        Spans subSpans = subWeight.getSpans(context, requiredPostings);
        if (subSpans == null) {
          return null;
        } else {
          Spans ignoreSpans = null;
          if (ignoreWeight != null) {
            ignoreSpans = ignoreWeight.getSpans(context, requiredPostings);
          }
          return new MtasSpanRecurrenceSpans(MtasSpanRecurrenceQuery.this,
              subSpans, minimumRecurrence, maximumRecurrence, ignoreSpans,
              maximumIgnoreLength);
        }
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.lucene.search.Weight#extractTerms(java.util.Set)
     */
    @Override
    public void extractTerms(Set<Term> terms) {
      subWeight.extractTerms(terms);
    }

  }

}
