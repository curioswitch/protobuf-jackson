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
 * {@link StackManipulation} which jumps to a destination if the two ints on the execution stack are
 * not equal.
 *
 * <p>Used for if-statements like:
 *
 * <pre>{code
 *   if (int1 == int2) {
 *      ...
 *   }
 *   // destination
 * }</pre>
 */
public final class IfIntsNotEqual implements StackManipulation {

  private final Label destination;

  public IfIntsNotEqual(Label destination) {
    this.destination = destination;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public Size apply(MethodVisitor methodVisitor, Context implementationContext) {
    methodVisitor.visitJumpInsn(Opcodes.IF_ICMPNE, destination);
    return new Size(-StackSize.SINGLE.getSize() * 2, 0);
  }
}
