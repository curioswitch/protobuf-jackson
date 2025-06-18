/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.offset;

import com.google.common.base.Strings;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.FieldMaskUtil;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Printer;
import com.google.protobuf.util.JsonFormat.TypeRegistry;
import com.google.protobuf.util.JsonTestProto.TestAllTypes;
import com.google.protobuf.util.JsonTestProto.TestAllTypes.AliasedEnum;
import com.google.protobuf.util.JsonTestProto.TestAllTypes.NestedEnum;
import com.google.protobuf.util.JsonTestProto.TestAny;
import com.google.protobuf.util.JsonTestProto.TestCustomJsonName;
import com.google.protobuf.util.JsonTestProto.TestDuration;
import com.google.protobuf.util.JsonTestProto.TestFieldMask;
import com.google.protobuf.util.JsonTestProto.TestFieldOrder;
import com.google.protobuf.util.JsonTestProto.TestMap;
import com.google.protobuf.util.JsonTestProto.TestOneof;
import com.google.protobuf.util.JsonTestProto.TestRecursive;
import com.google.protobuf.util.JsonTestProto.TestRegression;
import com.google.protobuf.util.JsonTestProto.TestStruct;
import com.google.protobuf.util.JsonTestProto.TestTimestamp;
import com.google.protobuf.util.JsonTestProto.TestWrappers;
import com.google.protobuf.util.Timestamps;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageMarshallerTest {

  static final Method alwaysPrintFieldsWithNoPresence;

  static {
    Method method;
    try {
      method = Printer.class.getMethod("alwaysPrintFieldsWithNoPresence");
    } catch (NoSuchMethodException e) {
      try {
        method = Printer.class.getMethod("includingDefaultValueFields");
      } catch (NoSuchMethodException unused) {
        throw new IllegalStateException("Could not find alwaysPrintFieldsWithPresence method.", e);
      }
    }
    alwaysPrintFieldsWithNoPresence = method;
  }

  @Test
  void allFields() throws Exception {
    assertMatchesUpstream(JsonTestUtil.testAllTypesAllFields());
  }

  @Test
  void unknownEnumValue() throws Exception {
    TestAllTypes message =
        TestAllTypes.newBuilder()
            .setOptionalNestedEnumValue(12345)
            .addRepeatedNestedEnumValue(12345)
            .addRepeatedNestedEnumValue(0)
            .build();
    assertMatchesUpstream(message);

    TestMap.Builder mapBuilder = TestMap.newBuilder();
    mapBuilder.putInt32ToEnumMapValue(1, 0);
    Map<Integer, Integer> mapWithInvalidValues = new HashMap<>();
    mapWithInvalidValues.put(2, 12345);
    mapBuilder.putAllInt32ToEnumMapValue(mapWithInvalidValues);
    TestMap mapMessage = mapBuilder.build();
    assertMatchesUpstream(mapMessage);
  }

  @Test
  void specialFloatValues() throws Exception {
    TestAllTypes message =
        TestAllTypes.newBuilder()
            .addRepeatedFloat(Float.NaN)
            .addRepeatedFloat(Float.POSITIVE_INFINITY)
            .addRepeatedFloat(Float.NEGATIVE_INFINITY)
            .addRepeatedDouble(Double.NaN)
            .addRepeatedDouble(Double.POSITIVE_INFINITY)
            .addRepeatedDouble(Double.NEGATIVE_INFINITY)
            .build();
    assertMatchesUpstream(message);
  }

  @Test
  void parserAcceptsStringForNumericField() throws Exception {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    mergeFromJson(
        "{\n"
            + "  \"optionalInt32\": \"1234\",\n"
            + "  \"optionalUint32\": \"5678\",\n"
            + "  \"optionalSint32\": \"9012\",\n"
            + "  \"optionalFixed32\": \"3456\",\n"
            + "  \"optionalSfixed32\": \"7890\",\n"
            + "  \"optionalFloat\": \"1.5\",\n"
            + "  \"optionalDouble\": \"1.25\",\n"
            + "  \"optionalBool\": \"true\"\n"
            + "}",
        builder);
    TestAllTypes message = builder.build();
    assertThat(message.getOptionalInt32()).isEqualTo(1234);
    assertThat(message.getOptionalUint32()).isEqualTo(5678);
    assertThat(message.getOptionalSint32()).isEqualTo(9012);
    assertThat(message.getOptionalFixed32()).isEqualTo(3456);
    assertThat(message.getOptionalSfixed32()).isEqualTo(7890);
    assertThat(message.getOptionalFloat()).isCloseTo(1.5f, offset(0.000001f));
    assertThat(message.getOptionalDouble()).isCloseTo(1.25, offset(0.000001));
    assertThat(message.getOptionalBool()).isTrue();
  }

  @Test
  void parserAcceptsFloatingPointValueForIntegerField() throws Exception {
    // Test that numeric values like "1.000", "1e5" will also be accepted.
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    mergeFromJson(
        "{\n"
            + "  \"repeatedInt32\": [1.000, 1e5, \"1.000\", \"1e5\"],\n"
            + "  \"repeatedUint32\": [1.000, 1e5, \"1.000\", \"1e5\"],\n"
            + "  \"repeatedInt64\": [1.000, 1e5, \"1.000\", \"1e5\"],\n"
            + "  \"repeatedUint64\": [1.000, 1e5, \"1.000\", \"1e5\"]\n"
            + "}",
        builder);
    assertThat(builder.getRepeatedInt32List()).containsExactly(1, 100000, 1, 100000);
    assertThat(builder.getRepeatedUint32List()).containsExactly(1, 100000, 1, 100000);
    assertThat(builder.getRepeatedInt64List()).containsExactly(1L, 100000L, 1L, 100000L);
    assertThat(builder.getRepeatedUint64List()).containsExactly(1L, 100000L, 1L, 100000L);

    // Non-integers will still be rejected.
    assertRejects("optionalInt32", "1.5");
    assertRejects("optionalUint32", "1.5");
    assertRejects("optionalInt64", "1.5");
    assertRejects("optionalUint64", "1.5");
  }

  private static void assertRejects(String name, String value) {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    // Numeric form is rejected.
    assertThatThrownBy(() -> mergeFromJson("{\"" + name + "\":" + value + "}", builder))
        .isInstanceOf(IOException.class);
    // String form is also rejected.
    assertThatThrownBy(() -> mergeFromJson("{\"" + name + "\":\"" + value + "\"}", builder))
        .isInstanceOf(IOException.class);
  }

  private static void assertAccepts(String name, String value) throws IOException {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    // Both numeric form and string form are accepted.
    mergeFromJson("{\"" + name + "\":" + value + "}", builder);
    builder.clear();
    mergeFromJson("{\"" + name + "\":\"" + value + "\"}", builder);
  }

  @Test
  void parserRejectOutOfRangeNumericValues() throws Exception {
    assertAccepts("optionalInt32", String.valueOf(Integer.MAX_VALUE));
    assertAccepts("optionalInt32", String.valueOf(Integer.MIN_VALUE));
    assertRejects("optionalInt32", String.valueOf(Integer.MAX_VALUE + 1L));
    assertRejects("optionalInt32", String.valueOf(Integer.MIN_VALUE - 1L));

    assertAccepts("optionalUint32", String.valueOf(Integer.MAX_VALUE + 1L));
    assertRejects("optionalUint32", "123456789012345");
    assertRejects("optionalUint32", "-1");

    BigInteger one = new BigInteger("1");
    BigInteger maxLong = new BigInteger(String.valueOf(Long.MAX_VALUE));
    BigInteger minLong = new BigInteger(String.valueOf(Long.MIN_VALUE));
    assertAccepts("optionalInt64", maxLong.toString());
    assertAccepts("optionalInt64", minLong.toString());
    assertRejects("optionalInt64", maxLong.add(one).toString());
    assertRejects("optionalInt64", minLong.subtract(one).toString());

    assertAccepts("optionalUint64", maxLong.add(one).toString());
    assertRejects("optionalUint64", "1234567890123456789012345");
    assertRejects("optionalUint64", "-1");

    assertAccepts("optionalBool", "true");
    assertRejects("optionalBool", "1");
    assertRejects("optionalBool", "0");

    assertAccepts("optionalFloat", String.valueOf(Float.MAX_VALUE));
    assertAccepts("optionalFloat", String.valueOf(-Float.MAX_VALUE));
    assertRejects("optionalFloat", String.valueOf(Double.MAX_VALUE));
    assertRejects("optionalFloat", String.valueOf(-Double.MAX_VALUE));

    BigDecimal moreThanOne = new BigDecimal("1.000001");
    BigDecimal maxDouble = new BigDecimal(Double.MAX_VALUE);
    BigDecimal minDouble = new BigDecimal(-Double.MAX_VALUE);
    assertAccepts("optionalDouble", maxDouble.toString());
    assertAccepts("optionalDouble", minDouble.toString());
    assertRejects("optionalDouble", maxDouble.multiply(moreThanOne).toString());
    assertRejects("optionalDouble", minDouble.multiply(moreThanOne).toString());
  }

  @Test
  void parserAcceptsNull() throws Exception {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    mergeFromJson(
        "{\n"
            + "  \"optionalInt32\": null,\n"
            + "  \"optionalInt64\": null,\n"
            + "  \"optionalUint32\": null,\n"
            + "  \"optionalUint64\": null,\n"
            + "  \"optionalSint32\": null,\n"
            + "  \"optionalSint64\": null,\n"
            + "  \"optionalFixed32\": null,\n"
            + "  \"optionalFixed64\": null,\n"
            + "  \"optionalSfixed32\": null,\n"
            + "  \"optionalSfixed64\": null,\n"
            + "  \"optionalFloat\": null,\n"
            + "  \"optionalDouble\": null,\n"
            + "  \"optionalBool\": null,\n"
            + "  \"optionalString\": null,\n"
            + "  \"optionalBytes\": null,\n"
            + "  \"optionalNestedMessage\": null,\n"
            + "  \"optionalNestedEnum\": null,\n"
            + "  \"repeatedInt32\": null,\n"
            + "  \"repeatedInt64\": null,\n"
            + "  \"repeatedUint32\": null,\n"
            + "  \"repeatedUint64\": null,\n"
            + "  \"repeatedSint32\": null,\n"
            + "  \"repeatedSint64\": null,\n"
            + "  \"repeatedFixed32\": null,\n"
            + "  \"repeatedFixed64\": null,\n"
            + "  \"repeatedSfixed32\": null,\n"
            + "  \"repeatedSfixed64\": null,\n"
            + "  \"repeatedFloat\": null,\n"
            + "  \"repeatedDouble\": null,\n"
            + "  \"repeatedBool\": null,\n"
            + "  \"repeatedString\": null,\n"
            + "  \"repeatedBytes\": null,\n"
            + "  \"repeatedNestedMessage\": null,\n"
            + "  \"repeatedNestedEnum\": null\n"
            + "}",
        builder);
    TestAllTypes message = builder.build();
    assertThat(message).isEqualTo(TestAllTypes.getDefaultInstance());

    // Repeated field elements cannot be null.
    TestAllTypes.Builder builder2 = TestAllTypes.newBuilder();
    assertThatThrownBy(
            () -> mergeFromJson("{\n" + "  \"repeatedInt32\": [null, null],\n" + "}", builder2))
        .isInstanceOf(IOException.class);

    TestAllTypes.Builder builder3 = TestAllTypes.newBuilder();
    assertThatThrownBy(
            () ->
                mergeFromJson(
                    "{\n" + "  \"repeatedNestedMessage\": [null, null],\n" + "}", builder3))
        .isInstanceOf(IOException.class);
  }

  @Test
  void nullInOneOf() throws Exception {
    TestOneof.Builder builder = TestOneof.newBuilder();
    mergeFromJson("{\n" + "  \"oneofNullValue\": null \n" + "}", builder);
    TestOneof message = builder.build();
    assertThat(message.getOneofFieldCase()).isEqualTo(TestOneof.OneofFieldCase.ONEOF_NULL_VALUE);
    assertThat(message.getOneofNullValue()).isEqualTo(NullValue.NULL_VALUE);
  }

  @Test
  void testNullFirstInDuplicateOneof() throws Exception {
    TestOneof.Builder builder = TestOneof.newBuilder();
    mergeFromJson("{\"oneofNestedMessage\": null, \"oneofInt32\": 1}", builder);
    TestOneof message = builder.build();
    assertThat(message.getOneofInt32()).isEqualTo(1);
  }

  @Test
  void testNullLastInDuplicateOneof() throws Exception {
    TestOneof.Builder builder = TestOneof.newBuilder();
    mergeFromJson("{\"oneofInt32\": 1, \"oneofNestedMessage\": null}", builder);
    TestOneof message = builder.build();
    assertThat(message.getOneofInt32()).isEqualTo(1);
  }

  @Test
  void parserRejectDuplicatedFields() throws Exception {
    // NOTE: Upstream parser does not correctly reject duplicates with the same field variableName,
    // only when json variableName and proto variableName are both specified. We handle both cases.
    // Also, since
    // we keep track based on field number, the logic is much simpler so most of these tests
    // are redundant and just preserved to maintain parity with upstream.

    // We also actually do a mergeValue as the method indicates. If the field is already set in the
    // input, it will be overwritten. We only want to ensure a valid JSON input.

    // Duplicated optional fields.
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    assertThatThrownBy(
            () ->
                mergeFromJson(
                    "{\n"
                        + "  \"optionalNestedMessage\": {},\n"
                        + "  \"optional_nested_message\": {}\n"
                        + "}",
                    builder))
        .isInstanceOf(InvalidProtocolBufferException.class);
    builder.clear();
    assertThatThrownBy(
            () ->
                mergeFromJson(
                    "{\n"
                        + "  \"optionalNestedMessage\": {},\n"
                        + "  \"optionalNestedMessage\": {}\n"
                        + "}",
                    builder))
        .isInstanceOf(InvalidProtocolBufferException.class);

    // Duplicated repeated fields.
    builder.clear();
    assertThatThrownBy(
            () ->
                mergeFromJson(
                    "{\n"
                        + "  \"repeatedInt32\": [1, 2],\n"
                        + "  \"repeated_int32\": [5, 6]\n"
                        + "}",
                    builder))
        .isInstanceOf(InvalidProtocolBufferException.class);
    builder.clear();
    assertThatThrownBy(
            () ->
                mergeFromJson(
                    "{\n"
                        + "  \"repeatedInt32\": [1, 2],\n"
                        + "  \"repeatedInt32\": [5, 6]\n"
                        + "}",
                    builder))
        .isInstanceOf(InvalidProtocolBufferException.class);

    // Duplicated oneof fields, same variableName.
    TestOneof.Builder builder2 = TestOneof.newBuilder();
    assertThatThrownBy(
            () ->
                mergeFromJson(
                    "{\n" + "  \"oneofInt32\": 1,\n" + "  \"oneof_int32\": 2\n" + "}", builder2))
        .isInstanceOf(InvalidProtocolBufferException.class);
    builder2.clear();
    assertThatThrownBy(
            () ->
                mergeFromJson(
                    "{\n" + "  \"oneofInt32\": 1,\n" + "  \"oneofInt32\": 2\n" + "}", builder2))
        .isInstanceOf(InvalidProtocolBufferException.class);

    // Duplicated oneof fields, different variableName.
    builder2.clear();
    assertThatThrownBy(
            () ->
                mergeFromJson(
                    "{\n" + "  \"oneofInt32\": 1,\n" + "  \"oneofNullValue\": null\n" + "}",
                    builder2))
        .isInstanceOf(InvalidProtocolBufferException.class);
  }

  @Test
  void mapFields() throws Exception {
    assertMatchesUpstream(JsonTestUtil.testMapAllTypes());

    TestMap message =
        TestMap.newBuilder().putInt32ToInt32Map(1, 2).putInt32ToInt32Map(3, 4).build();
    assertMatchesUpstream(message);
  }

  @Test
  void mapNullValueIsRejected() throws Exception {
    TestMap.Builder builder = TestMap.newBuilder();
    assertThatThrownBy(
            () ->
                mergeFromJson(
                    "{\n"
                        + "  \"int32ToInt32Map\": {null: 1},\n"
                        + "  \"int32ToMessageMap\": {null: 2}\n"
                        + "}",
                    builder))
        .isInstanceOf(InvalidProtocolBufferException.class);

    TestMap.Builder builder2 = TestMap.newBuilder();
    assertThatThrownBy(
            () ->
                mergeFromJson(
                    "{\n"
                        + "  \"int32ToInt32Map\": {\"1\": null},\n"
                        + "  \"int32ToMessageMap\": {\"2\": null}\n"
                        + "}",
                    builder2))
        .isInstanceOf(InvalidProtocolBufferException.class);
  }

  @Test
  void mapEnumNullValueIsIgnored() throws Exception {
    TestMap.Builder builder = TestMap.newBuilder();
    mergeFromJson(
        /* ignoringUnknownFields= */ true,
        "{\n" + "  \"int32ToEnumMap\": {\"1\": null}\n" + "}",
        builder);
    TestMap map = builder.build();
    assertThat(map.getInt32ToEnumMapMap()).isEmpty();
  }

  @Test
  void parserAcceptsNonQuotedObjectKey() throws Exception {
    TestMap.Builder builder = TestMap.newBuilder();
    mergeFromJson(
        "{\n" + "  int32ToInt32Map: {1: 2},\n" + "  stringToInt32Map: {hello: 3}\n" + "}", builder);
    TestMap message = builder.build();
    assertThat(message.getInt32ToInt32MapMap().get(1).intValue()).isEqualTo(2);
    assertThat(message.getStringToInt32MapMap().get("hello").intValue()).isEqualTo(3);
  }

  @Test
  void wrappers() throws Exception {
    TestWrappers.Builder builder = TestWrappers.newBuilder();
    builder.getBoolValueBuilder().setValue(false);
    builder.getInt32ValueBuilder().setValue(0);
    builder.getInt64ValueBuilder().setValue(0);
    builder.getUint32ValueBuilder().setValue(0);
    builder.getUint64ValueBuilder().setValue(0);
    builder.getFloatValueBuilder().setValue(0.0f);
    builder.getDoubleValueBuilder().setValue(0.0);
    builder.getStringValueBuilder().setValue("");
    builder.getBytesValueBuilder().setValue(ByteString.EMPTY);
    TestWrappers message = builder.build();
    assertMatchesUpstream(message);

    builder = TestWrappers.newBuilder();
    builder.getBoolValueBuilder().setValue(true);
    builder.getInt32ValueBuilder().setValue(1);
    builder.getInt64ValueBuilder().setValue(2);
    builder.getUint32ValueBuilder().setValue(3);
    builder.getUint64ValueBuilder().setValue(4);
    builder.getFloatValueBuilder().setValue(5.0f);
    builder.getDoubleValueBuilder().setValue(6.0);
    builder.getStringValueBuilder().setValue("7");
    builder.getBytesValueBuilder().setValue(ByteString.copyFrom(new byte[] {8}));
    message = builder.build();
    assertMatchesUpstream(message);
  }

  @Test
  void timestamp() throws Exception {
    TestTimestamp message =
        TestTimestamp.newBuilder()
            .setTimestampValue(Timestamps.parse("1970-01-01T00:00:00Z"))
            .build();
    assertMatchesUpstream(message);
  }

  @Test
  void duration() throws Exception {
    TestDuration message =
        TestDuration.newBuilder().setDurationValue(Durations.parse("12345s")).build();
    assertMatchesUpstream(message);
  }

  @Test
  void fieldMask() throws Exception {
    TestFieldMask message =
        TestFieldMask.newBuilder()
            .setFieldMaskValue(FieldMaskUtil.fromString("foo.bar,baz,foo_bar.baz"))
            .build();
    assertMatchesUpstream(message);
  }

  @Test
  void struct() throws Exception {
    // Build a struct with all possible values.
    TestStruct.Builder builder = TestStruct.newBuilder();
    Struct.Builder structBuilder = builder.getStructValueBuilder();
    structBuilder.putFields("null_value", Value.newBuilder().setNullValueValue(0).build());
    structBuilder.putFields("number_value", Value.newBuilder().setNumberValue(1.25).build());
    structBuilder.putFields("string_value", Value.newBuilder().setStringValue("hello").build());
    Struct.Builder subStructBuilder = Struct.newBuilder();
    subStructBuilder.putFields("number_value", Value.newBuilder().setNumberValue(1234).build());
    structBuilder.putFields(
        "struct_value", Value.newBuilder().setStructValue(subStructBuilder.build()).build());
    ListValue.Builder listBuilder = ListValue.newBuilder();
    listBuilder.addValues(Value.newBuilder().setNumberValue(1.125).build());
    listBuilder.addValues(Value.newBuilder().setNullValueValue(0).build());
    structBuilder.putFields(
        "list_value", Value.newBuilder().setListValue(listBuilder.build()).build());
    TestStruct message = builder.build();
    assertMatchesUpstream(message);

    builder = TestStruct.newBuilder();
    builder.setValue(Value.newBuilder().setNullValueValue(0).build());
    message = builder.build();
    assertMatchesUpstream(message);

    builder = TestStruct.newBuilder();
    listBuilder = builder.getListValueBuilder();
    listBuilder.addValues(Value.newBuilder().setNumberValue(31831.125).build());
    listBuilder.addValues(Value.newBuilder().setNullValueValue(0).build());
    message = builder.build();
    assertMatchesUpstream(message);
  }

  @Test
  void anyFields() throws Exception {
    TestAllTypes content = TestAllTypes.newBuilder().setOptionalInt32(1234).build();
    TestAny message = TestAny.newBuilder().setAnyValue(Any.pack(content)).build();
    assertMatchesUpstream(message, TestAllTypes.getDefaultInstance());

    TestAny messageWithDefaultAnyValue =
        TestAny.newBuilder().setAnyValue(Any.getDefaultInstance()).build();
    assertMatchesUpstream(messageWithDefaultAnyValue);

    // Well-known types have a special formatting when embedded in Any.
    //
    // 1. Any in Any.
    Any anyMessage = Any.pack(Any.pack(content));
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());

    // 2. Wrappers in Any.
    anyMessage = Any.pack(Int32Value.newBuilder().setValue(12345).build());
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());
    anyMessage = Any.pack(UInt32Value.newBuilder().setValue(12345).build());
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());
    anyMessage = Any.pack(Int64Value.newBuilder().setValue(12345).build());
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());
    anyMessage = Any.pack(UInt64Value.newBuilder().setValue(12345).build());
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());
    anyMessage = Any.pack(FloatValue.newBuilder().setValue(12345).build());
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());
    anyMessage = Any.pack(DoubleValue.newBuilder().setValue(12345).build());
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());
    anyMessage = Any.pack(BoolValue.newBuilder().setValue(true).build());
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());
    anyMessage = Any.pack(StringValue.newBuilder().setValue("Hello").build());
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());
    anyMessage =
        Any.pack(BytesValue.newBuilder().setValue(ByteString.copyFrom(new byte[] {1, 2})).build());
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());

    // 3. Timestamp in Any.
    anyMessage = Any.pack(Timestamps.parse("1969-12-31T23:59:59Z"));
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());

    // 4. Duration in Any
    anyMessage = Any.pack(Durations.parse("12345.10s"));
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());

    // 5. FieldMask in Any
    anyMessage = Any.pack(FieldMaskUtil.fromString("foo.bar,baz"));
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());

    // 6. Struct in Any
    Struct.Builder structBuilder = Struct.newBuilder();
    structBuilder.putFields("number", Value.newBuilder().setNumberValue(1.125).build());
    anyMessage = Any.pack(structBuilder.build());
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());

    // 7. Value (number type) in Any
    Value.Builder valueBuilder = Value.newBuilder();
    valueBuilder.setNumberValue(1);
    anyMessage = Any.pack(valueBuilder.build());
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());

    // 8. Value (null type) in Any
    anyMessage = Any.pack(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());
    assertMatchesUpstream(anyMessage, TestAllTypes.getDefaultInstance());
  }

  @Test
  void anyInMaps() throws Exception {
    TestAny.Builder testAny = TestAny.newBuilder();
    testAny.putAnyMap("int32_wrapper", Any.pack(Int32Value.newBuilder().setValue(123).build()));
    testAny.putAnyMap("int64_wrapper", Any.pack(Int64Value.newBuilder().setValue(456).build()));
    testAny.putAnyMap("timestamp", Any.pack(Timestamps.parse("1969-12-31T23:59:59Z")));
    testAny.putAnyMap("duration", Any.pack(Durations.parse("12345.1s")));
    testAny.putAnyMap("field_mask", Any.pack(FieldMaskUtil.fromString("foo.bar,baz")));
    Value numberValue = Value.newBuilder().setNumberValue(1.125).build();
    Struct.Builder struct = Struct.newBuilder();
    struct.putFields("number", numberValue);
    testAny.putAnyMap("struct", Any.pack(struct.build()));
    Value nullValue = Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
    testAny.putAnyMap(
        "list_value",
        Any.pack(ListValue.newBuilder().addValues(numberValue).addValues(nullValue).build()));
    testAny.putAnyMap("number_value", Any.pack(numberValue));
    testAny.putAnyMap("any_value_number", Any.pack(Any.pack(numberValue)));
    testAny.putAnyMap("any_value_default", Any.pack(Any.getDefaultInstance()));
    testAny.putAnyMap("default", Any.getDefaultInstance());

    assertMatchesUpstream(testAny.build(), TestAllTypes.getDefaultInstance());
  }

  @Test
  void parserMissingTypeUrl() throws Exception {
    Any.Builder builder = Any.newBuilder();
    assertThatThrownBy(() -> mergeFromJson("{\n" + "  \"optionalInt32\": 1234\n" + "}", builder))
        .isInstanceOf(InvalidProtocolBufferException.class);
  }

  @Test
  void parserUnexpectedTypeUrl() throws Exception {
    Any.Builder builder = Any.newBuilder();
    assertThatThrownBy(
            () ->
                mergeFromJson(
                    "{\n"
                        + "  \"@type\": \"type.googleapis.com/json_test.TestAllTypes\",\n"
                        + "  \"optionalInt32\": 12345\n"
                        + "}",
                    builder))
        .isInstanceOf(InvalidProtocolBufferException.class);
  }

  @Test
  void parserRejectTrailingComma() throws Exception {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    assertThatThrownBy(() -> mergeFromJson("{\n" + "  \"optionalInt32\": 12345,\n" + "}", builder))
        .isInstanceOf(InvalidProtocolBufferException.class);

    // Note: Upstream parser does not handle this case, but we do thanks to Jackson's validity
    // checks.
    builder.clear();
    assertThatThrownBy(
            () -> mergeFromJson("{\n" + "  \"repeatedInt32\": [12345,]\n" + "}", builder))
        .isInstanceOf(InvalidProtocolBufferException.class);
  }

  @Test
  void parserRejectInvalidBase64() throws Exception {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    assertThatThrownBy(() -> mergeFromJson("{\"optionalBytes\": \"!@#$\"", builder))
        .isInstanceOf(InvalidProtocolBufferException.class);
  }

  @Test
  void parserAcceptBase64Variants() throws Exception {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    mergeFromJson("{\"optionalBytes\": \"AQI\"}", builder);
  }

  @Test
  void parserRejectInvalidEnumValue() throws Exception {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    assertThatThrownBy(
            () -> mergeFromJson("{\n" + "  \"optionalNestedEnum\": \"XXX\"\n" + "}", builder))
        .isInstanceOf(InvalidProtocolBufferException.class);
  }

  @Test
  void parserUnknownFields() throws Exception {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    String json = "{\n" + "  \"unknownField\": \"XXX\"\n" + "}";
    assertThatThrownBy(() -> mergeFromJson(json, builder))
        .isInstanceOf(InvalidProtocolBufferException.class);
  }

  @Test
  void parserIgnoringUnknownFields() throws Exception {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    String json = "{\n" + " \"optionalInt32\": 10, \n" + "  \"unknownField\": \"XXX\"\n" + "}";
    mergeFromJson(/* ignoringUnknownFields= */ true, json, builder);
    assertThat(builder.getOptionalInt32()).isEqualTo(10);
  }

  @Test
  void parserIgnoringUnknownEnums() throws Exception {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    String json = "{\n" + "  \"optionalNestedEnum\": \"XXX\"\n" + "}";
    mergeFromJson(/* ignoringUnknownFields= */ true, json, builder);
    assertThat(builder.getOptionalNestedEnumValue()).isZero();
  }

  @Test
  void parserSupportAliasEnums() throws Exception {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    String json = "{\n" + "  \"optionalAliasedEnum\": \"QUX\"\n" + "}";
    mergeFromJson(json, builder);
    assertThat(builder.getOptionalAliasedEnum()).isEqualTo(AliasedEnum.ALIAS_BAZ);

    builder = TestAllTypes.newBuilder();
    json = "{\n" + "  \"optionalAliasedEnum\": \"qux\"\n" + "}";
    mergeFromJson(json, builder);
    assertThat(builder.getOptionalAliasedEnum()).isEqualTo(AliasedEnum.ALIAS_BAZ);

    builder = TestAllTypes.newBuilder();
    json = "{\n" + "  \"optionalAliasedEnum\": \"bAz\"\n" + "}";
    mergeFromJson(json, builder);
    assertThat(builder.getOptionalAliasedEnum()).isEqualTo(AliasedEnum.ALIAS_BAZ);
  }

  @Test
  void unknownEnumMap() throws Exception {
    TestMap.Builder builder = TestMap.newBuilder();
    mergeFromJson(
        /* ignoringUnknownFields= */ true,
        "{\n" + "  \"int32ToEnumMap\": {1: \"XXX\", 2: \"FOO\"}" + "}",
        builder);

    assertThat(builder.getInt32ToEnumMapMap()).containsExactly(entry(2, NestedEnum.FOO));
  }

  @Test
  void repeatedUnknownEnum() throws Exception {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    mergeFromJson(
        /* ignoringUnknownFields= */ true,
        "{\n" + "  \"repeatedNestedEnum\": [\"XXX\", \"FOO\", \"BAR\", \"BAZ\"]" + "}",
        builder);

    assertThat(builder.getRepeatedNestedEnumList())
        .containsExactly(NestedEnum.FOO, NestedEnum.BAR, NestedEnum.BAZ);
  }

  @Test
  void parserIntegerEnumValue() throws Exception {
    TestAllTypes.Builder actualBuilder = TestAllTypes.newBuilder();
    mergeFromJson("{\n" + "  \"optionalNestedEnum\": 2\n" + "}", actualBuilder);

    TestAllTypes expected = TestAllTypes.newBuilder().setOptionalNestedEnum(NestedEnum.BAZ).build();
    assertThat(actualBuilder.build()).isEqualTo(expected);
  }

  @Test
  void customJsonName() throws Exception {
    TestCustomJsonName message = TestCustomJsonName.newBuilder().setValue(12345).build();
    assertMatchesUpstream(message);
  }

  @Test
  void htmlEscape() throws Exception {
    TestAllTypes message = TestAllTypes.newBuilder().setOptionalString("</script>").build();
    assertMatchesUpstream(message);

    MessageMarshaller marshaller = MessageMarshaller.builder().register(TestAllTypes.class).build();

    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    marshaller.mergeValue(marshaller.writeValueAsString(message), builder);
    assertThat(builder.getOptionalString()).isEqualTo(message.getOptionalString());
  }

  @Test
  void includingDefaultValueFields() throws Exception {
    TestAllTypes message = TestAllTypes.getDefaultInstance();
    assertMatchesUpstream(message);
    assertMatchesUpstream(
        message,
        /* includingDefaultValueFields= */ true,
        /* preservingProtoFieldNames= */ false,
        /* omittingInsignificantWhitespace= */ false,
        /* printingEnumsAsInts= */ false,
        /* sortingMapKeys= */ false);

    TestMap mapMessage = TestMap.getDefaultInstance();
    assertMatchesUpstream(mapMessage);
    assertMatchesUpstream(
        mapMessage,
        /* includingDefaultValueFields= */ true,
        /* preservingProtoFieldNames= */ false,
        /* omittingInsignificantWhitespace= */ false,
        /* printingEnumsAsInts= */ false,
        /* sortingMapKeys= */ false);

    TestOneof oneofMessage = TestOneof.getDefaultInstance();
    assertMatchesUpstream(oneofMessage);
    assertMatchesUpstream(
        oneofMessage,
        /* includingDefaultValueFields= */ true,
        /* preservingProtoFieldNames= */ false,
        /* omittingInsignificantWhitespace= */ false,
        /* printingEnumsAsInts= */ false,
        /* sortingMapKeys= */ false);

    oneofMessage = TestOneof.newBuilder().setOneofInt32(42).build();
    assertMatchesUpstream(oneofMessage);
    assertMatchesUpstream(
        oneofMessage,
        /* includingDefaultValueFields= */ true,
        /* preservingProtoFieldNames= */ false,
        /* omittingInsignificantWhitespace= */ false,
        /* printingEnumsAsInts= */ false,
        /* sortingMapKeys= */ false);

    oneofMessage = TestOneof.newBuilder().setOneofNullValue(NullValue.NULL_VALUE).build();
    assertMatchesUpstream(oneofMessage);
    assertMatchesUpstream(
        oneofMessage,
        /* includingDefaultValueFields= */ true,
        /* preservingProtoFieldNames= */ false,
        /* omittingInsignificantWhitespace= */ false,
        /* printingEnumsAsInts= */ false,
        /* sortingMapKeys= */ false);
  }

  @Test
  void preservingProtoFieldNames() throws Exception {
    TestAllTypes message = TestAllTypes.newBuilder().setOptionalInt32(12345).build();
    assertMatchesUpstream(message);
    assertMatchesUpstream(
        message,
        /* includingDefaultValueFields= */ false,
        /* preservingProtoFieldNames= */ true,
        /* omittingInsignificantWhitespace= */ false,
        /* printingEnumsAsInts= */ false,
        /* sortingMapKeys= */ false);

    // The json_name field option is ignored when configured to use original proto field names.
    TestCustomJsonName messageWithCustomJsonName =
        TestCustomJsonName.newBuilder().setValue(12345).build();
    assertMatchesUpstream(
        messageWithCustomJsonName,
        /* includingDefaultValueFields= */ false,
        /* preservingProtoFieldNames= */ true,
        /* omittingInsignificantWhitespace= */ false,
        /* printingEnumsAsInts= */ false,
        /* sortingMapKeys= */ false);

    // Parsers accept both original proto field names and lowerCamelCase names.
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    mergeFromJson("{\"optionalInt32\": 12345}", builder);
    assertThat(builder.getOptionalInt32()).isEqualTo(12345);
    builder.clear();
    mergeFromJson("{\"optional_int32\": 54321}", builder);
    assertThat(builder.getOptionalInt32()).isEqualTo(54321);
  }

  @Test
  void printingEnumsAsInts() throws Exception {
    TestAllTypes message = TestAllTypes.newBuilder().setOptionalNestedEnum(NestedEnum.BAR).build();
    assertMatchesUpstream(
        message,
        /* includingDefaultValueFields= */ false,
        /* preservingProtoFieldNames= */ false,
        /* omittingInsignificantWhitespace= */ false,
        /* printingEnumsAsInts= */ true,
        /* sortingMapKeys= */ false);
  }

  @Test
  void omittingInsignificantWhitespace() throws Exception {
    TestAllTypes message = TestAllTypes.newBuilder().setOptionalInt32(12345).build();
    assertMatchesUpstream(
        message,
        /* includingDefaultValueFields= */ false,
        /* preservingProtoFieldNames= */ false,
        /* omittingInsignificantWhitespace= */ true,
        /* printingEnumsAsInts= */ false,
        /* sortingMapKeys= */ false);
    TestAllTypes message1 = TestAllTypes.getDefaultInstance();
    assertMatchesUpstream(
        message1,
        /* includingDefaultValueFields= */ false,
        /* preservingProtoFieldNames= */ false,
        /* omittingInsignificantWhitespace= */ true,
        /* printingEnumsAsInts= */ false,
        /* sortingMapKeys= */ false);
    TestAllTypes message2 = JsonTestUtil.testAllTypesAllFields();
    assertMatchesUpstream(
        message2,
        /* includingDefaultValueFields= */ false,
        /* preservingProtoFieldNames= */ false,
        /* omittingInsignificantWhitespace= */ true,
        /* printingEnumsAsInts= */ false,
        /* sortingMapKeys= */ false);
  }

  // Regression test for b/29892357
  @Test
  void emptyWrapperTypesInAny() throws Exception {
    Any.Builder builder = Any.newBuilder();
    mergeFromJson(
        "{\n"
            + "  \"@type\": \"type.googleapis.com/google.protobuf.BoolValue\",\n"
            + "  \"value\": false\n"
            + "}\n",
        builder,
        TestAllTypes.getDefaultInstance());
    Any any = builder.build();
    assertThat(any.getValue().size()).isEqualTo(0);
  }

  @Test
  void recursionLimit() throws Exception {
    TestRecursive.Builder builder = TestRecursive.newBuilder();
    mergeFromJson(recursiveJson(ParseSupport.RECURSION_LIMIT - 1), builder);
    TestRecursive message = builder.build();
    for (int i = 0; i < ParseSupport.RECURSION_LIMIT - 1; i++) {
      message = message.getNested();
    }
    assertThat(message.getValue()).isEqualTo(1234);

    builder.clear();
    assertThatThrownBy(() -> mergeFromJson(recursiveJson(ParseSupport.RECURSION_LIMIT), builder))
        .isInstanceOf(InvalidProtocolBufferException.class);
  }

  // Test that we are not leaking out JSON exceptions.
  @SuppressWarnings("InputStreamSlowMultibyteRead")
  @Test
  void jsonException() throws Exception {
    InputStream throwingInputStream =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("12345");
          }
        };
    // When the underlying reader throws IOException, MessageMarshaller should forward
    // through this IOException.
    assertThatThrownBy(
            () -> {
              TestAllTypes.Builder builder = TestAllTypes.newBuilder();
              MessageMarshaller marshaller =
                  MessageMarshaller.builder().register(builder.getDefaultInstanceForType()).build();
              marshaller.mergeValue(throwingInputStream, builder);
            })
        .isInstanceOf(IOException.class)
        .hasMessage("12345");

    ByteArrayInputStream invalidJsonStream =
        new ByteArrayInputStream("{ xxx - yyy }".getBytes(StandardCharsets.UTF_8));
    // When the JSON parser throws parser exceptions, MessageMarshaller should turn
    // that into InvalidProtocolBufferException.
    assertThatThrownBy(
            () -> {
              TestAllTypes.Builder builder = TestAllTypes.newBuilder();
              MessageMarshaller marshaller =
                  MessageMarshaller.builder().register(builder.getDefaultInstanceForType()).build();
              marshaller.mergeValue(invalidJsonStream, builder);
            })
        .isInstanceOf(InvalidProtocolBufferException.class);
  }

  @Test
  void sortedMapKeys() throws Exception {
    TestMap.Builder mapBuilder = TestMap.newBuilder();
    mapBuilder.putStringToInt32Map("\ud834\udd20", 3); // utf-8 F0 9D 84 A0
    mapBuilder.putStringToInt32Map("foo", 99);
    mapBuilder.putStringToInt32Map("xxx", 123);
    mapBuilder.putStringToInt32Map("\u20ac", 1); // utf-8 E2 82 AC
    mapBuilder.putStringToInt32Map("abc", 20);
    mapBuilder.putStringToInt32Map("19", 19);
    mapBuilder.putStringToInt32Map("8", 8);
    mapBuilder.putStringToInt32Map("\ufb00", 2); // utf-8 EF AC 80
    mapBuilder.putInt32ToInt32Map(3, 3);
    mapBuilder.putInt32ToInt32Map(10, 10);
    mapBuilder.putInt32ToInt32Map(5, 5);
    mapBuilder.putInt32ToInt32Map(4, 4);
    mapBuilder.putInt32ToInt32Map(1, 1);
    mapBuilder.putInt32ToInt32Map(2, 2);
    mapBuilder.putInt32ToInt32Map(-3, -3);
    TestMap mapMessage = mapBuilder.build();
    assertMatchesUpstream(
        mapMessage,
        /* includingDefaultValueFields= */ false,
        /* preservingProtoFieldNames= */ false,
        /* omittingInsignificantWhitespace= */ false,
        /* printingEnumsAsInts= */ false,
        /* sortingMapKeys= */ true);

    TestMap emptyMap = TestMap.getDefaultInstance();
    assertMatchesUpstream(
        emptyMap,
        /* includingDefaultValueFields= */ false,
        /* preservingProtoFieldNames= */ false,
        /* omittingInsignificantWhitespace= */ false,
        /* printingEnumsAsInts= */ false,
        /* sortingMapKeys= */ true);
  }

  // https://github.com/curioswitch/curiostack/issues/7
  @Test
  void protoFieldAlreadyCamelCase() throws Exception {
    assertMatchesUpstream(TestRegression.newBuilder().addFeedIds(1).build());
  }

  @Test
  void protoFieldReservedName() throws Exception {
    assertMatchesUpstream(TestRegression.newBuilder().setClass_("hello").build());
  }

  @Test
  void fieldsOutOfOrder() throws Exception {
    assertMatchesUpstream(TestFieldOrder.newBuilder().setValue1("foo").setValue2("bar").build());
  }

  // Make sure handling of int values with string fields matches upstream.
  @Test
  void stringFieldIntValue() throws Exception {
    // Here
    TestAllTypes.Builder builder1 = TestAllTypes.newBuilder();
    mergeFromJson("{\"optional_string\": 1}", builder1);
    assertThat(builder1.build().getOptionalString()).isEqualTo("1");

    // Upstream
    TestAllTypes.Builder builder2 = TestAllTypes.newBuilder();
    JsonFormat.parser().merge("{\"optional_string\": 1}", builder2);
    assertThat(builder2.build().getOptionalString()).isEqualTo("1");
  }

  // Seems to be errorprone bug
  @SuppressWarnings("InlineMeInliner")
  private static String recursiveJson(int numRecursions) {
    StringBuilder input = new StringBuilder("{\n");
    for (int i = 0; i < numRecursions; i++) {
      input.append(Strings.repeat(" ", (i + 1) * 2));
      input.append("\"nested\": {\n");
    }
    input.append(Strings.repeat(" ", (numRecursions + 1) * 2));
    input.append("\"value\": 1234\n");
    for (int i = numRecursions - 1; i >= 0; i--) {
      input.append(Strings.repeat(" ", (i + 1) * 2));
      input.append("}\n");
    }
    input.append("}");
    return input.toString();
  }

  static void assertMatchesUpstream(Message message, Message... additionalTypes)
      throws IOException {
    assertMatchesUpstream(
        message,
        /* includingDefaultValueFields= */ false,
        /* preservingProtoFieldNames= */ false,
        /* omittingInsignificantWhitespace= */ false,
        /* printingEnumsAsInts= */ false,
        /* sortingMapKeys= */ false,
        additionalTypes);
  }

  private static void assertMatchesUpstream(
      Message message,
      boolean includingDefaultValueFields,
      boolean preservingProtoFieldNames,
      boolean omittingInsignificantWhitespace,
      boolean printingEnumsAsInts,
      boolean sortingMapKeys,
      Message... additionalTypes)
      throws IOException {
    MessageMarshaller.Builder marshallerBuilder =
        MessageMarshaller.builder()
            .register(message.getClass())
            .includingDefaultValueFields(includingDefaultValueFields)
            .preservingProtoFieldNames(preservingProtoFieldNames)
            .omittingInsignificantWhitespace(omittingInsignificantWhitespace)
            .printingEnumsAsInts(printingEnumsAsInts)
            .sortingMapKeys(sortingMapKeys);
    for (Message m : additionalTypes) {
      marshallerBuilder.register(m.getDefaultInstanceForType());
    }
    MessageMarshaller marshaller = marshallerBuilder.build();
    TypeRegistry.Builder typeRegistry = TypeRegistry.newBuilder();
    for (Message m : additionalTypes) {
      typeRegistry.add(m.getDescriptorForType());
    }
    Printer printer = JsonFormat.printer().usingTypeRegistry(typeRegistry.build());
    if (includingDefaultValueFields) {
      try {
        printer = (Printer) alwaysPrintFieldsWithNoPresence.invoke(printer);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new IOException(e);
      }
    }
    if (preservingProtoFieldNames) {
      printer = printer.preservingProtoFieldNames();
    }
    if (omittingInsignificantWhitespace) {
      printer = printer.omittingInsignificantWhitespace();
    }
    if (printingEnumsAsInts) {
      printer = printer.printingEnumsAsInts();
    }
    if (sortingMapKeys) {
      printer = printer.sortingMapKeys();
    }

    String json = marshaller.writeValueAsString(message);
    String upstreamJson = printer.print(message);
    assertThat(json).isEqualTo(upstreamJson);

    // TODO(choko): Jackson seems to always escape unicode characters when writing bytes. We only
    // have unicode characters in our sorted map test so for now just flag on that and investigate
    // whether this can be fixed.
    if (!sortingMapKeys) {
      String jsonFromBytes =
          new String(marshaller.writeValueAsBytes(message), StandardCharsets.UTF_8);
      assertThat(jsonFromBytes).isEqualTo(upstreamJson);

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      marshaller.writeValue(message, bos);
      assertThat(bos.toString(StandardCharsets.UTF_8.toString())).isEqualTo(upstreamJson);
    }

    Message.Builder builder = message.newBuilderForType();
    mergeFromJson(json, builder, additionalTypes);
    assertThat(builder.build()).isEqualTo(message);
  }

  private static void mergeFromJson(
      String json, Message.Builder builder, Message... additionalTypes) throws IOException {
    mergeFromJson(/* ignoringUnknownFields= */ false, json, builder, additionalTypes);
  }

  @SuppressWarnings("InconsistentOverloads")
  private static void mergeFromJson(
      boolean ignoringUnknownFields,
      String json,
      Message.Builder builder,
      Message... additionalTypes)
      throws IOException {
    MessageMarshaller.Builder marshallerBuilder =
        MessageMarshaller.builder()
            .register(builder.getDefaultInstanceForType())
            .ignoringUnknownFields(ignoringUnknownFields);
    for (Message prototype : additionalTypes) {
      marshallerBuilder.register(prototype);
    }
    MessageMarshaller marshaller = marshallerBuilder.build();
    marshaller.mergeValue(json, builder);

    Message.Builder builder2 = builder.build().newBuilderForType();
    marshaller.mergeValue(json.getBytes(StandardCharsets.UTF_8), builder2);
    assertThat(builder2.build()).isEqualTo(builder.build());

    Message.Builder builder3 = builder.build().newBuilderForType();
    try (ByteArrayInputStream bis =
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
      marshaller.mergeValue(bis, builder3);
    }
    assertThat(builder3.build()).isEqualTo(builder.build());
  }

  @Test
  void stringFieldFloatInput() throws Exception {
    String json = "{\"optional_string\": 1.100000000000000000000000000001}";

    // Confirm behavior matches upstream.
    TestAllTypes.Builder upstreamBuilder = TestAllTypes.newBuilder();
    JsonFormat.parser().merge(json, upstreamBuilder);
    assertThat(upstreamBuilder.build().getOptionalString())
        .isEqualTo("1.100000000000000000000000000001");

    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    mergeFromJson(json, builder);
    assertThat(builder.build().getOptionalString()).isEqualTo("1.100000000000000000000000000001");
  }
}
