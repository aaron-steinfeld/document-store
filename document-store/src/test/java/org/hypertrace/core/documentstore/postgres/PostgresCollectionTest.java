package org.hypertrace.core.documentstore.postgres;

import static org.hypertrace.core.documentstore.postgres.PostgresCollection.CREATED_AT;
import static org.hypertrace.core.documentstore.postgres.PostgresCollection.DOCUMENT_ID;
import static org.hypertrace.core.documentstore.postgres.PostgresCollection.ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.sql.Connection;
import java.util.List;
import org.hypertrace.core.documentstore.Filter;
import org.hypertrace.core.documentstore.Filter.Op;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PostgresCollectionTest {

  private PostgresCollection collection;

  @BeforeEach
  public void setUp() {
    String COLLECTION_NAME = "mytest";
    Connection client = mock(Connection.class);
    collection = new PostgresCollection(client, COLLECTION_NAME);
  }

  @Test
  public void testParseNonCompositeFilter() {
    {
      Filter filter = new Filter(Filter.Op.EQ, ID, "val1");
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals(ID + " = ?", query);
    }

    {
      Filter filter = new Filter(Filter.Op.NEQ, ID, "val1");
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals(ID + " != ?", query);
    }

    {
      Filter filter = new Filter(Filter.Op.GT, ID, 5);
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals("CAST (" + ID + " AS NUMERIC) > ?", query);
    }

    {
      Filter filter = new Filter(Filter.Op.GTE, ID, 5);
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals("CAST (" + ID + " AS NUMERIC) >= ?", query);
    }

    {
      Filter filter = new Filter(Filter.Op.LT, ID, 5);
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals("CAST (" + ID + " AS NUMERIC) < ?", query);
    }

    {
      Filter filter = new Filter(Filter.Op.LTE, ID, 5);
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals("CAST (" + ID + " AS NUMERIC) <= ?", query);
    }

    {
      Filter filter = new Filter(Filter.Op.LIKE, ID, "abc");
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals(ID + " ILIKE ?", query);
    }

    {
      Filter filter = new Filter(Filter.Op.IN, ID, List.of("abc", "xyz"));
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals(ID + " IN (?, ?)", query);
    }
  }

  @Test
  public void testParseNonCompositeFilterForJsonField() {
    {
      Filter filter = new Filter(Filter.Op.EQ, "key1", "val1");
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals("document->>'key1' = ?", query);
    }

    {
      Filter filter = new Filter(Filter.Op.NEQ, "key1", "val1");
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals("document->'key1' IS NULL OR document->>'key1' != ?", query);
    }

    {
      Filter filter = new Filter(Filter.Op.GT, "key1", 5);
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals("CAST (document->>'key1' AS NUMERIC) > ?", query);
    }

    {
      Filter filter = new Filter(Filter.Op.GTE, "key1", 5);
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals("CAST (document->>'key1' AS NUMERIC) >= ?", query);
    }

    {
      Filter filter = new Filter(Filter.Op.LT, "key1", 5);
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals("CAST (document->>'key1' AS NUMERIC) < ?", query);
    }

    {
      Filter filter = new Filter(Filter.Op.LTE, "key1", 5);
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals("CAST (document->>'key1' AS NUMERIC) <= ?", query);
    }

    {
      Filter filter = new Filter(Filter.Op.LIKE, "key1", "abc");
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals("document->>'key1' ILIKE ?", query);
    }

    {
      Filter filter = new Filter(Filter.Op.IN, "key1", List.of("abc", "xyz"));
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals("document->>'key1' IN (?, ?)", query);
    }

    {
      Filter filter = new Filter(Op.NOT_IN, "key1", List.of("abc", "xyz"));
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals("document->'key1' IS NULL OR document->>'key1' NOT IN (?, ?)", query);
    }

    {
      Filter filter = new Filter(Filter.Op.EQ, DOCUMENT_ID, "k1:k2");
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals("document->>'_id' = ?", query);
    }

    {
      Filter filter = new Filter(Filter.Op.EXISTS, "key1.key2", null);
      String query = collection.parseNonCompositeFilter(filter, initParams());
      System.err.println(query);
      Assertions.assertEquals("document->'key1'->'key2' IS NOT NULL ", query);
    }

    {
      Filter filter = new Filter(Filter.Op.NOT_EXISTS, "key1", null);
      String query = collection.parseNonCompositeFilter(filter, initParams());
      Assertions.assertEquals("document->'key1' IS NULL ", query);
    }
  }

  @Test
  public void testNonCompositeFilterUnsupportedException() {
    String expectedMessage = collection.UNSUPPORTED_QUERY_OPERATION;
    {
      Filter filter = new Filter(Filter.Op.CONTAINS, "key1", null);
      String expected = String.format(expectedMessage, Filter.Op.CONTAINS);
      Exception exception =
          assertThrows(
              UnsupportedOperationException.class,
              () -> collection.parseNonCompositeFilter(filter, initParams()));
      String actualMessage = exception.getMessage();
      Assertions.assertTrue(actualMessage.contains(expected));
    }
  }

  @Test
  public void testParseQueryForCompositeFilterWithNullConditions() {
    {
      Filter filter = new Filter(Filter.Op.AND, null, null);
      Assertions.assertNull(collection.parseFilter(filter, initParams()));
    }
    {
      Filter filter = new Filter(Filter.Op.OR, null, null);
      Assertions.assertNull(collection.parseFilter(filter, initParams()));
    }
  }

  @Test
  public void testParseQueryForCompositeFilter() {
    {
      Filter filter =
          new Filter(Filter.Op.EQ, ID, "val1").and(new Filter(Filter.Op.EQ, CREATED_AT, "val2"));
      String query = collection.parseCompositeFilter(filter, initParams());
      Assertions.assertEquals(String.format("(%s = ?) AND (%s = ?)", ID, CREATED_AT), query);
    }

    {
      Filter filter =
          new Filter(Filter.Op.EQ, ID, "val1").or(new Filter(Filter.Op.EQ, CREATED_AT, "val2"));

      String query = collection.parseCompositeFilter(filter, initParams());
      Assertions.assertEquals(String.format("(%s = ?) OR (%s = ?)", ID, CREATED_AT), query);
    }
  }

  @Test
  public void testParseQueryForCompositeFilterForJsonField() {
    {
      Filter filter =
          new Filter(Filter.Op.EQ, "key1", "val1").and(new Filter(Filter.Op.EQ, "key2", "val2"));
      String query = collection.parseCompositeFilter(filter, initParams());
      Assertions.assertEquals("(document->>'key1' = ?) AND (document->>'key2' = ?)", query);
    }

    {
      Filter filter =
          new Filter(Filter.Op.EQ, "key1", "val1").or(new Filter(Filter.Op.EQ, "key2", "val2"));

      String query = collection.parseCompositeFilter(filter, initParams());
      Assertions.assertEquals("(document->>'key1' = ?) OR (document->>'key2' = ?)", query);
    }
  }

  @Test
  public void testParseNestedQuery() {
    Filter filter1 =
        new Filter(Filter.Op.EQ, ID, "val1").and(new Filter(Filter.Op.EQ, "key2", "val2"));

    Filter filter2 =
        new Filter(Filter.Op.EQ, ID, "val3").and(new Filter(Filter.Op.EQ, "key4", "val4"));

    Filter filter = filter1.or(filter2);
    String query = collection.parseFilter(filter, initParams());
    Assertions.assertEquals(
        String.format(
            "((%s = ?) AND (document->>'key2' = ?)) " + "OR ((%s = ?) AND (document->>'key4' = ?))",
            ID, ID),
        query);
  }

  @Test
  public void testJSONFieldParseNestedQuery() {
    Filter filter1 =
        new Filter(Filter.Op.EQ, "key1", "val1").and(new Filter(Filter.Op.EQ, "key2", "val2"));

    Filter filter2 =
        new Filter(Filter.Op.EQ, "key3", "val3").and(new Filter(Filter.Op.EQ, "key4", "val4"));

    Filter filter = filter1.or(filter2);
    String query = collection.parseFilter(filter, initParams());
    Assertions.assertEquals(
        "((document->>'key1' = ?) AND (document->>'key2' = ?)) "
            + "OR ((document->>'key3' = ?) AND (document->>'key4' = ?))",
        query);
  }

  private Params.Builder initParams() {
    return Params.newBuilder();
  }
}
