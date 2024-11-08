/*
 * Copyright (c) Choko (choko@curioswitch.org)
 * SPDX-License-Identifier: MIT
 */

package org.curioswitch.common.protobuf.json;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

// Because it is fairly expensive to build a TypeSpecificMarshaller, we go ahead
// and cache to save time across different MessageMarshaller instances. We still
// want to make sure they can be garbage collected, so we use weak references.
final class MarshallerCache {

  private final Map<MarshallerOptions, MarshallerReference> cache = new HashMap<>();
  private final ReferenceQueue<TypeSpecificMarshaller<?>> queue = new ReferenceQueue<>();
  private final ReentrantLock lock = new ReentrantLock();

  @Nullable
  TypeSpecificMarshaller<?> get(MarshallerOptions key) {
    lock.lock();
    try {
      clean();
      MarshallerReference ref = cache.get(key);
      return ref != null ? ref.get() : null;
    } finally {
      lock.unlock();
    }
  }

  void put(MarshallerOptions key, TypeSpecificMarshaller<?> value) {
    lock.lock();
    try {
      clean();
      cache.put(key, new MarshallerReference(key, value, queue));
    } finally {
      lock.unlock();
    }
  }

  private void clean() {
    MarshallerReference ref;
    while ((ref = (MarshallerReference) queue.poll()) != null) {
      cache.remove(ref.getKey());
    }
  }

  private static class MarshallerReference extends WeakReference<TypeSpecificMarshaller<?>> {
    private final MarshallerOptions key;

    MarshallerReference(
        MarshallerOptions key,
        TypeSpecificMarshaller<?> value,
        ReferenceQueue<TypeSpecificMarshaller<?>> queue) {
      super(value, queue);
      this.key = key;
    }

    MarshallerOptions getKey() {
      return key;
    }
  }
}
