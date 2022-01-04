/*
 * Copyright (c) 2019-2022 Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json.bytebuddy;

import net.bytebuddy.implementation.Implementation.Context;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.StackSize;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

/**
 * A {@link StackManipulation} which jumps to a destination if two items on the execution stack are
 * equal. For reference types, there should only be a single boolean on the execution stack which is
 * the result of {@link Object#equals(Object)}.
 *
 * <p>Used for if-statements like
 *
 * <pre>{code
 *   if (a != b) {
 *     ...
 *   }
 *   // destination
 * }</pre>
 */
public final class IfEqual implements StackManipulation {

  private final Class<?> variableType;
  private final Label destination;

  public IfEqual(Class<?> variableType, Label destination) {
    this.variableType = variableType;
    this.destination = destination;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public Size apply(MethodVisitor methodVisitor, Context implementationContext) {
    int opcode;
    Size size = new Size(-StackSize.of(variableType).getSize() * 2, 0);
    if (variableType == int.class || variableType == boolean.class) {
      opcode = Opcodes.IF_ICMPEQ;
    } else if (variableType == long.class) {
      methodVisitor.visitInsn(Opcodes.LCMP);
      opcode = Opcodes.IFEQ;
    } else if (variableType == float.class) {
      methodVisitor.visitInsn(Opcodes.FCMPG);
      opcode = Opcodes.IFEQ;
    } else if (variableType == double.class) {
      methodVisitor.visitInsn(Opcodes.DCMPG);
      opcode = Opcodes.IFEQ;
    } else {
      // Reference type comparison assumes the result of Object.equals is already on the stack.
      opcode = Opcodes.IFNE;
      // There is only a boolean on the stack, so we only consume one item, unlike the others that
      // consume both.
      size = new Size(-1, 0);
    }
    methodVisitor.visitJumpInsn(opcode, destination);
    return size;
  }
}
