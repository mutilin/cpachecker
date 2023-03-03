// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.storage;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleNode;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleState;
import org.sosy_lab.cpachecker.util.AbstractStates;

public class UsageDelta implements Delta<List<CompatibleNode>> {

  private static class EmptyUsageDelta extends UsageDelta {
    private final static EmptyUsageDelta instance = new EmptyUsageDelta();

    public EmptyUsageDelta() {
      super(ImmutableList.of());
    }

    @Override
    public List<CompatibleNode> apply(List<CompatibleNode> pState) {
      return pState;
    }

    @Override
    public boolean covers(Delta<List<CompatibleNode>> pDelta) {
      return pDelta == EmptyUsageDelta.instance;
    }

    @Override
    public UsageDelta add(Delta<List<CompatibleNode>> pDelta) {
      return (UsageDelta) pDelta;
    }

    @Override
    public boolean equals(Object pDelta) {
      return pDelta == instance;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(instance);
    }
  }

  private final List<Delta<CompatibleNode>> nestedDeltas;

  public UsageDelta(List<Delta<CompatibleNode>> pList) {
    nestedDeltas = pList;
  }

  @Override
  public List<CompatibleNode> apply(List<CompatibleNode> pState) {
    ImmutableList.Builder<CompatibleNode> result = ImmutableList.builder();
    assert nestedDeltas.size() == pState.size();
    boolean changed = false;

    for (int i = 0; i < nestedDeltas.size(); i++) {
      CompatibleNode oldState = pState.get(i);
      CompatibleNode newState = nestedDeltas.get(i).apply(oldState);
      if (newState != oldState) {
        changed = true;
      } else if (newState == null) {
        // infeasible
        return ImmutableList.of();
      }
      result.add(newState);
    }
    if (changed) {
      return result.build();
    } else {
      return pState;
    }
  }

  @Override
  public boolean covers(Delta<List<CompatibleNode>> pDelta) {
    if (pDelta == EmptyUsageDelta.instance) {
      return false;
    }
    UsageDelta pOther = (UsageDelta) pDelta;
    assert nestedDeltas.size() == pOther.nestedDeltas.size();

    for (int i = 0; i < this.nestedDeltas.size(); i++) {
      if (!nestedDeltas.get(i).covers(pOther.nestedDeltas.get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean equals(Object pDelta) {
    if (pDelta == EmptyUsageDelta.instance) {
      return false;
    }
    if (pDelta instanceof UsageDelta) {
      UsageDelta pOther = (UsageDelta) pDelta;
      return nestedDeltas.equals(pOther.nestedDeltas);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(nestedDeltas);
  }

  @SuppressWarnings("unchecked")
  public static UsageDelta
      constructDeltaBetween(AbstractState reducedState, AbstractState rootState) {

    ImmutableList.Builder<Delta<CompatibleNode>> storedStates = ImmutableList.builder();

    FluentIterable<CompatibleNode> reducedStates =
        AbstractStates.asIterable(reducedState)
            .filter(CompatibleState.class)
            .transform(CompatibleState::getCompatibleNode);
    FluentIterable<CompatibleNode> rootStates =
        AbstractStates.asIterable(rootState)
            .filter(CompatibleState.class)
            .transform(CompatibleState::getCompatibleNode);

    assert reducedStates.size() == rootStates.size();
    boolean nonTrivial = false;

    for (int i = 0; i < reducedStates.size(); i++) {
      CompatibleNode nestedReducedState = reducedStates.get(i);
      CompatibleNode nestedRootState = rootStates.get(i);

      Delta<CompatibleNode> delta =
          (Delta<CompatibleNode>) nestedReducedState.getDeltaBetween(nestedRootState);

      storedStates.add(delta);
      if (delta != GenericDelta.getInstance()) {
        nonTrivial = true;
      }
    }

    if (nonTrivial) {
      return new UsageDelta(storedStates.build());
    } else {
      return EmptyUsageDelta.instance;
    }
  }

  @Override
  public UsageDelta add(Delta<List<CompatibleNode>> pDelta) {
    if (pDelta == EmptyUsageDelta.instance) {
      return this;
    }
    UsageDelta pOther = (UsageDelta) pDelta;
    ImmutableList.Builder<Delta<CompatibleNode>> result = ImmutableList.builder();
    assert nestedDeltas.size() == pOther.nestedDeltas.size();
    boolean changed = false;

    for (int i = 0; i < nestedDeltas.size(); i++) {
      Delta<CompatibleNode> oldDelta = nestedDeltas.get(i);
      Delta<CompatibleNode> newDelta = oldDelta.add(pOther.nestedDeltas.get(i));
      if (oldDelta != newDelta) {
        changed = true;
      }
      result.add(newDelta);
    }
    if (changed) {
      return new UsageDelta(result.build());
    } else {
      return this;
    }
  }

}
