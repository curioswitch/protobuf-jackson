/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.AsmVisitorWrapper.ForDeclaredMethods;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.jar.asm.ClassWriter;

/**
 * Abstract class for protobuf marshallers. Subclasses implement serialization and parsing logic
 * only for the specific {@link Message} type {@code T}. For well known types, this is hand-written
 * code and for others, bytecode is automatically generated for handling the type.
 */
public abstract class TypeSpecificMarshaller<T extends Message> {

  private static final MarshallerCache MARSHALLER_CACHE = new MarshallerCache();

  private final T prototype;

  protected TypeSpecificMarshaller(T prototype) {
    this.prototype = prototype;
  }

  T readValue(JsonParser parser, int currentDepth) throws IOException {
    Message.Builder builder = prototype.newBuilderForType();
    mergeValue(parser, currentDepth, builder);
    // T.newBuilderForType().buildAndAdd() is T
    @SuppressWarnings("unchecked")
    T built = (T) builder.build();
    return built;
  }

  void mergeValue(JsonParser parser, int currentDepth, Message.Builder builder) throws IOException {
    ParseSupport.checkRecursionLimit(currentDepth);
    JsonToken json = parser.currentToken();
    if (json == null) {
      // Nested messages will already have current token set, but top-level ones will not.
      json = parser.nextToken();
    }
    if (json != JsonToken.START_OBJECT) {
      throw new InvalidProtocolBufferException(
          "Expected start of object, got: " + parser.getText());
    }
    doMerge(parser, currentDepth, builder);
    // Object end will be handled in ParseSupport.checkObjectEnd.
  }

  /**
   * Parses the remaining fields of the object in the input as a message. Used for parsing {@link
   * com.google.protobuf.Any}, where the first part of the object has already been parsed to
   * determine the type url.
   */
  final T parseRemainingFieldsOfObjectAsMessage(JsonParser parser, int currentDepth)
      throws IOException {
    Message.Builder builder = prototype.newBuilderForType();
    doMerge(parser, currentDepth, builder);
    // T.newBuilderForType().buildAndAdd() is T
    @SuppressWarnings("unchecked")
    T built = (T) builder.build();
    return built;
  }

  void writeValue(T message, JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    doWrite(message, gen);
    gen.writeEndObject();
  }

  /**
   * Serialize to JSON the message encoded in binary protobuf format in {@code encodedMessage}. Used
   * to write the content of type wrappers in {@link com.google.protobuf.Any}.
   */
  void writeValue(ByteString encodedMessage, JsonGenerator gen) throws IOException {
    // getParserForTYpe for T returns Parser<T>
    @SuppressWarnings("unchecked")
    Parser<T> parser = (Parser<T>) prototype.getParserForType();
    writeValue(parser.parseFrom(encodedMessage), gen);
  }

  /**
   * Serialize to JSON the message encoded in binary protobuf format in {@code encodedMessage}
   * without starting or ending a new JSON object. Used to write the content of normal messages in
   * {@link com.google.protobuf.Any}, which will take care of creating the JSON object to store the
   * type url.
   */
  void doWrite(ByteString encodedMessage, JsonGenerator gen) throws IOException {
    // getParserForTYpe for T returns Parser<T>
    @SuppressWarnings("unchecked")
    Parser<T> parser = (Parser<T>) prototype.getParserForType();
    doWrite(parser.parseFrom(encodedMessage), gen);
  }

  protected void doWrite(T message, JsonGenerator gen) throws IOException {
    throw new UnsupportedOperationException();
  }

  protected void doMerge(JsonParser parser, int currentDepth, Message.Builder messageBuilder)
      throws IOException {
    throw new UnsupportedOperationException();
  }

  Descriptor getDescriptorForMarshalledType() {
    return prototype.getDescriptorForType();
  }

  T getMarshalledPrototype() {
    return prototype;
  }

  static void buildAndAdd(
      MarshallerOptions options, Map<Descriptor, TypeSpecificMarshaller<?>> builtMarshallers) {
    if (builtMarshallers.containsKey(options.getPrototype().getDescriptorForType())) {
      return;
    }
    buildOrFindMarshaller(options, builtMarshallers);
    Map<String, TypeSpecificMarshaller<?>> builtMarshallersByFieldName = new HashMap<>();
    for (Map.Entry<Descriptor, TypeSpecificMarshaller<?>> entry : builtMarshallers.entrySet()) {
      builtMarshallersByFieldName.put(
          CodeGenUtil.fieldNameForNestedMarshaller(entry.getKey()), entry.getValue());
    }
    // Wire up nested serializers.
    for (TypeSpecificMarshaller<?> m : builtMarshallers.values()) {
      for (Field field : m.getClass().getDeclaredFields()) {
        if (field.getName().startsWith("MARSHALLER_")) {
          try {
            TypeSpecificMarshaller<?> nested = builtMarshallersByFieldName.get(field.getName());
            if (nested == null) {
              throw new IllegalStateException(
                  "nested marshaller could not be found for field: + field.getName()");
            }
            field.set(m, nested);
          } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                "Could not set marshaller field, which we know is accessible.", e);
          }
        }
      }
    }
  }

  private static <T extends Message> void buildOrFindMarshaller(
      MarshallerOptions options,
      Map<Descriptor, TypeSpecificMarshaller<?>> alreadyBuiltMarshallers) {
    Descriptor descriptor = options.getPrototype().getDescriptorForType();
    if (alreadyBuiltMarshallers.containsKey(descriptor)) {
      return;
    }

    TypeDefinition superType =
        TypeDescription.Generic.Builder.parameterizedType(
                TypeSpecificMarshaller.class, options.getPrototype().getClass())
            .build();

    // Use default ConstructorStrategy which will generate a constructor with a Message argument of
    // the prototype, matching the superclass.
    @SuppressWarnings("unchecked")
    DynamicType.Builder<TypeSpecificMarshaller<T>> buddy =
        (DynamicType.Builder<TypeSpecificMarshaller<T>>)
            new ByteBuddy()
                .subclass(superType)
                .modifiers(Modifier.PUBLIC | Modifier.FINAL)
                .visit(new ForDeclaredMethods().writerFlags(ClassWriter.COMPUTE_FRAMES));

    List<Message> nestedMessagePrototypes = new ArrayList<>();
    for (FieldDescriptor f : descriptor.getFields()) {
      ProtoFieldInfo field = new ProtoFieldInfo(f, options.getPrototype());

      // Store a pre-encoded version of the field variableName to avoid re-encoding all the time.
      String fieldName = CodeGenUtil.fieldNameForSerializedFieldName(field);
      buddy =
          buddy
              .defineField(
                  fieldName,
                  SerializedString.class,
                  Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL)
              .initializer(
                  new SetSerializedFieldName(
                      fieldName,
                      options.isPreservingProtoFieldNames() ? f.getName() : f.getJsonName()));
      if (field.valueJavaType() != JavaType.MESSAGE) {
        continue;
      }
      Message nestedPrototype = field.valuePrototype();
      if (nestedMessagePrototypes.contains(nestedPrototype)) {
        continue;
      }
      // We delay building the nested serializers until after this one in case a descendant
      // references the current type, allowing us to avoid infinite recursion.
      nestedMessagePrototypes.add(nestedPrototype);

      TypeDefinition nestedMarshallerType =
          TypeDescription.Generic.Builder.parameterizedType(
                  TypeSpecificMarshaller.class, nestedPrototype.getClass())
              .build();
      buddy =
          buddy.defineField(
              CodeGenUtil.fieldNameForNestedMarshaller(nestedPrototype.getDescriptorForType()),
              nestedMarshallerType,
              Modifier.PUBLIC | Modifier.STATIC);
    }

    TypeSpecificMarshaller<?> marshaller;
    TypeSpecificMarshaller<?> cached = MARSHALLER_CACHE.get(options);
    if (cached != null) {
      marshaller = cached;
    } else {
      buddy =
          buddy
              .defineMethod("doMerge", void.class, Modifier.FINAL | Modifier.PROTECTED)
              .withParameter(JsonParser.class, "parser")
              .withParameter(int.class, "currentDepth")
              .withParameter(Message.Builder.class, "messageBuilder")
              .throwing(IOException.class)
              .intercept(new DoParse(options.getPrototype(), options.isIgnoringUnknownFields()))
              .defineMethod("doWrite", void.class, Modifier.FINAL | Modifier.PROTECTED)
              .withParameter(options.getPrototype().getClass(), "message")
              .withParameter(JsonGenerator.class, "gen")
              .throwing(IOException.class)
              .intercept(
                  new DoWrite(
                      options.getPrototype(),
                      options.isIncludingDefaultValueFields(),
                      options.getFieldsToAlwaysOutput(),
                      options.isPrintingEnumsAsInts(),
                      options.isSortingMapKeys()));
      try {
        marshaller =
            buddy
                .make()
                .load(TypeSpecificMarshaller.class.getClassLoader())
                .getLoaded()
                .getConstructor(options.getPrototype().getClass())
                .newInstance(options.getPrototype());
        MARSHALLER_CACHE.put(options, marshaller);
      } catch (InstantiationException
          | NoSuchMethodException
          | InvocationTargetException
          | IllegalAccessException e) {
        throw new IllegalStateException(
            "Could not generate marshaller, this is generally a bug in this library. "
                + "Please file a report at https://github.com/curioswitch/curiostack with this stack "
                + "trace and an example proto to reproduce.",
            e);
      }
    }
    alreadyBuiltMarshallers.put(descriptor, marshaller);
    for (Message nestedPrototype : nestedMessagePrototypes) {
      buildOrFindMarshaller(
          new MarshallerOptions(
              nestedPrototype,
              options.isIncludingDefaultValueFields(),
              options.getFieldsToAlwaysOutput(),
              options.isPreservingProtoFieldNames(),
              options.isIgnoringUnknownFields(),
              options.isPrintingEnumsAsInts(),
              options.isSortingMapKeys()),
          alreadyBuiltMarshallers);
    }
  }
}
