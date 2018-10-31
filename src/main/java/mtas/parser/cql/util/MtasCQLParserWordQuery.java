package mtas.parser.cql.util;

import mtas.analysis.token.MtasToken;
import mtas.parser.cql.ParseException;
import mtas.search.spans.MtasSpanMatchNoneQuery;
import mtas.search.spans.MtasSpanOrQuery;
import mtas.search.spans.MtasSpanPrefixQuery;
import mtas.search.spans.MtasSpanRegexpQuery;
import mtas.search.spans.MtasSpanTermQuery;
import mtas.search.spans.MtasSpanWildcardQuery;
import mtas.search.spans.util.MtasSpanQuery;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanWeight;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class MtasCQLParserWordQuery extends MtasSpanQuery {
  MtasSpanQuery query;
  Term term;

  public static final String MTAS_CQL_TERM_QUERY = "term";
  public static final String MTAS_CQL_REGEXP_QUERY = "regexp";
  public static final String MTAS_CQL_WILDCARD_QUERY = "wildcard";
  public static final String MTAS_CQL_VARIABLE_QUERY = "variable";

  public MtasCQLParserWordQuery(String field, String prefix,
      Map<String, String[]> variables) {
    super(1, 1);
    term = new Term(field, prefix + MtasToken.DELIMITER);
    query = new MtasSpanPrefixQuery(term, true);
  }

  public MtasCQLParserWordQuery(String field, String prefix, String value,
      Map<String, String[]> variables, Set<String> usedVariables)
      throws ParseException {
    this(field, prefix, value, MTAS_CQL_REGEXP_QUERY, variables, usedVariables);
  }

  public MtasCQLParserWordQuery(String field, String prefix, String value,
      String type, Map<String, String[]> variables, Set<String> usedVariables)
      throws ParseException {
    super(1, 1);
    String termBase = prefix + MtasToken.DELIMITER + value;
    if (type.equals(MTAS_CQL_REGEXP_QUERY)) {
      term = new Term(field, termBase + "\u0000*");
      query = new MtasSpanRegexpQuery(term, true);
    } else if (type.equals(MTAS_CQL_WILDCARD_QUERY)) {
      term = new Term(field, termBase);
      query = new MtasSpanWildcardQuery(term, true);
    } else if (type.equals(MTAS_CQL_TERM_QUERY)) {
      term = new Term(field,
          "\"" + termBase.replace("\"", "\"\\\"\"") + "\"\u0000*");
      query = new MtasSpanRegexpQuery(term, true);
    } else if (type.equals(MTAS_CQL_VARIABLE_QUERY)) {
      if (value != null && variables != null && variables.containsKey(value)
          && variables.get(value) != null) {
        if (usedVariables.contains(value)) {
          throw new ParseException(
              "variable $" + value + " should be used exactly one time");
        } else {
          usedVariables.add(value);
        }
        String[] list = variables.get(value);
        MtasSpanQuery[] queries = new MtasSpanQuery[list.length];
        term = new Term(field, prefix + MtasToken.DELIMITER);
        for (int i = 0; i < list.length; i++) {
          termBase = prefix + MtasToken.DELIMITER + list[i];
          term = new Term(field, "\"" + termBase + "\"\u0000*");
          queries[i] = new MtasSpanRegexpQuery(term, true);
        }
        if (queries.length == 0) {
          query = new MtasSpanMatchNoneQuery(field);
        } else if (queries.length > 1) {
          query = new MtasSpanOrQuery(queries);
        } else {
          query = queries[0];
        }
      } else {
        throw new ParseException("variable $" + value + " not defined");
      }
    } else {
      term = new Term(field, prefix + MtasToken.DELIMITER + value);
      query = new MtasSpanTermQuery(term, true);
    }
  }

  @Override
  public String getField() {
    return term.field();
  }

  @Override
  public MtasSpanQuery rewrite(IndexReader reader) throws IOException {
    return query.rewrite(reader);
  }

  @Override
  public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores, float boost)
      throws IOException {
    return query.createWeight(searcher, needsScores, boost);
  }

  @Override
  public String toString(String field) {
    return query.toString(term.field());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    final MtasCQLParserWordQuery that = (MtasCQLParserWordQuery) obj;
    return query.equals(that.query);
  }

  @Override
  public int hashCode() {
    int h = this.getClass().getSimpleName().hashCode();
    h = (h * 5) ^ term.hashCode();
    h = (h * 7) ^ query.hashCode();
    return h;
  }

  @Override
  public void disableTwoPhaseIterator() {
    super.disableTwoPhaseIterator();
    query.disableTwoPhaseIterator();
  }

  @Override
  public boolean isMatchAllPositionsQuery() {
    return false;
  }
}
