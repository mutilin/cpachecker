// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.thread;

import static org.sosy_lab.cpachecker.cpa.thread.ThreadTransferRelation.isThreadCreateFunction;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CThreadOperationStatement.CThreadCreateStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ThreadIdProvider;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.thread.ThreadCPA.SelfSupport;
import org.sosy_lab.cpachecker.cpa.thread.ThreadDelta.SelfParallelDelta;
import org.sosy_lab.cpachecker.cpa.thread.ThreadDelta.SkipThreadDelta;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleNode;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleState;
import org.sosy_lab.cpachecker.cpa.usage.storage.Delta;
import org.sosy_lab.cpachecker.cpa.usage.storage.GenericDelta;
import org.sosy_lab.cpachecker.util.Pair;

public class ThreadState
    implements LatticeAbstractState<ThreadState>, CompatibleNode, ThreadIdProvider {
  public enum ThreadStatus {
    PARENT_THREAD,
    CREATED_THREAD,
    SELF_PARALLEL_THREAD;
  }

  protected final Map<String, ThreadStatus> threadSet;
  // The removedSet is useless now, but it will be used in future in more complicated cases
  // Do not remove it now
  protected final ImmutableMap<ThreadLabel, ThreadStatus> removedSet;
  // thread variable
  protected final String currentId;
  protected final String currentThread;
  protected final int currentPriority;

  protected final static String mainThread = "main";
  // Spotbug suggests to remove protected, which is worse
  @SuppressFBWarnings("MS_PKGPROTECT")
  protected static SelfSupport selfHandle;

  protected ThreadState(
      String pId,
      String pCurrent,
      int pPrio,
      Map<String, ThreadStatus> Tset,
      ImmutableMap<ThreadLabel, ThreadStatus> Rset) {
    threadSet = Tset;
    removedSet = Rset;
    currentId = pId;
    currentThread = pCurrent;
    currentPriority = pPrio;
  }

  @Override
  public int hashCode() {
    return Objects.hash(removedSet, threadSet);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ThreadState)) {
      return false;
    }
    ThreadState other = (ThreadState) obj;
    return Objects.equals(removedSet, other.removedSet)
        && Objects.equals(threadSet, other.threadSet);
  }

  @Override
  public int compareTo(CompatibleState pOther) {
    ThreadState other = (ThreadState) pOther;
    int result = other.threadSet.size() - this.threadSet.size(); // decreasing queue

    if (result != 0) {
      return result;
    }

    if (threadSet == other.threadSet) {
      return 0;
    }

    Iterator<Entry<String, ThreadStatus>> thisIterator = this.threadSet.entrySet().iterator();
    Iterator<Entry<String, ThreadStatus>> otherIterator = other.threadSet.entrySet().iterator();

    while (thisIterator.hasNext() && otherIterator.hasNext()) {
      Entry<String, ThreadStatus> thisEntry = thisIterator.next();
      Entry<String, ThreadStatus> otherEntry = otherIterator.next();
      String thisLabel = thisEntry.getKey();
      String otherLabel = otherEntry.getKey();
      result = thisLabel.compareTo(otherLabel);
      if (result != 0) {
        return result;
      }
      ThreadStatus thisStatus = this.threadSet.get(thisLabel);
      ThreadStatus otherStatus = other.threadSet.get(otherLabel);
      result = thisStatus.compareTo(otherStatus);
      if (result != 0) {
        return result;
      }
    }
    return 0;
  }

  @Override
  public boolean isCompatibleWith(CompatibleState state) {
    Preconditions.checkArgument(state instanceof ThreadState);
    ThreadState other = (ThreadState) state;

    // Does not matter which set to iterate, anyway we need an intersection
    for (Entry<String, ThreadStatus> entry : threadSet.entrySet()) {
      String l = entry.getKey();
      ThreadStatus s = entry.getValue();

      if (other.threadSet.containsKey(l)) {
        ThreadStatus otherL = other.threadSet.get(l);

        /*
         * In case of self-parallel we need to consider it to be parallel with any other to support
         * such strange cases: pthread_create(&t, func1); pthread_create(&t, func2);
         */

        if (s == ThreadStatus.SELF_PARALLEL_THREAD
            || otherL == ThreadStatus.SELF_PARALLEL_THREAD
            || (s == ThreadStatus.PARENT_THREAD && otherL != ThreadStatus.PARENT_THREAD)
            || (s == ThreadStatus.CREATED_THREAD && otherL == ThreadStatus.PARENT_THREAD)) {
          return true;
        }
      }
    }
    return false;
  }

  public static ThreadState emptyState(SelfSupport pSelf) {
    selfHandle = pSelf;
    return new ThreadState(mainThread, mainThread, 0, ImmutableMap.of(), ImmutableMap.of());
  }

  @Override
  public String toString() {
    return getCurrentId() + " = " + getCurrentThread() + ":" + getThreadSet();
  }

  protected String getCurrentId() {
    return currentId;
  }

  public String getCurrentThread() {
    return currentThread;
  }

  @Override
  public boolean cover(CompatibleNode pNode) {
    return ((ThreadState)pNode).isLessOrEqual(this);
  }

  @Override
  public ThreadState join(ThreadState pOther) {
    throw new UnsupportedOperationException("Join is not implemented for ThreadCPA");
  }

  @Override
  public boolean isLessOrEqual(ThreadState pOther) {
    boolean b =
        Objects.equals(removedSet, pOther.removedSet);
    if (b && pOther.threadSet == threadSet) {
      return true;
    }
    if (threadSet == pOther.threadSet) {
      return true;
    }
    if (threadSet.size() > pOther.threadSet.size()) {
      return false;
    }
    return pOther.threadSet.entrySet().containsAll(threadSet.entrySet());
  }

  public boolean hasEmptyEffect() {
    return true;
  }

  public Map<String, ThreadStatus> getThreadSet() {
    return threadSet;
  }

  public int getPriority() {
    return currentPriority;
  }

  ImmutableMap<ThreadLabel, ThreadStatus> getRemovedSet() {
    return removedSet;
  }

  int getThreadSize() {
    // Only for statistics
    return threadSet.size();
  }

  @Override
  public String getThreadIdForEdge(CFAEdge pEdge) {
    return this.toString();
  }

  public ThreadState
      copyWith(String pId, String pCurrent, int pPrio, Map<String, ThreadStatus> tSet) {
    return new ThreadState(pId, pCurrent, pPrio, tSet, this.removedSet);
  }

  public ThreadState copyWith(Map<String, ThreadStatus> tSet) {
    return copyWith(this.currentId, this.currentThread, this.currentPriority, tSet);
  }

  @Override
  public Delta<CompatibleNode> getDeltaBetween(CompatibleNode pOther) {
    ThreadState pState = (ThreadState) pOther;
    Map<String, ThreadStatus> expanded = pState.getThreadSet();
    Map<String, ThreadStatus> newSet;

    if (expanded.equals(threadSet)) {
      return GenericDelta.getInstance();
    } else if (threadSet.isEmpty()) {
      newSet = expanded;
    } else {
      newSet = new TreeMap<>();
      for (Entry<String, ThreadStatus> entry : expanded.entrySet()) {
        if (threadSet.containsKey(entry.getKey())) {
          if (!threadSet.get(entry.getKey()).equals(entry.getValue())) {
            throw new UnsupportedOperationException(
                "Statuses for thread " + entry.getKey() + " differs");
          }
        } else {
          newSet.put(entry.getKey(), entry.getValue());
        }
      }
    }
    if (selfHandle == SelfSupport.SUPPORT) {
      return new SelfParallelDelta(newSet);
    } else if (selfHandle == SelfSupport.SKIP) {
      return new SkipThreadDelta(newSet);
    } else {
      return new ThreadDelta(newSet);
    }
  }

  @Override
  public List<Pair<String, String>>
      getSpawnedThreadIdByEdge(CFAEdge pEdge, ARGState pState) {

    if (pEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
      CFunctionCall fCall = ((CFunctionCallEdge) pEdge).getSummaryEdge().getExpression();
      if (isThreadCreateFunction(fCall)) {
        CThreadCreateStatement tCall = (CThreadCreateStatement) fCall;
        return Collections.singletonList(
            Pair.of(tCall.getVariableName(), pEdge.getSuccessor().getFunctionName()));
      }
    }
    if (pEdge instanceof CFunctionSummaryStatementEdge) {
      CFunctionCall functionCall = ((CFunctionSummaryStatementEdge) pEdge).getFunctionCall();
      if (isThreadCreateFunction(functionCall)) {
        CThreadCreateStatement tCall = (CThreadCreateStatement) functionCall;
        return Collections.singletonList(
            Pair.of(
                tCall.getVariableName(),
                ((CFunctionSummaryStatementEdge) pEdge).getFunctionName()));
      }
    }
    return ImmutableList.of();
  }
}
