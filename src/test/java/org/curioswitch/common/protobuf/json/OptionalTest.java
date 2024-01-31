/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.util.JsonTestProto;
import org.junit.jupiter.api.Test;

class OptionalTest {

  @Test
  void optionalNotSet() throws Exception {
    JsonTestProto.TestAllTypes message = JsonTestProto.TestAllTypes.newBuilder().build();

    MessageMarshaller marshaller =
        MessageMarshaller.builder()
            .register(message.getClass())
            .omittingInsignificantWhitespace(true)
            .build();

    String json = marshaller.writeValueAsString(message);
    assertThat(json).isEqualTo("{}");

    MessageMarshallerTest.assertMatchesUpstream(message);
  }

  @Test
  void optionalSetToDefaultValue() throws Exception {
    JsonTestProto.TestAllTypes message =
        JsonTestProto.TestAllTypes.newBuilder().setVeryOptionalInt32(0).build();

    MessageMarshaller marshaller =
        MessageMarshaller.builder()
            .register(message.getClass())
            .omittingInsignificantWhitespace(true)
            .build();

    String json = marshaller.writeValueAsString(message);
    assertThat(json).isEqualTo("{\"veryOptionalInt32\":0}");

    MessageMarshallerTest.assertMatchesUpstream(message);
  }
}
