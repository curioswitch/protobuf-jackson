/*
 * Copyright (c) 2019-2022 Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import com.google.common.io.Resources;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;
import com.google.protobuf.util.JsonFormat.Printer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.curioswitch.common.protobuf.json.GithubApi.SearchResponse;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

public class GithubApiBenchmark {

  private static final MessageMarshaller MARSHALLER =
      MessageMarshaller.builder()
          .register(SearchResponse.getDefaultInstance())
          .omittingInsignificantWhitespace(true)
          .preservingProtoFieldNames(true)
          .build();

  private static final Parser UPSTREAM_PARSER = JsonFormat.parser();
  private static final Printer UPSTREAM_PRINTER =
      JsonFormat.printer().preservingProtoFieldNames().omittingInsignificantWhitespace();

  private static final String SEARCH_RESPONSE_JSON;
  private static final SearchResponse SEARCH_RESPONSE;

  private static final byte[] SEARCH_RESPONSE_PROTOBUF_BINARY;

  static {
    try {
      SEARCH_RESPONSE_JSON =
          Resources.toString(
              Resources.getResource("github_search_response.json"), StandardCharsets.UTF_8);

      SearchResponse.Builder builder = SearchResponse.newBuilder();
      UPSTREAM_PARSER.merge(SEARCH_RESPONSE_JSON, builder);
      SEARCH_RESPONSE = builder.build();
      SEARCH_RESPONSE_PROTOBUF_BINARY = SEARCH_RESPONSE.toByteArray();
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  @Benchmark
  public void marshallerParseString(Blackhole bh) throws Exception {
    SearchResponse.Builder builder = SearchResponse.newBuilder();
    MARSHALLER.mergeValue(SEARCH_RESPONSE_JSON, builder);
    bh.consume(builder);
  }

  @Benchmark
  public void marshallerWriteString(Blackhole bh) throws Exception {
    bh.consume(MARSHALLER.writeValueAsString(SEARCH_RESPONSE));
  }

  @Benchmark
  public void upstreamParseString(Blackhole bh) throws Exception {
    SearchResponse.Builder builder = SearchResponse.newBuilder();
    UPSTREAM_PARSER.merge(SEARCH_RESPONSE_JSON, builder);
    bh.consume(builder);
  }

  @Benchmark
  public void upstreamWriteString(Blackhole bh) throws Exception {
    bh.consume(UPSTREAM_PRINTER.print(SEARCH_RESPONSE));
  }

  @Benchmark
  public void protobufParseBytes(Blackhole bh) throws Exception {
    SearchResponse.Builder builder = SearchResponse.newBuilder();
    builder.mergeFrom(SEARCH_RESPONSE_PROTOBUF_BINARY);
    bh.consume(builder);
  }

  @Benchmark
  public void protobufToBytes(Blackhole bh) throws Exception {
    bh.consume(SEARCH_RESPONSE.toByteArray());
  }
}
