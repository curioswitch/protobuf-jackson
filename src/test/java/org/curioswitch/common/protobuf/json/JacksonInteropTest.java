/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonTestProto.TestAllTypes;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class JacksonInteropTest {
  @Test
  @Disabled // Currently we do not support non-interned field names.
  void interningDisabled() throws Exception {
    JsonFactory factory =
        new JsonFactoryBuilder().configure(JsonFactory.Feature.INTERN_FIELD_NAMES, false).build();

    String json = JsonFormat.printer().print(JsonTestUtil.testAllTypesAllFields());

    MessageMarshaller marshaller = MessageMarshaller.builder().register(TestAllTypes.class).build();

    TestAllTypes.Builder builder = TestAllTypes.newBuilder();

    JsonParser parser = factory.createParser(json);
    marshaller.mergeValue(parser, builder);
    assertThat(builder.build()).isEqualTo(JsonTestUtil.testAllTypesAllFields());
  }
}
