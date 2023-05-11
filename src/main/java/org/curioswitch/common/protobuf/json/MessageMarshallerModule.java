/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.protobuf.Message;
import java.io.IOException;

public final class MessageMarshallerModule extends SimpleModule {

  private static final long serialVersionUID = -7559578444655954044L;

  @SuppressWarnings({"rawtypes", "unchecked"})
  public MessageMarshallerModule(MessageMarshaller marshaller) {
    for (Message prototype : marshaller.registeredPrototypes()) {
      addDeserializer(
          (Class) prototype.getClass(), new MessageDeserializer<>(prototype, marshaller));
      addSerializer(new MessageSerializer<>(prototype, marshaller));
    }
  }

  private static class MessageDeserializer<T extends Message> extends StdDeserializer<T> {

    private static final long serialVersionUID = 2347902651812283460L;

    private final T prototype;
    private final MessageMarshaller marshaller;

    MessageDeserializer(T prototype, MessageMarshaller marshaller) {
      super(prototype.getClass());
      this.prototype = prototype;
      this.marshaller = marshaller;
    }

    @Override
    public T deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
      JsonParser wrapped =
          new JsonParserWrapper(
              parser,
              (p, clz) -> {
                JsonDeserializer<?> deserializer;
                try {
                  deserializer = ctxt.findNonContextualValueDeserializer(ctxt.constructType(clz));
                } catch (JsonMappingException e) {
                  return JsonParserWrapper.NOT_DESERIALIZED;
                }
                if (deserializer == null) {
                  return JsonParserWrapper.NOT_DESERIALIZED;
                }
                return deserializer.deserialize(p, ctxt);
              });
      Message.Builder builder = prototype.newBuilderForType();
      marshaller.mergeValue(wrapped, builder);
      @SuppressWarnings("unchecked") // newBuilderForType().build() always returns T.
      T message = (T) builder.build();
      return message;
    }
  }

  private static class MessageSerializer<T extends Message> extends StdSerializer<T> {

    private static final long serialVersionUID = 7592254532224523930L;

    private final MessageMarshaller marshaller;

    MessageSerializer(T prototype, MessageMarshaller marshaller) {
      super(prototype.getClass(), true);
      this.marshaller = marshaller;
    }

    @Override
    public void serialize(T value, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      JsonGenerator wrapped =
          new JsonGeneratorWrapper(
              gen,
              (obj, g) -> {
                JsonSerializer<Object> serializer;
                try {
                  serializer = provider.findValueSerializer(obj.getClass());
                } catch (JsonMappingException e) {
                  return false;
                }
                if (serializer == null) {
                  return false;
                }
                serializer.serialize(obj, g, provider);
                return true;
              });
      marshaller.writeValue(value, wrapped);
    }
  }
}
