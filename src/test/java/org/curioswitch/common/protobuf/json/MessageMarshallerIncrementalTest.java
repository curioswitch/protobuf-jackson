/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.util.JsonTestProto;
import org.curioswitch.common.protobuf.json.test.GithubApi;
import org.junit.jupiter.api.Test;

class MessageMarshallerIncrementalTest {

  @Test
  void incrementalMarshaller() throws Exception {
    MessageMarshaller marshaller =
        MessageMarshaller.builder().register(GithubApi.SearchResponse.getDefaultInstance()).build();
    GithubApi.SearchResponse response =
        GithubApi.SearchResponse.newBuilder().setTotalCount(10).build();
    JsonTestProto.TestAllTypes allTypes = JsonTestUtil.testAllTypesAllFields();
    assertThat(marshaller.writeValueAsString(response)).isNotEmpty();
    assertThatThrownBy(() -> marshaller.writeValueAsString(allTypes))
        .isInstanceOf(IllegalArgumentException.class);

    MessageMarshaller marshaller2 =
        marshaller.toBuilder().register(JsonTestProto.TestAllTypes.getDefaultInstance()).build();
    assertThat(marshaller2.writeValueAsString(response)).isNotEmpty();
    assertThat(marshaller2.writeValueAsString(allTypes)).isNotEmpty();
  }
}
