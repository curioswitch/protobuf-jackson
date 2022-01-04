/*
 * Copyright (c) 2019-2022 Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Printer;
import com.google.protobuf.util.JsonTestProto.TestAllTypes;
import java.nio.charset.StandardCharsets;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

public class JsonSerializeBenchmark {

  private static final Printer PRINTER = JsonFormat.printer();

  private static final TestAllTypes MESSAGE = JsonTestUtil.testAllTypesAllFields();

  private static final MessageMarshaller SERIALIZER =
      MessageMarshaller.builder().register(TestAllTypes.getDefaultInstance()).build();

  @Benchmark
  public void upstreamJson(Blackhole bh) throws Exception {
    bh.consume(PRINTER.print(MESSAGE).getBytes(StandardCharsets.UTF_8));
  }

  @Benchmark
  public void codegenJson(Blackhole bh) throws Exception {
    bh.consume(SERIALIZER.writeValueAsBytes(MESSAGE));
  }

  @Benchmark
  public void toBytes(Blackhole bh) throws Exception {
    bh.consume(MESSAGE.toByteArray());
  }
}
