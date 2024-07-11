// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.threadabstraction;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.DummyCFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractEdge;

public class ThreadAbstractionEdge implements AbstractEdge {
  private final CFAEdge edge;
  private final @Nullable String thread;

  private static final CFAEdge INITIAL_EDGE =
      new DummyCFAEdge(CFANode.newDummyCFANode("dummy"), CFANode.newDummyCFANode("dummy"));
  public static final CFAEdge EMPTY_EDGE =
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
    return Objects.hash(edge, thread);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ThreadAbstractionEdge other
        && edge.equals(other.edge)
        && Objects.equals(thread, other.thread);
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
