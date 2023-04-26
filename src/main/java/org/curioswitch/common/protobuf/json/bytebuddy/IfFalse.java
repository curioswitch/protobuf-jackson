/*
 * Copyright (c) Choko (choko@curioswitch.org)
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
 * A {@link StackManipulation} which jumps to a destination if the boolean value on the stack is
 * false.
 *
 * <p>Used for if-statements like
 *
 * <pre>{code
 *   if (a) {
 *     ...
 *   }
 *   // destination
 * }</pre>
 */
public final class IfFalse implements StackManipulation {

  private final Label destination;

  public IfFalse(Label destination) {
    this.destination = destination;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public Size apply(MethodVisitor methodVisitor, Context implementationContext) {
    methodVisitor.visitJumpInsn(Opcodes.IFEQ, destination);
    return StackSize.SINGLE.toDecreasingSize();
  }
}
