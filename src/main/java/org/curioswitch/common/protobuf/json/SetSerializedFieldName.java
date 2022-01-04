/*
 * Copyright (c) 2019-2022 Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import java.util.Map;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.jar.asm.MethodVisitor;

/**
 * Sets the static field with the given name to a SerializedString containing the protobuf field's
 * name, for efficient serializing, e.g.,
 *
 * <pre>{@code
 * private static final FIELD_NAME_1 = SerializeSupport.serializeString("fieldFoo");
 * private static final FIELD_NAME_3 = SerializeSupport.serializeString("fieldBar");
 *
 * }</pre>
 */
class SetSerializedFieldName implements ByteCodeAppender, Implementation {

  private static final StackManipulation SerializeSupport_serializeString;

  static {
    try {
      SerializeSupport_serializeString =
          CodeGenUtil.invoke(
              SerializeSupport.class.getDeclaredMethod("serializeString", String.class));
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  private final String fieldName;
  private final String unserializedFieldValue;

  SetSerializedFieldName(String fieldName, String unserializedFieldValue) {
    this.fieldName = fieldName;
    this.unserializedFieldValue = unserializedFieldValue;
  }

  @Override
  public Size apply(
      MethodVisitor methodVisitor,
      Context implementationContext,
      MethodDescription instrumentedMethod) {
    Map<String, FieldDescription> fieldsByName = CodeGenUtil.fieldsByName(implementationContext);
    StackManipulation.Size operandStackSize =
        new StackManipulation.Compound(
                new TextConstant(unserializedFieldValue),
                SerializeSupport_serializeString,
                FieldAccess.forField(fieldsByName.get(fieldName)).write())
            .apply(methodVisitor, implementationContext);
    return new Size(operandStackSize.getMaximalSize(), instrumentedMethod.getStackSize());
  }

  @Override
  public ByteCodeAppender appender(Target implementationTarget) {
    return this;
  }

  @Override
  public InstrumentedType prepare(InstrumentedType instrumentedType) {
    return instrumentedType;
  }
}
