/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import java.io.IOException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.util.JsonGeneratorDelegate;

final class JsonGeneratorWrapper extends JsonGeneratorDelegate {

  static boolean maybeSerialize(Object value, JsonGenerator gen) throws IOException {
    if (!(gen instanceof JsonGeneratorWrapper)) {
      return false;
    }
    return ((JsonGeneratorWrapper) gen).maybeSerialize.serialize(value, gen);
  }

  interface MaybeSerializer {
    boolean serialize(Object value, JsonGenerator gen) throws IOException;
  }

  private final MaybeSerializer maybeSerialize;

  JsonGeneratorWrapper(JsonGenerator d, MaybeSerializer maybeSerialize) {
    super(d);
    this.maybeSerialize = maybeSerialize;
  }
}
