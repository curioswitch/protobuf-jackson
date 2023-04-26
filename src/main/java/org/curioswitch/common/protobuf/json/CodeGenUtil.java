/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.implementation.Implementation.Context;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;

/**
 * Utilities for code generation of protobufs, to simplify code or to unify logic between the byte
 * buddy builder and the generated instrumentation.
 */
final class CodeGenUtil {

  private static final Comparator<FieldDescriptor> FIELD_DESCRIPTOR_COMPARATOR =
      Comparator.comparing(FieldDescriptor::getNumber);

  /**
   * Returns the fields sorted in order of field number. By default, they are sorted in order of
   * definition in the proto file.
   */
  static List<FieldDescriptor> sorted(List<FieldDescriptor> fields) {
    List<FieldDescriptor> sorted = new ArrayList<>(fields);
    sorted.sort(FIELD_DESCRIPTOR_COMPARATOR);
    return sorted;
  }

  /**
   * Returns the name of the java field storing a {@link TypeSpecificMarshaller} for the given
   * descriptor.
   */
  static String fieldNameForNestedMarshaller(Descriptor descriptor) {
    return "MARSHALLER_" + descriptor.getFullName().replace('.', '_');
  }

  /**
   * Returns the name of the java field storing the pre-serialized version of the protobuf field
   * name.
   */
  static String fieldNameForSerializedFieldName(ProtoFieldInfo field) {
    return "FIELD_NAME_" + field.descriptor().getNumber();
  }

  /** Returns a {@link StackManipulation} that invokes the given {@link Method}. */
  static StackManipulation invoke(Method method) {
    return MethodInvocation.invoke(new MethodDescription.ForLoadedMethod(method));
  }

  /** Returns a {@link Map} of names to class / instance fields. */
  static Map<String, FieldDescription> fieldsByName(Context implementationContext) {
    Map<String, FieldDescription> map = new HashMap<>();
    for (FieldDescription field : implementationContext.getInstrumentedType().getDeclaredFields()) {
      map.put(field.getName(), field);
    }
    return map;
  }

  /**
   * Returns a {@link StackManipulation} that returns the {@link
   * com.google.protobuf.Descriptors.EnumDescriptor} for the given enum field.
   */
  static StackManipulation getEnumDescriptor(ProtoFieldInfo info) {
    Class<?> clz = info.enumClass();
    MethodDescription.ForLoadedMethod getDescriptor;
    try {
      getDescriptor = new MethodDescription.ForLoadedMethod(clz.getDeclaredMethod("getDescriptor"));
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Not an enum class: " + clz, e);
    }
    return MethodInvocation.invoke(getDescriptor);
  }

  private CodeGenUtil() {}
}
