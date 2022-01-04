/*
 * Copyright (c) 2019-2022 Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import java.util.HashMap;
import java.util.Map;

/**
 * A registry for looking up {@link TypeSpecificMarshaller} for a given protobuf {@link Descriptor}
 * or full name of the protobuf type.
 */
final class MarshallerRegistry {

  // Optimize for the common case of finding a serializer by Descriptor, which hashes much faster
  // than String. We create a map from String as well for use when resolving by type variableName
  // for serialization of Any. Iterating over the descriptors instead of creating a parallel map
  // would be reasonable too, but the memory usage should be tiny.
  private final Map<Descriptor, TypeSpecificMarshaller<?>> descriptorRegistry;
  private final Map<String, TypeSpecificMarshaller<?>> typeNameRegistry;

  MarshallerRegistry(Map<Descriptor, TypeSpecificMarshaller<?>> descriptorRegistry) {
    this.descriptorRegistry = new HashMap<>(descriptorRegistry);
    Map<String, TypeSpecificMarshaller<?>> typeNameRegistry = new HashMap<>();
    for (Map.Entry<Descriptor, TypeSpecificMarshaller<?>> entry : descriptorRegistry.entrySet()) {
      typeNameRegistry.put(entry.getKey().getFullName(), entry.getValue());
    }
    this.typeNameRegistry = typeNameRegistry;
  }

  /**
   * Returns the {@link TypeSpecificMarshaller} that can marshall protobufs with the same type as
   * {@code prototype}.
   */
  TypeSpecificMarshaller<?> findForPrototype(Message prototype) {
    TypeSpecificMarshaller<?> marshaller = descriptorRegistry.get(prototype.getDescriptorForType());
    if (marshaller == null) {
      throw new IllegalArgumentException(
          "Could not find marshaller for type: "
              + prototype.getDescriptorForType().getFullName()
              + ". Has it been registered?");
    }
    return marshaller;
  }

  /**
   * Returns the {@link TypeSpecificMarshaller} that can marshall protobufs with type url {@code
   * typeUrl}.
   */
  // Used by Any.
  TypeSpecificMarshaller<?> findByTypeUrl(String typeUrl) throws InvalidProtocolBufferException {
    String typeName = getTypeName(typeUrl);
    TypeSpecificMarshaller<?> marshaller = typeNameRegistry.get(typeName);
    if (marshaller == null) {
      throw new InvalidProtocolBufferException("Cannot find type for url: " + typeUrl);
    }
    return marshaller;
  }

  @SuppressWarnings("StringSplitter")
  private static String getTypeName(String typeUrl) throws InvalidProtocolBufferException {
    String[] parts = typeUrl.split("/");
    if (parts.length == 1) {
      throw new InvalidProtocolBufferException("Invalid type url found: " + typeUrl);
    }
    return parts[parts.length - 1];
  }
}
