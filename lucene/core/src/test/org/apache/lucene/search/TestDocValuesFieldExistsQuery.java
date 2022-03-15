/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.search;

import java.io.IOException;
import org.apache.lucene.document.BinaryPoint;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

public class TestDocValuesFieldExistsQuery extends LuceneTestCase {

  public void testRewriteWithTermsPresent() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
    final int numDocs = atLeast(100);
    for (int i = 0; i < numDocs; ++i) {
      Document doc = new Document();
      doc.add(new StringField("f", random().nextBoolean() ? "yes" : "no", Store.NO));
      iw.addDocument(doc);
    }
    iw.commit();
    final IndexReader reader = iw.getReader();
    iw.close();

    assertTrue((new DocValuesFieldExistsQuery("f")).rewrite(reader) instanceof MatchAllDocsQuery);
    reader.close();
    dir.close();
  }

  public void testRewriteWithPointValuesPresent() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
    final int numDocs = atLeast(100);
    for (int i = 0; i < numDocs; ++i) {
      Document doc = new Document();
      doc.add(new BinaryPoint("dim", new byte[4], new byte[4]));
      iw.addDocument(doc);
    }
    iw.commit();
    final IndexReader reader = iw.getReader();
    iw.close();

    assertTrue((new DocValuesFieldExistsQuery("dim")).rewrite(reader) instanceof MatchAllDocsQuery);
    reader.close();
    dir.close();
  }

  public void testNoRewrite() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
    final int numDocs = atLeast(100);
    for (int i = 0; i < numDocs; ++i) {
      Document doc = new Document();
      doc.add(new BinaryPoint("dim", new byte[4], new byte[4]));
      iw.addDocument(doc);
    }
    for (int i = 0; i < numDocs; ++i) {
      Document doc = new Document();
      doc.add(new StringField("f", random().nextBoolean() ? "yes" : "no", Store.NO));
      iw.addDocument(doc);
    }
    iw.commit();
    final IndexReader reader = iw.getReader();
    iw.close();

    assertFalse(
        (new DocValuesFieldExistsQuery("dim")).rewrite(reader) instanceof MatchAllDocsQuery);
    assertFalse((new DocValuesFieldExistsQuery("f")).rewrite(reader) instanceof MatchAllDocsQuery);
    reader.close();
    dir.close();
  }

  public void testNoRewriteWithDocValues() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
    final int numDocs = atLeast(100);
    for (int i = 0; i < numDocs; ++i) {
      Document doc = new Document();
      doc.add(new NumericDocValuesField("dv1", 1));
      doc.add(new SortedNumericDocValuesField("dv2", 1));
      doc.add(new SortedNumericDocValuesField("dv2", 2));
      iw.addDocument(doc);
    }
    iw.commit();
    final IndexReader reader = iw.getReader();
    iw.close();

    assertFalse(
        (new DocValuesFieldExistsQuery("dv1")).rewrite(reader) instanceof MatchAllDocsQuery);
    assertFalse(
        (new DocValuesFieldExistsQuery("dv2")).rewrite(reader) instanceof MatchAllDocsQuery);
    assertFalse(
        (new DocValuesFieldExistsQuery("dv3")).rewrite(reader) instanceof MatchAllDocsQuery);
    reader.close();
    dir.close();
  }

  public void testRandom() throws IOException {
    final int iters = atLeast(10);
    for (int iter = 0; iter < iters; ++iter) {
      Directory dir = newDirectory();
      RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
      final int numDocs = atLeast(100);
      for (int i = 0; i < numDocs; ++i) {
        Document doc = new Document();
        final boolean hasValue = random().nextBoolean();
        if (hasValue) {
          doc.add(new NumericDocValuesField("dv1", 1));
          doc.add(new SortedNumericDocValuesField("dv2", 1));
          doc.add(new SortedNumericDocValuesField("dv2", 2));
          doc.add(new StringField("has_value", "yes", Store.NO));
        }
        doc.add(new StringField("f", random().nextBoolean() ? "yes" : "no", Store.NO));
        iw.addDocument(doc);
      }
      if (random().nextBoolean()) {
        iw.deleteDocuments(new TermQuery(new Term("f", "no")));
      }
      iw.commit();
      final IndexReader reader = iw.getReader();
      final IndexSearcher searcher = newSearcher(reader);
      iw.close();

      assertSameMatches(
          searcher,
          new TermQuery(new Term("has_value", "yes")),
          new DocValuesFieldExistsQuery("dv1"),
          false);
      assertSameMatches(
          searcher,
          new TermQuery(new Term("has_value", "yes")),
          new DocValuesFieldExistsQuery("dv2"),
          false);

      reader.close();
      dir.close();
    }
  }

  public void testApproximation() throws IOException {
    final int iters = atLeast(10);
    for (int iter = 0; iter < iters; ++iter) {
      Directory dir = newDirectory();
      RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
      final int numDocs = atLeast(100);
      for (int i = 0; i < numDocs; ++i) {
        Document doc = new Document();
        final boolean hasValue = random().nextBoolean();
        if (hasValue) {
          doc.add(new NumericDocValuesField("dv1", 1));
          doc.add(new SortedNumericDocValuesField("dv2", 1));
          doc.add(new SortedNumericDocValuesField("dv2", 2));
          doc.add(new StringField("has_value", "yes", Store.NO));
        }
        doc.add(new StringField("f", random().nextBoolean() ? "yes" : "no", Store.NO));
        iw.addDocument(doc);
      }
      if (random().nextBoolean()) {
        iw.deleteDocuments(new TermQuery(new Term("f", "no")));
      }
      iw.commit();
      final IndexReader reader = iw.getReader();
      final IndexSearcher searcher = newSearcher(reader);
      iw.close();

      BooleanQuery.Builder ref = new BooleanQuery.Builder();
      ref.add(new TermQuery(new Term("f", "yes")), Occur.MUST);
      ref.add(new TermQuery(new Term("has_value", "yes")), Occur.FILTER);

      BooleanQuery.Builder bq1 = new BooleanQuery.Builder();
      bq1.add(new TermQuery(new Term("f", "yes")), Occur.MUST);
      bq1.add(new DocValuesFieldExistsQuery("dv1"), Occur.FILTER);
      assertSameMatches(searcher, ref.build(), bq1.build(), true);

      BooleanQuery.Builder bq2 = new BooleanQuery.Builder();
      bq2.add(new TermQuery(new Term("f", "yes")), Occur.MUST);
      bq2.add(new DocValuesFieldExistsQuery("dv2"), Occur.FILTER);
      assertSameMatches(searcher, ref.build(), bq2.build(), true);

      reader.close();
      dir.close();
    }
  }

  public void testScore() throws IOException {
    final int iters = atLeast(10);
    for (int iter = 0; iter < iters; ++iter) {
      Directory dir = newDirectory();
      RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
      final int numDocs = atLeast(100);
      for (int i = 0; i < numDocs; ++i) {
        Document doc = new Document();
        final boolean hasValue = random().nextBoolean();
        if (hasValue) {
          doc.add(new NumericDocValuesField("dv1", 1));
          doc.add(new SortedNumericDocValuesField("dv2", 1));
          doc.add(new SortedNumericDocValuesField("dv2", 2));
          doc.add(new StringField("has_value", "yes", Store.NO));
        }
        doc.add(new StringField("f", random().nextBoolean() ? "yes" : "no", Store.NO));
        iw.addDocument(doc);
      }
      if (random().nextBoolean()) {
        iw.deleteDocuments(new TermQuery(new Term("f", "no")));
      }
      iw.commit();
      final IndexReader reader = iw.getReader();
      final IndexSearcher searcher = newSearcher(reader);
      iw.close();

      final float boost = random().nextFloat() * 10;
      final Query ref =
          new BoostQuery(
              new ConstantScoreQuery(new TermQuery(new Term("has_value", "yes"))), boost);

      final Query q1 = new BoostQuery(new DocValuesFieldExistsQuery("dv1"), boost);
      assertSameMatches(searcher, ref, q1, true);

      final Query q2 = new BoostQuery(new DocValuesFieldExistsQuery("dv2"), boost);
      assertSameMatches(searcher, ref, q2, true);

      reader.close();
      dir.close();
    }
  }

  public void testMissingField() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
    iw.addDocument(new Document());
    iw.commit();
    final IndexReader reader = iw.getReader();
    final IndexSearcher searcher = newSearcher(reader);
    iw.close();
    assertEquals(0, searcher.count(new DocValuesFieldExistsQuery("f")));
    reader.close();
    dir.close();
  }

  public void testAllDocsHaveField() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(new NumericDocValuesField("f", 1));
    iw.addDocument(doc);
    iw.commit();
    final IndexReader reader = iw.getReader();
    final IndexSearcher searcher = newSearcher(reader);
    iw.close();
    assertEquals(1, searcher.count(new DocValuesFieldExistsQuery("f")));
    reader.close();
    dir.close();
  }

  public void testFieldExistsButNoDocsHaveField() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random(), dir);
    // 1st segment has the field, but 2nd one does not
    Document doc = new Document();
    doc.add(new NumericDocValuesField("f", 1));
    iw.addDocument(doc);
    iw.commit();
    iw.addDocument(new Document());
    iw.commit();
    final IndexReader reader = iw.getReader();
    final IndexSearcher searcher = newSearcher(reader);
    iw.close();
    assertEquals(1, searcher.count(new DocValuesFieldExistsQuery("f")));
    reader.close();
    dir.close();
  }

  public void testQueryMatchesCount() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);

    int randomNumDocs = TestUtil.nextInt(random(), 10, 100);
    int numMatchingDocs = 0;

    for (int i = 0; i < randomNumDocs; i++) {
      Document doc = new Document();
      // ensure we index at least a document with long between 0 and 10
      if (i == 0 || random().nextBoolean()) {
        doc.add(new LongPoint("long", i));
        doc.add(new NumericDocValuesField("long", i));
        doc.add(new StringField("string", "value", Store.NO));
        doc.add(new SortedDocValuesField("string", new BytesRef("value")));
        numMatchingDocs++;
      }
      w.addDocument(doc);
    }
    w.forceMerge(1);

    DirectoryReader reader = w.getReader();
    final IndexSearcher searcher = new IndexSearcher(reader);

    assertSameCount(reader, searcher, "long", numMatchingDocs);
    assertSameCount(reader, searcher, "string", numMatchingDocs);
    assertSameCount(reader, searcher, "doesNotExist", 0);

    // Test that we can't count in O(1) when there are deleted documents
    w.w.getConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    w.deleteDocuments(LongPoint.newRangeQuery("long", 0L, 10L));
    DirectoryReader reader2 = w.getReader();
    final IndexSearcher searcher2 = new IndexSearcher(reader2);
    final Query testQuery = new DocValuesFieldExistsQuery("long");
    final Weight weight2 = searcher2.createWeight(testQuery, ScoreMode.COMPLETE, 1);
    assertEquals(weight2.count(reader2.leaves().get(0)), -1);

    IOUtils.close(reader, reader2, w, dir);
  }

  private void assertSameCount(
      IndexReader reader, IndexSearcher searcher, String field, int numMatchingDocs)
      throws IOException {
    final Query testQuery = new DocValuesFieldExistsQuery(field);
    assertEquals(searcher.count(testQuery), numMatchingDocs);
    final Weight weight = searcher.createWeight(testQuery, ScoreMode.COMPLETE, 1);
    assertEquals(weight.count(reader.leaves().get(0)), numMatchingDocs);
  }

  private void assertSameMatches(IndexSearcher searcher, Query q1, Query q2, boolean scores)
      throws IOException {
    final int maxDoc = searcher.getIndexReader().maxDoc();
    final TopDocs td1 = searcher.search(q1, maxDoc, scores ? Sort.RELEVANCE : Sort.INDEXORDER);
    final TopDocs td2 = searcher.search(q2, maxDoc, scores ? Sort.RELEVANCE : Sort.INDEXORDER);
    assertEquals(td1.totalHits.value, td2.totalHits.value);
    for (int i = 0; i < td1.scoreDocs.length; ++i) {
      assertEquals(td1.scoreDocs[i].doc, td2.scoreDocs[i].doc);
      if (scores) {
        assertEquals(td1.scoreDocs[i].score, td2.scoreDocs[i].score, 10e-7);
      }
    }
  }
}
