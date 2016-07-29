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

import static org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.ClassMatcher.match;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ExceptionWrapper {

  @FunctionalInterface
  public interface ThrowingConsumer<T> {
     void accept(T t) throws Exception;
  }

  @FunctionalInterface
  public interface ThrowingBiConsumer<T1, T2> {
     void accept(T1 t1, T2 t2) throws Exception;
  }

  @FunctionalInterface
  public interface ThrowingRunnable<E extends Exception> {
    void run() throws E;
  }

  @FunctionalInterface
  public interface ThrowingRunnable2<E1 extends Exception, E2 extends Exception> {
    void run() throws E1, E2;
  }

  private static class WrappedException extends RuntimeException {

    private WrappedException(final Exception e) {
      this.e = e;
    }

    public Exception getException() {
      return e;
    }

    private final Exception e;
    private static final long serialVersionUID = -4533358885010669201L;
  }

  public static <S> Consumer<S> wrap(final ThrowingConsumer<S> c) {
    return (x) -> {
      try {
        c.accept(x);
      } catch (Exception e) {
        throw new WrappedException(e);
      }};
  }

  public static <S1, S2> BiConsumer<S1, S2> wrap(final ThrowingBiConsumer<S1, S2> c) {
    return (x, y) -> { try { c.accept(x, y); } catch (Exception e) { throw new WrappedException(e);}};
  }

  public static <E extends Exception> void reraise(final Class<E> cl, final ThrowingRunnable<E> a) throws E {
    try {
      a.run();
    } catch (WrappedException e) {
      final Exception ex = e.getException();
      final Optional<E> r = match(ex).with(cl, Function.identity()).result();
      if (r.isPresent()) {
        r.get();
      } else {
        if (ex instanceof RuntimeException) {
          throw (RuntimeException) ex;
        } else {
          throw e;
        }
      }
    }
  }

  public static <E1 extends Exception, E2 extends Exception> void reraise2(final Class<E1> cl1,
                                                                           final Class<E2> cl2,
                                                                           final ThrowingRunnable2<E1, E2> a)
                                                                               throws E1, E2 {
    try {
      a.run();
    } catch (WrappedException e) {
      final Exception ex = e.getException();
      final Optional<E1> r1 = match(ex).with(cl1, Function.identity()).result();
      if (r1.isPresent()) {
        throw r1.get();
      } else {
        final Optional<E2> r2 = match(ex).with(cl2, Function.identity()).result();
        if (r2.isPresent()) {
          throw r2.get();
        } else {
          if (ex instanceof RuntimeException) {
            throw (RuntimeException) ex;
          } else {
            throw e;
          }
        }
      }
    }
  }
}
