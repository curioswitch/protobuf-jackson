/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.protobuf.util.JsonTestProto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ObjectMapperTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final JsonSerializer<JsonTestProto.TestAllTypes.NestedMessage> NESTED_SERIALIZER =
      new StdSerializer<JsonTestProto.TestAllTypes.NestedMessage>(
          JsonTestProto.TestAllTypes.NestedMessage.class) {
        @Override
        public void serialize(
            JsonTestProto.TestAllTypes.NestedMessage value,
            JsonGenerator gen,
            SerializerProvider serializers)
            throws IOException {
          gen.writeString("nested=" + value.getValue());
        }
      };

  private static final StdDeserializer<JsonTestProto.TestAllTypes.NestedMessage>
      NESTED_DESERIALIZER =
          new StdDeserializer<JsonTestProto.TestAllTypes.NestedMessage>(
              JsonTestProto.TestAllTypes.NestedMessage.class) {
            @Override
            public JsonTestProto.TestAllTypes.NestedMessage deserialize(
                JsonParser p, DeserializationContext ctxt) throws IOException {
              String value = p.getValueAsString();
              if (!value.startsWith("nested=")) {
                throw new JsonParseException("invalid nexted message");
              }
              int val = Integer.parseInt(value.substring("nested=".length()));
              return JsonTestProto.TestAllTypes.NestedMessage.newBuilder().setValue(val).build();
            }
          };

  static {
    MessageMarshaller marshaller =
        MessageMarshaller.builder()
            .register(JsonTestProto.TestAllTypes.getDefaultInstance())
            .build();
    OBJECT_MAPPER.registerModule(MessageMarshallerModule.of(marshaller));
    OBJECT_MAPPER.registerModule(
        new SimpleModule()
            .addSerializer(NESTED_SERIALIZER)
            .addDeserializer(JsonTestProto.TestAllTypes.NestedMessage.class, NESTED_DESERIALIZER));
  }

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

    String allTypesSerialized = OBJECT_MAPPER.writeValueAsString(allTypes);
    assertThat(allTypesSerialized)
        .isEqualTo("{\n" + "  \"optionalNestedMessage\": \"nested=900\"\n" + "}");

    JsonTestProto.TestAllTypes deserializedAllTypes =
        OBJECT_MAPPER.readValue(allTypesSerialized, JsonTestProto.TestAllTypes.class);
    Assertions.assertThat(deserializedAllTypes).isEqualTo(allTypes);
  }

  @Test
  void doesNotCloseJsonGenerator() throws Exception {
    JsonGenerator generator =
        new ObjectMapper().getFactory().createGenerator(new ByteArrayOutputStream());
    MessageMarshaller marshaller =
        MessageMarshaller.builder()
            .register(JsonTestProto.TestAllTypes.getDefaultInstance())
            .build();
    marshaller.writeValue(JsonTestProto.TestAllTypes.getDefaultInstance(), generator);
    assertThat(generator.isClosed()).isFalse();
  }

  @Test
  void doesNotCloseJsonParser() throws Exception {
    JsonParser parser = new ObjectMapper().getFactory().createParser("{}");
    MessageMarshaller marshaller =
        MessageMarshaller.builder()
            .register(JsonTestProto.TestAllTypes.getDefaultInstance())
            .build();
    marshaller.mergeValue(parser, JsonTestProto.TestAllTypes.newBuilder());
    assertThat(parser.isClosed()).isFalse();
  }
}
