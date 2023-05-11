/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.util.JsonParserDelegate;
import java.io.IOException;

final class JsonParserWrapper extends JsonParserDelegate {

  static final Object NOT_DESERIALIZED = new Object();

  static Object maybeDeserialize(JsonParser p, Class<?> clz) throws IOException {
    if (!(p instanceof JsonParserWrapper)) {
      return NOT_DESERIALIZED;
    }
    return ((JsonParserWrapper) p).maybeDeserializer.deserialize(p, clz);
  }

  interface MaybeDeserializer {
    Object deserialize(JsonParser p, Class<?> clz) throws IOException;
  }

  private final MaybeDeserializer maybeDeserializer;

  JsonParserWrapper(JsonParser d, MaybeDeserializer maybeDeserializer) {
    super(d);
    this.maybeDeserializer = maybeDeserializer;
  }
}
