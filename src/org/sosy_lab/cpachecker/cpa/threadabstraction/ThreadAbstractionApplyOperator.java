// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.threadabstraction;

import java.util.List;
import org.sosy_lab.cpachecker.core.interfaces.AbstractEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ApplyOperator;

public class ThreadAbstractionApplyOperator implements ApplyOperator {

  @Override
  public AbstractState apply(AbstractState pState1, AbstractState pState2) {
    if (pState1 instanceof ThreadAbstractionState
        && pState2 instanceof ThreadAbstractionStateWithEdge) {
      ThreadAbstractionState state1 = (ThreadAbstractionState) pState1;
      ThreadAbstractionStateWithEdge state2 = (ThreadAbstractionStateWithEdge) pState2;

      if (state2.getAbstractEdge() != null) {
        if (state2.isCompatibleWith(state1)) {
          return new ThreadAbstractionStateWithEdge(state1, state2.getAppliedEdge());
        }
      }
    }
    return null;
  }

  @Override
  public AbstractState project(AbstractState pParent, AbstractState pChild, AbstractEdge pEdge) {
    return project(pParent, pChild);
  }

  @Override
  public AbstractState project(AbstractState pParent, AbstractState pChild) {
    ThreadAbstractionState parent = (ThreadAbstractionState) pParent;
    ThreadAbstractionState child = (ThreadAbstractionState) pChild;
    return new ThreadAbstractionStateWithEdge(parent, prepareEdge(parent, child));
  }

  private ThreadAbstractionEdge
      prepareEdge(ThreadAbstractionState parent, ThreadAbstractionState child) {

    if (parent.equals(child)) {
      // The edge is not in precision
      return ThreadAbstractionEdge.getEmptyEdge();
    }

    if (!child.getCurrentThread().equals(parent.getCurrentThread())) {
      // thread creation, already have this projection
      return null;
    }

    String currentThread = parent.getCurrentThread();

    // Note: the creation of the first thread is not projected but it is not needed anyway
    if (!parent.getThreadSet().isEmpty()) {
      return new ThreadAbstractionEdge(child.getThreadSet().get(currentThread), null);
    }
    return null;
  }

  @Override
  public boolean canBeAnythingApplied(AbstractState pState) {
    return true;
  }

  @Override
  public boolean isInvariantToEffects(AbstractState pState) {
    return false;
  }

  @Override
  public AbstractState
      createCompositeProjection(List<AbstractState> pStates, List<AbstractEdge> pEdges) {
    throw new UnsupportedOperationException(
        "Composite projection is not supported for TA Analysis");
  }

}
