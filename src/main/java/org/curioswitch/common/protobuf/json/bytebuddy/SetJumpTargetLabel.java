/*
 * Copyright (c) 2019-2022 Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json.bytebuddy;

import net.bytebuddy.implementation.Implementation.Context;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;

/**
 * Adds a jump target label. Used to indicate the destination of a jump. Used at the end of an
 * if-statement and beginning and end of loops.
 */
public final class SetJumpTargetLabel implements StackManipulation {

  private final Label label;

  public SetJumpTargetLabel(Label label) {
    this.label = label;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public Size apply(MethodVisitor methodVisitor, Context implementationContext) {
    methodVisitor.visitLabel(label);
    return new Size(0, 0);
  }
}
