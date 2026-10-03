/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import com.google.protobuf.Message;
import java.io.IOException;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * A {@link JacksonModule} which can be registered to an {@link tools.jackson.databind.ObjectMapper}
 * to enable it to marshal protobuf using {@link MessageMarshaller}. If any modules registered after
 * this one handle protobuf messages, they will be used even when marshalling nested messages within
 * {@link MessageMarshaller}. This can be used to customize the marshalling of certain types when
 * needed.
 *
 * <p>For example:
 *
 * <pre>{@code
 * MessageMarshaller marshaller = MessageMarshaller.builder()
 *     .register(MyMessage.getDefaultInstance())
 *     .build();
 * ObjectMapper mapper = JsonMapper.builder()
 *     .addModule(MessageMarshallerModule.of(marshaller))
 *     .build();
 * mapper.writeValueAsString(MyMessage.newBuilder().setValue("hello!").build());
 * }</pre>
 */
public final class MessageMarshallerModule extends SimpleModule {

  /**
   * Returns a new {@link JacksonModule} which will use the provided {@link MessageMarshaller} to
   * marshal protobuf messages.
   */
  public static JacksonModule of(MessageMarshaller marshaller) {
    return new MessageMarshallerModule(marshaller);
  }

  private static final long serialVersionUID = -7559578444655954044L;

  @SuppressWarnings({"rawtypes", "unchecked"})
  private MessageMarshallerModule(MessageMarshaller marshaller) {
    for (Message prototype : marshaller.registeredPrototypes()) {
      // Couldn't figure out how to get the generics to work properly for this without rawtypes.
      addDeserializer(
          (Class) prototype.getClass(), new MessageDeserializer<>(prototype, marshaller));
      addSerializer(new MessageSerializer<>(prototype, marshaller));
    }
  }

  private static class MessageDeserializer<T extends Message> extends StdDeserializer<T> {

    private static final long serialVersionUID = 2347902651812283460L;

    private final transient T prototype;
    private final transient MessageMarshaller marshaller;

    MessageDeserializer(T prototype, MessageMarshaller marshaller) {
      super(prototype.getClass());
      this.prototype = prototype;
      this.marshaller = marshaller;
    }

    @Override
    public T deserialize(JsonParser parser, DeserializationContext ctxt) {
      JsonParser wrapped =
          new JsonParserWrapper(
              parser,
              (p, clz) -> {
                ValueDeserializer<?> deserializer;
                try {
                  deserializer = ctxt.findNonContextualValueDeserializer(ctxt.constructType(clz));
                } catch (DatabindException e) {
                  return JsonParserWrapper.NOT_DESERIALIZED;
                }
                if (deserializer == null) {
                  return JsonParserWrapper.NOT_DESERIALIZED;
                }
                return deserializer.deserialize(p, ctxt);
              });
      Message.Builder builder = prototype.newBuilderForType();
      try {
        marshaller.mergeValue(wrapped, builder);
      } catch (IOException e) {
        throw toJacksonException(e, message -> DatabindException.from(parser, message, e));
      }
      @SuppressWarnings("unchecked") // newBuilderForType().build() always returns T.
      T message = (T) builder.build();
      return message;
    }
  }

  private static class MessageSerializer<T extends Message> extends StdSerializer<T> {

    private static final long serialVersionUID = 7592254532224523930L;

    private final MessageMarshaller marshaller;

    MessageSerializer(T prototype, MessageMarshaller marshaller) {
      super(messageClass(prototype));
      this.marshaller = marshaller;
    }

    @SuppressWarnings("unchecked") // The class of a T is a Class<T>.
    private static <T extends Message> Class<T> messageClass(T prototype) {
      return (Class<T>) prototype.getClass();
    }

    @Override
    public void serialize(T value, JsonGenerator gen, SerializationContext provider) {
      JsonGenerator wrapped =
          new JsonGeneratorWrapper(
              gen,
              (obj, g) -> {
                ValueSerializer<Object> serializer;
                try {
                  serializer = provider.findValueSerializer(obj.getClass());
                } catch (DatabindException e) {
                  return false;
                }
                if (serializer == null) {
                  return false;
                }
                serializer.serialize(obj, g, provider);
                return true;
              });
      try {
        marshaller.writeValue(value, wrapped);
      } catch (IOException e) {
        throw toJacksonException(e, message -> DatabindException.from(gen, message, e));
      }
    }
  }

  /**
   * Jackson 3 serializers and deserializers throw unchecked {@link JacksonException}s. {@link
   * MessageMarshaller} reports these as {@link IOException}s, so unwrap them, i.e. when thrown by
   * serializers of nested messages.
   */
  private static JacksonException toJacksonException(
      IOException e, Function<String, JacksonException> factory) {
    for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
      if (cause instanceof JacksonException) {
        return (JacksonException) cause;
      }
    }
    return factory.apply(String.valueOf(e.getMessage()));
  }
}
