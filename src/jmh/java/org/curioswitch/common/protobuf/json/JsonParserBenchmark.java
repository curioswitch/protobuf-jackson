/*
 * Copyright (c) 2019-2022 Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;
import com.google.protobuf.util.JsonTestProto.TestAllTypes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

public class JsonParserBenchmark {

  private static final Parser UPSTREAM_PARSER = JsonFormat.parser();

  private static final MessageMarshaller MESSAGE_PARSER =
      MessageMarshaller.builder().register(TestAllTypes.getDefaultInstance()).build();

  private static final ByteString BINARY = JsonTestUtil.testAllTypesAllFields().toByteString();

  private static final String JSON;

  static {
    try {
      JSON = JsonFormat.printer().print(JsonTestUtil.testAllTypesAllFields());
    } catch (InvalidProtocolBufferException e) {
      throw new Error(e);
    }
  }

  @Benchmark
  public void upstreamJson(Blackhole bh) throws Exception {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    UPSTREAM_PARSER.merge(JSON, builder);
    bh.consume(builder);
  }

  @Benchmark
  public void codegenJson(Blackhole bh) throws Exception {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    MESSAGE_PARSER.mergeValue(JSON, builder);
    bh.consume(builder);
  }

  @Benchmark
  public void fromBinary(Blackhole bh) throws Exception {
    bh.consume(TestAllTypes.parseFrom(BINARY));
  }
}
