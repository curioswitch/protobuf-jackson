/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.curioswitch.common.protobuf.json.test.GithubApi.SearchResponse;
import org.junit.jupiter.api.Test;

class GithubApiTest {

  private static final Parser UPSTREAM_PARSER = JsonFormat.parser();

  private static final byte[] SEARCH_RESPONSE_JSON;
  private static final SearchResponse SEARCH_RESPONSE;

  static {
    try {
      SEARCH_RESPONSE_JSON =
          Resources.toByteArray(Resources.getResource("github_search_response.json"));

      SearchResponse.Builder builder = SearchResponse.newBuilder();
      UPSTREAM_PARSER.merge(new String(SEARCH_RESPONSE_JSON, StandardCharsets.UTF_8), builder);
      SEARCH_RESPONSE = builder.build();
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  @Test
  void parse() {
    assertThat(SEARCH_RESPONSE).isNotNull();
  }
}
