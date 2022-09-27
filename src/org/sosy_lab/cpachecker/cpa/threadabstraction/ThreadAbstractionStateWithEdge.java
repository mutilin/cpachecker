// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.threadabstraction;

import java.util.Map;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithEdge;

public class ThreadAbstractionStateWithEdge extends ThreadAbstractionState
    implements AbstractStateWithEdge {

  private final ThreadAbstractionEdge edge;

  protected ThreadAbstractionStateWithEdge(
      Map<String, CFAEdge> Tset,
      String pCurrent,
      ThreadAbstractionEdge pEdge) {
    super(Tset, pCurrent);
    edge = pEdge;
  }

  protected ThreadAbstractionStateWithEdge(
      ThreadAbstractionState pState,
      ThreadAbstractionEdge pEdge) {
    super(pState.threadSet, pState.currentThread);
    edge = pEdge;
  }

  @Override
  public boolean isCompatibleWith(ThreadAbstractionState pOther) {
    return super.isCompatibleWith(pOther) && !this.edge.getThread().equals(pOther.currentThread);
  }

  public ThreadAbstractionState getThreadAbstractionState() {
    return new ThreadAbstractionState(this.getThreadSet(), this.getCurrentThread());
  }

  @Override
  public ThreadAbstractionEdge getAbstractEdge() {
    return edge;
  }

  @Override
  public boolean hasEmptyEffect() {
    if (this.threadSetSize() < 2) { // we need to have at least 2 threads
      return true;
    }
    return edge == null;
  }

  @Override
  public boolean isProjection() {
    return true;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ThreadAbstractionStateWithEdge)) {
      return false;
    }

    ThreadAbstractionStateWithEdge pOther = (ThreadAbstractionStateWithEdge) obj;
    return super.equals(pOther) && edge.equals(pOther.getAbstractEdge());
  }

  @Override
  public String toString() {
    return super.toString() + "\n " + edge;
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
