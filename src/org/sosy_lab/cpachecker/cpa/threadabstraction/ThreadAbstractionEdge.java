// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.threadabstraction;

import org.sosy_lab.cpachecker.cfa.DummyCFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractEdge;

public class ThreadAbstractionEdge implements AbstractEdge {
  private final CFAEdge edge;
  private final String thread;

  private final static CFAEdge INITIAL_EDGE =
      new DummyCFAEdge(CFANode.newDummyCFANode("dummy"), CFANode.newDummyCFANode("dummy"));
  public final static CFAEdge EMPTY_EDGE =
      new DummyCFAEdge(CFANode.newDummyCFANode("dummy"), CFANode.newDummyCFANode("dummy"));

  ThreadAbstractionEdge(CFAEdge pEdge, String pThread) {
    edge = pEdge;
    thread = pThread;
  }

  public CFAEdge getCFAEdge() {
    return edge;
  }

  public String getThread() {
    return thread;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((edge == null) ? 0 : edge.hashCode());
    result = prime * result + ((thread == null) ? 0 : thread.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ThreadAbstractionEdge)) {
      return false;
    }
    ThreadAbstractionEdge otherEdge = (ThreadAbstractionEdge) obj;
    if (thread == null || otherEdge.getThread() == null) {
      return edge.equals(otherEdge.getCFAEdge()) && thread == null && otherEdge.getThread() == null;
    }
    return edge.equals(otherEdge.getCFAEdge()) && thread.equals(otherEdge.getThread());
  }

  // Only edges that contain thread are applied edges. If thread == null the edge is regular (its
  // thread would be equal to state's currentThread)
  public boolean isAppliedEdge() {
    return thread != null;
  }

  public static CFAEdge getInitialEdge() {
    return INITIAL_EDGE;
  }

  public boolean isEmptyEdge() {
    return edge == EMPTY_EDGE;
  }

  public static ThreadAbstractionEdge getEmptyEdge() {
    return new ThreadAbstractionEdge(EMPTY_EDGE, null);
  }

  @Override
  public String toString() {
    if (thread == null) {
      return edge.toString();
    }
    return thread + ": " + edge;
  }
}
