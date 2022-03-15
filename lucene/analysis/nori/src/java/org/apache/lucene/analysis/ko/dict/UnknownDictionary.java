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
package org.apache.lucene.analysis.ko.dict;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.util.IOUtils;

/** Dictionary for unknown-word handling. */
public final class UnknownDictionary extends BinaryDictionary {
  private final CharacterDefinition characterDefinition = CharacterDefinition.getInstance();

  /**
   * Create a {@link UnknownDictionary} from an external resource path.
   *
   * @param targetMapFile where to load target map resource
   * @param posDictFile where to load POS dictionary resource
   * @param dictFile where to load dictionary entries resource
   * @throws IOException if resource was not found or broken
   */
  public UnknownDictionary(Path targetMapFile, Path posDictFile, Path dictFile) throws IOException {
    super(
        () -> Files.newInputStream(targetMapFile),
        () -> Files.newInputStream(posDictFile),
        () -> Files.newInputStream(dictFile));
  }

  private UnknownDictionary() throws IOException {
    super(
        () -> getClassResource(TARGETMAP_FILENAME_SUFFIX),
        () -> getClassResource(POSDICT_FILENAME_SUFFIX),
        () -> getClassResource(DICT_FILENAME_SUFFIX));
  }

  private static InputStream getClassResource(String suffix) throws IOException {
    final String resourcePath = UnknownDictionary.class.getSimpleName() + suffix;
    return IOUtils.requireResourceNonNull(
        UnknownDictionary.class.getResourceAsStream(resourcePath), resourcePath);
  }

  public CharacterDefinition getCharacterDefinition() {
    return characterDefinition;
  }

  public static UnknownDictionary getInstance() {
    return SingletonHolder.INSTANCE;
  }

  @Override
  public String getReading(int wordId) {
    return null;
  }

  @Override
  public Morpheme[] getMorphemes(int wordId, char[] surfaceForm, int off, int len) {
    return null;
  }

  private static class SingletonHolder {
    static final UnknownDictionary INSTANCE;

    static {
      try {
        INSTANCE = new UnknownDictionary();
      } catch (IOException ioe) {
        throw new RuntimeException("Cannot load UnknownDictionary.", ioe);
      }
    }
  }
}
