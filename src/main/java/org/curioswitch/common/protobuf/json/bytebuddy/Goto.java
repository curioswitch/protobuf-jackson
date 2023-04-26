/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json.bytebuddy;

import net.bytebuddy.implementation.Implementation.Context;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

/**
 * A {@link StackManipulation} which jumps unconditionally to a destination.
 *
 * <p>Used at the end of the block of loops to jump back to the loop condition.
 *
 * <pre>{code
 *   while (&#47;* destination *&#47; a != b) {
 *     ...
 *     // goto destination
 *   }
 * }</pre>
 */
public final class Goto implements StackManipulation {

  private final Label destination;

  public Goto(Label destination) {
    this.destination = destination;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public Size apply(MethodVisitor methodVisitor, Context implementationContext) {
    methodVisitor.visitJumpInsn(Opcodes.GOTO, destination);
    return new Size(0, 0);
  }
}
