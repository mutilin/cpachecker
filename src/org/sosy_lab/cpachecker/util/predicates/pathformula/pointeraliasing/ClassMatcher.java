/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ClassMatcher {
  public static class ClassMatcher1<R> {
    private ClassMatcher1(final Object scrutinee, final R result) {
        this.scrutinee = scrutinee;
        this.result = Optional.of(result);
    }

    private ClassMatcher1(final Object scrutinee) {
      this.scrutinee = scrutinee;
      this.result = Optional.empty();
    }

    public <Y> ClassMatcher1<R> with(final Class<Y> targetClass, final Function<? super Y, ? extends R> f) {
      if (result.isPresent()) {
        return this;
      }
      if (targetClass.isInstance(scrutinee)) {
        result = Optional.of(f.apply(targetClass.cast(scrutinee)));
      }
      return this;
    }

    public ClassMatcher1<R> withNull(final R r) {
      if (result.isPresent()) {
        return this;
      }
      if (scrutinee == null) {
        result = Optional.of(r);
      }
      return this;
    }

    public R orElse(final R r) {
      return result.orElse(r);
    }

    public R orElseGet(Supplier<? extends R> s) {
      return result.orElseGet(s);
    }

    public <X extends Throwable> R orElseThrow(Supplier<X> e) throws X {
      return result.orElseThrow(e);
    }

    public @Nonnull Optional<R> result() {
      return result;
    }

    private final @Nullable Object scrutinee;
    private @Nonnull Optional<R> result = Optional.empty();
  }

  private ClassMatcher(Object scrutinee) {
    this.scrutinee = scrutinee;
  }

  public static ClassMatcher match(final @Nullable Object scrutinee) {
    return new ClassMatcher(scrutinee);
  }

  public <Y, R> ClassMatcher1<R> with(final Class<Y> targetClass, final Function<? super Y, ? extends R> f) {
    if (targetClass.isInstance(scrutinee)) {
      return new ClassMatcher1<>(scrutinee, f.apply(targetClass.cast(scrutinee)));
    }
    return new ClassMatcher1<>(scrutinee);
  }

  private final @Nullable Object scrutinee;
}
