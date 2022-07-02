/*
 * Copyright (c) 2019-2022 Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import static java.util.Objects.requireNonNull;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;
import com.google.protobuf.Message;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A wrapper of a {@link FieldDescriptor} to provide additional information like protobuf generated
 * code naming conventions and value type information.
 */
class ProtoFieldInfo {

  private final FieldDescriptor field;
  private final Message containingPrototype;
  private final Class<? extends Message.Builder> builderClass;

  private final String camelCaseName;

  @Nullable private final ProtoFieldInfo mapKeyField;
  @Nullable private final ProtoFieldInfo mapValueField;

  ProtoFieldInfo(FieldDescriptor field, Message containingPrototype) {
    this.field = requireNonNull(field, "field");
    this.containingPrototype = requireNonNull(containingPrototype, "containingPrototype");
    builderClass = containingPrototype.newBuilderForType().getClass();

    camelCaseName = underscoresToUpperCamelCase(field.getName());

    if (field.isMapField()) {
      Descriptor mapType = field.getMessageType();
      mapKeyField = new ProtoFieldInfo(mapType.findFieldByName("key"), containingPrototype);
      mapValueField = new ProtoFieldInfo(mapType.findFieldByName("value"), containingPrototype);
    } else {
      mapKeyField = null;
      mapValueField = null;
    }
  }

  /** Returns the raw {@link FieldDescriptor} for this field. */
  FieldDescriptor descriptor() {
    return field;
  }

  /** Returns whether this is a map field. */
  boolean isMapField() {
    return field.isMapField();
  }

  /**
   * Returns whether this is a repeated field. Note, map fields are also considered repeated fields.
   */
  boolean isRepeated() {
    return field.isRepeated();
  }

  /** Returns the {@link ProtoFieldInfo} of the key for this map field. */
  ProtoFieldInfo mapKeyField() {
    if (!isMapField()) {
      throw new IllegalStateException("Not a map field:" + field);
    }
    ProtoFieldInfo mapKeyField = this.mapKeyField;
    requireNonNull(mapKeyField);
    return mapKeyField;
  }

  /**
   * Returns the {@link ProtoFieldInfo} describing the actual value of this field, which for map
   * fields is the map's value.
   */
  ProtoFieldInfo valueField() {
    ProtoFieldInfo mapValueField = this.mapValueField;
    return mapValueField != null ? mapValueField : this;
  }

  /**
   * Returns the {@link Type} of the actual value of this field, which for map fields is the type of
   * the map's value.
   */
  FieldDescriptor.Type valueType() {
    return valueField().descriptor().getType();
  }

  /**
   * Returns the {@link FieldDescriptor.JavaType} of the actual value of this field, which for map
   * fields is the type of the map's value.
   */
  FieldDescriptor.JavaType valueJavaType() {
    return valueField().descriptor().getJavaType();
  }

  /**
   * Returns a prototype {@link Message} for the value of this field. For maps, it will be for the
   * value field of the map, otherwise it is for the field itself.
   */
  Message valuePrototype() {
    Message nestedPrototype =
        containingPrototype.newBuilderForType().newBuilderForField(field).buildPartial();
    if (isMapField()) {
      ProtoFieldInfo mapValueField = this.mapValueField;
      requireNonNull(mapValueField);
      // newBuilderForField will give us the Message corresponding to the map with key and value,
      // but we want the marshaller for the value itself.
      nestedPrototype = (Message) nestedPrototype.getField(mapValueField.descriptor());
    }
    return nestedPrototype;
  }

  /**
   * Returns the method to get the value for the field within its message. The message must already
   * be on the execution stack. For map fields, this will be the method that returns a {@link
   * java.util.Map} and for repeated fields it will be the method that returns a {@link List}.
   */
  Method getValueMethod() {
    StringBuilder methodName = new StringBuilder().append("get").append(camelCaseName);
    if (valueJavaType() == FieldDescriptor.JavaType.ENUM) {
      methodName.append("Value");
    }
    if (isMapField()) {
      methodName.append("Map");
    } else if (field.isRepeated()) {
      methodName.append("List");
    }
    try {
      return containingPrototype.getClass().getDeclaredMethod(methodName.toString());
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Could not find generated getter method.", e);
    }
  }

  /**
   * Returns the getter for the currently set value of this field's oneof. Must only be called for
   * oneof fields, which can be checked using {@link #isInOneof()}.
   */
  Method oneOfCaseMethod() {
    if (!isInOneof()) {
      throw new IllegalStateException("field is not in a oneof");
    }
    String methodName =
        "get" + underscoresToUpperCamelCase(field.getContainingOneof().getName()) + "Case";
    try {
      return containingPrototype.getClass().getDeclaredMethod(methodName);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Could not find generated oneof case method.", e);
    }
  }

  /**
   * Returns the method to determine whether the message has a value for this field. Only valid for
   * message types for proto3 messages, valid for all fields otherwise.
   */
  Method hasValueMethod() {
    String methodName = "has" + camelCaseName;
    try {
      return containingPrototype.getClass().getDeclaredMethod(methodName);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Could not find generated has method.", e);
    }
  }

  /**
   * Returns the {@link Method} that returns the current count of a repeated field. Must only be
   * called for repeated fields, which can be checked with {@link #isRepeated()}.
   */
  Method repeatedValueCountMethod() {
    String methodName = "get" + camelCaseName + "Count";
    try {
      return containingPrototype.getClass().getDeclaredMethod(methodName);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Could not find repeated field count method.", e);
    }
  }

  /**
   * Returns the {@link Method} that sets a single value of the field. For repeated and map fields,
   * this is the add or put method that only take an individual element;
   */
  Method setValueMethod() {
    StringBuilder setter = new StringBuilder();
    Class<?>[] args;
    if (isMapField()) {
      ProtoFieldInfo mapKeyField = this.mapKeyField;
      requireNonNull(mapKeyField);
      setter.append("put");
      args = new Class<?>[] {mapKeyField.javaClass(), javaClass()};
    } else {
      args = new Class<?>[] {javaClass()};
      if (field.isRepeated()) {
        setter.append("add");
      } else {
        setter.append("set");
      }
    }
    setter.append(camelCaseName);
    if (valueType() == FieldDescriptor.Type.ENUM) {
      setter.append("Value");
    }
    try {
      return builderClass.getDeclaredMethod(setter.toString(), args);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Could not find setter.", e);
    }
  }

  /** Returns whether this field is in a oneof. */
  boolean isInOneof() {
    return field.getContainingOneof() != null && !field.hasOptionalKeyword();
  }

  /**
   * Returns the getter for the currently set value of this field's oneof. Must only be called for
   * oneof fields, which can be checked using {@link #isInOneof()}.
   */
  String getOneOfCaseMethodName() {
    if (!isInOneof()) {
      throw new IllegalStateException("field is not in a oneof");
    }
    return "get" + underscoresToUpperCamelCase(field.getContainingOneof().getName());
  }

  /**
   * Determine the {@link Enum} class corresponding to this field's value. Because {@link Enum}
   * classes themselves are generated, we must introspect the prototype for determining the concrete
   * class. Due to type erasure, for repeated types we actually use protobuf reflection to add a
   * value to the container and retrieve it to determine the concrete type at runtime.
   */
  Class<?> enumClass() {
    Class<? extends Message> messageClass = containingPrototype.getClass();
    if (!field.isRepeated()) {
      return getEnumAsClassMethod().getReturnType();
    }
    if (isMapField()) {
      ProtoFieldInfo mapKeyField = this.mapKeyField;
      ProtoFieldInfo mapValueField = this.mapValueField;
      requireNonNull(mapKeyField);
      requireNonNull(mapValueField);
      if (valueJavaType() != FieldDescriptor.JavaType.ENUM) {
        throw new IllegalStateException(
            "Trying to determine enum class of non-enum type: " + field);
      }
      Message msgWithEnumValue =
          containingPrototype
              .newBuilderForType()
              .addRepeatedField(
                  field,
                  containingPrototype
                      .newBuilderForType()
                      .newBuilderForField(field)
                      .setField(
                          mapKeyField.descriptor(), mapKeyField.descriptor().getDefaultValue())
                      .setField(
                          mapValueField.descriptor(), mapValueField.descriptor().getDefaultValue())
                      .build())
              .build();
      try {
        return messageClass
            .getDeclaredMethod(
                getMapValueOrThrowMethodName(),
                new ProtoFieldInfo(mapKeyField.descriptor(), containingPrototype).javaClass())
            .invoke(msgWithEnumValue, mapKeyField.descriptor().getDefaultValue())
            .getClass();
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        throw new IllegalStateException("Could not find or invoke map item getter.", e);
      }
    }

    // Repeated field.
    // Enums always have at least one value, so we can call getValues().get(0) without checking.
    Message msgWithEnumValue =
        containingPrototype
            .newBuilderForType()
            .addRepeatedField(
                valueField().descriptor(),
                valueField().descriptor().getEnumType().getValues().get(0))
            .build();
    try {
      return ((List<?>) getEnumAsClassMethod().invoke(msgWithEnumValue)).get(0).getClass();
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Could not invoke enum getter for determining type.", e);
    }
  }

  /**
   * Return the Java {@link Class} that corresponds to the value of this field. Generally used for
   * method resolution and casting generics.
   */
  Class<?> javaClass() {
    if (isMapField() && valueJavaType() == FieldDescriptor.JavaType.MESSAGE) {
      Message mapEntry = containingPrototype.newBuilderForType().newBuilderForField(field).build();
      return mapEntry.getField(mapEntry.getDescriptorForType().findFieldByName("value")).getClass();
    }
    switch (valueJavaType()) {
      case INT:
        return int.class;
      case LONG:
        return long.class;
      case FLOAT:
        return float.class;
      case DOUBLE:
        return double.class;
      case BOOLEAN:
        return boolean.class;
      case STRING:
        return String.class;
      case BYTE_STRING:
        return ByteString.class;
      case ENUM:
        return int.class;
      case MESSAGE:
        return containingPrototype
            .newBuilderForType()
            .newBuilderForField(valueField().descriptor())
            .buildPartial()
            .getClass();
    }
    throw new IllegalArgumentException("Unknown field type: " + valueJavaType());
  }

  /**
   * Returns the name of the method that returns the value of a map field. Must only be called for
   * map fields, which can be checked using {@link #isMapField()}.
   */
  private String getMapValueOrThrowMethodName() {
    if (!isMapField()) {
      throw new IllegalStateException("field is not a map");
    }
    return "get" + camelCaseName + "OrThrow";
  }

  /**
   * Returns the {@link Method} that returns the value for this enum field within the message. Used
   * for introspection of the concrete Java type of an enum.
   */
  private Method getEnumAsClassMethod() {
    String getter = "get" + camelCaseName;
    if (field.isMapField()) {
      getter += "Map";
    } else if (field.isRepeated()) {
      getter += "List";
    }
    try {
      return containingPrototype.getClass().getDeclaredMethod(getter);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Could not find getter for enum field.", e);
    }
  }

  // Guava's CaseFormat does not handle non-snake-case field names the same as protobuf compiler,
  // so we just directly port the compiler's code from here:
  // https://github.com/google/protobuf/blob/2f4489a3e504e0a4aaffee69b551c6acc9e08374/src/google/protobuf/compiler/cpp/cpp_helpers.cc#L108
  private static String underscoresToUpperCamelCase(String input) {
    boolean capitalizeNextLetter = true;
    StringBuilder result = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c >= 'a' && c <= 'z') {
        if (capitalizeNextLetter) {
          result.append((char) (c + ('A' - 'a')));
        } else {
          result.append(c);
        }
        capitalizeNextLetter = false;
      } else if (c >= 'A' && c <= 'Z') {
        // Capital letters are left as-is.
        result.append(c);
        capitalizeNextLetter = false;
      } else if (c >= '0' && c <= '9') {
        result.append(c);
        capitalizeNextLetter = true;
      } else {
        capitalizeNextLetter = true;
      }
    }
    return result.toString();
  }
}
