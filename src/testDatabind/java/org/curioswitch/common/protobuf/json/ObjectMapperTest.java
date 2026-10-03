/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonTestProto;
import java.io.ByteArrayOutputStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.util.TokenBuffer;

class ObjectMapperTest {

  private static final ValueSerializer<JsonTestProto.TestAllTypes.NestedMessage> NESTED_SERIALIZER =
      new StdSerializer<JsonTestProto.TestAllTypes.NestedMessage>(
          JsonTestProto.TestAllTypes.NestedMessage.class) {
        @Override
        public void serialize(
            JsonTestProto.TestAllTypes.NestedMessage value,
            JsonGenerator gen,
            SerializationContext serializers) {
          gen.writeString("nested=" + value.getValue());
        }
      };

  private static final StdDeserializer<JsonTestProto.TestAllTypes.NestedMessage>
      NESTED_DESERIALIZER =
          new StdDeserializer<JsonTestProto.TestAllTypes.NestedMessage>(
              JsonTestProto.TestAllTypes.NestedMessage.class) {
            @Override
            public JsonTestProto.TestAllTypes.NestedMessage deserialize(
                JsonParser p, DeserializationContext ctxt) {
              String value = p.getValueAsString();
              if (!value.startsWith("nested=")) {
                throw new StreamReadException(p, "invalid nexted message");
              }
              int val = Integer.parseInt(value.substring("nested=".length()));
              return JsonTestProto.TestAllTypes.NestedMessage.newBuilder().setValue(val).build();
            }
          };

  private static final ObjectMapper OBJECT_MAPPER =
      JsonMapper.builder()
          .addModule(
              MessageMarshallerModule.of(
                  MessageMarshaller.builder()
                      .register(JsonTestProto.TestAllTypes.getDefaultInstance())
                      .build()))
          .addModule(
              new SimpleModule()
                  .addSerializer(NESTED_SERIALIZER)
                  .addDeserializer(
                      JsonTestProto.TestAllTypes.NestedMessage.class, NESTED_DESERIALIZER))
          .build();

  @Test
  void customSerializer() throws Exception {
    JsonTestProto.TestAllTypes.NestedMessage message =
        JsonTestProto.TestAllTypes.NestedMessage.newBuilder().setValue(900).build();

    String serialized = OBJECT_MAPPER.writeValueAsString(message);

    assertThat(serialized).isEqualTo("\"nested=900\"");

    JsonTestProto.TestAllTypes.NestedMessage deserialized =
        OBJECT_MAPPER.readValue(serialized, JsonTestProto.TestAllTypes.NestedMessage.class);
    Assertions.assertThat(deserialized).isEqualTo(message);

    JsonTestProto.TestAllTypes allTypes =
        JsonTestProto.TestAllTypes.newBuilder().setOptionalNestedMessage(message).build();

    // Whitespace is determined by the ObjectMapper, which does not indent by default.
    String allTypesSerialized = OBJECT_MAPPER.writeValueAsString(allTypes);
    assertThat(allTypesSerialized).isEqualTo("{\"optionalNestedMessage\":\"nested=900\"}");

    JsonTestProto.TestAllTypes deserializedAllTypes =
        OBJECT_MAPPER.readValue(allTypesSerialized, JsonTestProto.TestAllTypes.class);
    Assertions.assertThat(deserializedAllTypes).isEqualTo(allTypes);
  }

  @Test
  void whitespaceFollowsObjectMapper() throws Exception {
    JsonTestProto.TestAllTypes message =
        JsonTestProto.TestAllTypes.newBuilder().setOptionalInt32(1).build();

    assertThat(OBJECT_MAPPER.writeValueAsString(message)).isEqualTo("{\"optionalInt32\":1}");
    assertThat(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(message))
        .isEqualTo("{\n  \"optionalInt32\" : 1\n}");
  }

  @Test
  void doesNotCloseJsonGenerator() throws Exception {
    JsonGenerator generator =
        JsonMapper.builder().build().createGenerator(new ByteArrayOutputStream());
    MessageMarshaller marshaller =
        MessageMarshaller.builder()
            .register(JsonTestProto.TestAllTypes.getDefaultInstance())
            .build();
    marshaller.writeValue(JsonTestProto.TestAllTypes.getDefaultInstance(), generator);
    assertThat(generator.isClosed()).isFalse();
  }

  @Test
  void doesNotCloseJsonParser() throws Exception {
    JsonParser parser = JsonMapper.builder().build().createParser("{}");
    MessageMarshaller marshaller =
        MessageMarshaller.builder()
            .register(JsonTestProto.TestAllTypes.getDefaultInstance())
            .build();
    marshaller.mergeValue(parser, JsonTestProto.TestAllTypes.newBuilder());
    assertThat(parser.isClosed()).isFalse();
  }

  @Test
  void tokenBuffer() throws Exception {
    MessageMarshaller marshaller =
        MessageMarshaller.builder()
            .register(JsonTestProto.TestAllTypes.getDefaultInstance())
            .build();

    Value original =
        Value.newBuilder()
            .setStructValue(
                Struct.newBuilder()
                    // Allocate field name with new String(), meaning the key is not interned
                    // automatically
                    .putFields(
                        new String("optional_float"),
                        Value.newBuilder().setNumberValue(3.2).build())
                    .build())
            .build();

    ObjectMapper mapper =
        JsonMapper.builder().addModule(MessageMarshallerModule.of(marshaller)).build();

    // Use a jackson TokenBuffer to convert the message to a JsonNode
    TokenBuffer buffer = TokenBuffer.forGeneration();
    mapper.writeValue(buffer, original);
    JsonParser parser = buffer.asParser();

    JsonTestProto.TestAllTypes.Builder builder = JsonTestProto.TestAllTypes.newBuilder();
    marshaller.mergeValue(parser, builder);
    assertThat(builder.build().getOptionalFloat()).isEqualTo(3.2f);
  }
}
