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
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractEdge;

public class ThreadAbstractionEdge implements Comparable<ThreadAbstractionEdge>, AbstractEdge {
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
    return edge.equals(otherEdge.getCFAEdge()) && thread.equals(otherEdge.getThread());
  }

  public boolean isInitialEdge() {
    return edge == INITIAL_EDGE;
  }

  public static CFAEdge getInitialEdge() {
    return INITIAL_EDGE;
  }

  public boolean isEmptyEdge() {
    return edge == EMPTY_EDGE;
  }

  public boolean isThreadCreateEdge() {
    return (edge instanceof CFunctionSummaryStatementEdge) || (edge instanceof CFunctionCallEdge);
  }

  public static ThreadAbstractionEdge getEmptyEdge(String thread) {
    return new ThreadAbstractionEdge(EMPTY_EDGE, thread);
  }

  @Override
  public String toString() {
    return thread + ": " + edge;
  }

  @Override
  public int compareTo(ThreadAbstractionEdge pEdge) {
    if (edge.equals(pEdge.getCFAEdge()) && thread.equals(pEdge.getThread())) {
      return 0;
    }
    return this.toString().compareTo(pEdge.toString());
  }
}
