/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.rcucpa.rcusearch;

import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperState;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.cpa.pointer2.PointerDomain;
import org.sosy_lab.cpachecker.cpa.pointer2.PointerState;
import org.sosy_lab.cpachecker.cpa.pointer2.PointerStatistics;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

public class RCUSearchState extends AbstractSingleWrapperState
    implements LatticeAbstractState<RCUSearchState> {
  private static final long serialVersionUID = 1L;
  private final ImmutableSet<MemoryLocation> rcuPointers;

  public RCUSearchState(ImmutableSet<MemoryLocation> pointers, PointerState pPointerState) {
    super(pPointerState);
    rcuPointers = pointers;
  }

  public RCUSearchState() {
    this(ImmutableSet.of(), PointerState.INITIAL_STATE);
  }

  public Set<MemoryLocation> getRcuPointers() {
    return rcuPointers;
  }

  @Override
  public boolean equals(Object pO) {
    if (this == pO) {
      return true;
    }
    if (pO == null || getClass() != pO.getClass()) {
      return false;
    }

    RCUSearchState that = (RCUSearchState) pO;

    if (!rcuPointers.equals(that.rcuPointers)) {
      return false;
    }
    if (!super.equals(that.getWrappedState())) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = rcuPointers.hashCode();
    result = 31 * result + super.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return rcuPointers.toString() + (rcuPointers.isEmpty() ? " EMPTY" : " NOT EMPTY") + " # " +
        ((PointerState) getWrappedState()).getPointsToMap();
  }

  public static RCUSearchState copyOf(RCUSearchState pState) {
    return new RCUSearchState(
        pState.rcuPointers,
        (PointerState) pState.getWrappedState());
  }

  public Map<MemoryLocation, Set<MemoryLocation>> getPointsTo() {
    return PointerStatistics
        .replaceTopsAndBots(((PointerState) getWrappedState()).getPointsToMap());
  }

  @Override
  public RCUSearchState join(RCUSearchState pOther) throws CPAException, InterruptedException {
    Set<MemoryLocation> pointers = new TreeSet<>(rcuPointers);
    pointers.addAll(pOther.getRcuPointers());

    PointerState pointerState1 = (PointerState) getWrappedState();
    PointerState pointerState2 = (PointerState) pOther.getWrappedState();

    PointerDomain pDomain = PointerDomain.INSTANCE;
    PointerState result = (PointerState) pDomain.join(pointerState1, pointerState2);

    if (pointers.equals(rcuPointers) && result.equals(pointerState1)) {
      return this;
    } else if (pointers.equals(rcuPointers) && result.equals(pointerState1)) {
      return pOther;
    } else {
      return new RCUSearchState(ImmutableSet.copyOf(pointers), result);
    }
  }

  @Override
  public boolean isLessOrEqual(RCUSearchState pOther) throws CPAException, InterruptedException {
    if (this == pOther) {
      return true;
    }

    PointerState pState1 = (PointerState) getWrappedState();
    PointerState pState2 = (PointerState) pOther.getWrappedState();

    return pOther
        .getRcuPointers()
        .containsAll(
            this.getRcuPointers())
        && PointerDomain.INSTANCE.isLessOrEqual(pState2, pState1);
  }
}
