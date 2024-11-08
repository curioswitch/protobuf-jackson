/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import java.util.Objects;
import java.util.Set;

class MarshallerOptions {

  private final Message prototype;
  private final boolean includingDefaultValueFields;
  private final Set<FieldDescriptor> fieldsToAlwaysOutput;
  private final boolean preservingProtoFieldNames;
  private final boolean ignoringUnknownFields;
  private final boolean printingEnumsAsInts;
  private final boolean sortingMapKeys;

  MarshallerOptions(
      Message prototype,
      boolean includingDefaultValueFields,
      Set<FieldDescriptor> fieldsToAlwaysOutput,
      boolean preservingProtoFieldNames,
      boolean ignoringUnknownFields,
      boolean printingEnumsAsInts,
      boolean sortingMapKeys) {
    this.prototype = prototype;
    this.includingDefaultValueFields = includingDefaultValueFields;
    this.fieldsToAlwaysOutput = fieldsToAlwaysOutput;
    this.preservingProtoFieldNames = preservingProtoFieldNames;
    this.ignoringUnknownFields = ignoringUnknownFields;
    this.printingEnumsAsInts = printingEnumsAsInts;
    this.sortingMapKeys = sortingMapKeys;
  }

  Message getPrototype() {
    return prototype;
  }

  public boolean isIncludingDefaultValueFields() {
    return includingDefaultValueFields;
  }

  Set<FieldDescriptor> getFieldsToAlwaysOutput() {
    return fieldsToAlwaysOutput;
  }

  boolean isPreservingProtoFieldNames() {
    return preservingProtoFieldNames;
  }

  boolean isIgnoringUnknownFields() {
    return ignoringUnknownFields;
  }

  boolean isPrintingEnumsAsInts() {
    return printingEnumsAsInts;
  }

  boolean isSortingMapKeys() {
    return sortingMapKeys;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MarshallerOptions)) {
      return false;
    }
    MarshallerOptions that = (MarshallerOptions) o;
    return includingDefaultValueFields == that.includingDefaultValueFields
        && preservingProtoFieldNames == that.preservingProtoFieldNames
        && ignoringUnknownFields == that.ignoringUnknownFields
        && printingEnumsAsInts == that.printingEnumsAsInts
        && sortingMapKeys == that.sortingMapKeys
        && prototype.getDescriptorForType().equals(that.prototype.getDescriptorForType())
        && fieldsToAlwaysOutput.equals(that.fieldsToAlwaysOutput);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        prototype.getDescriptorForType(),
        includingDefaultValueFields,
        fieldsToAlwaysOutput,
        preservingProtoFieldNames,
        ignoringUnknownFields,
        printingEnumsAsInts,
        sortingMapKeys);
  }
}
