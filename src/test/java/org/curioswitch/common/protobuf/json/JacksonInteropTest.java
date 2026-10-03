/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonTestProto.TestAllTypes;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.json.JsonFactory;

class JacksonInteropTest {
  @Test
  void interningDisabled() throws Exception {
    JsonFactory factory =
        JsonFactory.builder().disable(TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES).build();

    String json = JsonFormat.printer().print(JsonTestUtil.testAllTypesAllFields());

    MessageMarshaller marshaller = MessageMarshaller.builder().register(TestAllTypes.class).build();

    TestAllTypes.Builder builder = TestAllTypes.newBuilder();

    JsonParser parser = factory.createParser(ObjectReadContext.empty(), json);
    marshaller.mergeValue(parser, builder);
    assertThat(builder.build()).isEqualTo(JsonTestUtil.testAllTypesAllFields());
  }
}
