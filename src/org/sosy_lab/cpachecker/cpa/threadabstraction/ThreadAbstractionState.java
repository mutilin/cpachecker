// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.threadabstraction;

import static org.sosy_lab.cpachecker.cpa.thread.ThreadTransferRelation.isThreadCreateFunction;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CThreadOperationStatement.CThreadCreateStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ThreadIdProvider;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.Pair;

public class ThreadAbstractionState
    implements LatticeAbstractState<ThreadAbstractionState>, ThreadIdProvider {

  protected final Map<String, CFAEdge> threadSet;
  protected final String currentThread;

  protected final static String MAIN_THREAD = "main";

  protected ThreadAbstractionState(
      Map<String, CFAEdge> Tset,
      String pCurrent) {
    threadSet = Tset;
    currentThread = pCurrent;
  }

  public static ThreadAbstractionState emptyState() {
    return new ThreadAbstractionState(ImmutableMap.of(), MAIN_THREAD);
  }

  public String getCurrentThread() {
    return currentThread;
  }

  public Map<String, CFAEdge> getThreadSet() {
    return threadSet;
  }

  public int threadSetSize() {
    return threadSet.size();
  }

  @Override
  public boolean isLessOrEqual(ThreadAbstractionState pOther) {
    // TODO Maybe later we can weaken this condition
    return this.equals(pOther);
  }

  public ThreadAbstractionState copyWith(Map<String, CFAEdge> Tset) {
    return new ThreadAbstractionState(Tset, this.currentThread);
  }

  @Override
  public ThreadAbstractionState join(ThreadAbstractionState pOther) {
    throw new UnsupportedOperationException("Join is not implemented for ThreadAbstractionCPA");
  }

  public boolean isCompatibleWith(ThreadAbstractionState pOther) {
    if (!pOther.threadSet.equals(threadSet)) {
      if (!pOther.threadSet.keySet().equals(threadSet.keySet())) {
        return false;
      }
      for (Entry<String, CFAEdge> entry : threadSet.entrySet()) {
        String th = entry.getKey();
        if (!pOther.threadSet.get(th).equals(threadSet.get(th))) {
          if (!((pOther.threadSet.get(th) instanceof CFunctionSummaryStatementEdge
              && threadSet.get(th) instanceof CFunctionCallEdge)
              || (threadSet.get(th) instanceof CFunctionSummaryStatementEdge
                  && pOther.threadSet.get(th) instanceof CFunctionCallEdge))) {
            return false;
          }
        }
      }
    }
    return true;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ThreadAbstractionState)) {
      return false;
    }
    ThreadAbstractionState pOther = (ThreadAbstractionState) obj;
    return this.currentThread.equals(pOther.currentThread)
        && this.threadSet.equals(pOther.threadSet);
  }

  @Override
  public String toString() {
    return getCurrentThread() + " " + getThreadSet();
  }

  @Override
  public String getThreadIdForEdge(CFAEdge pEdge) {
    return pEdge.getSuccessor().getFunctionName();
  }

  @Override
  public List<Pair<String, String>> getSpawnedThreadIdByEdge(CFAEdge pEdge, ARGState pState) {
    List<Pair<String, String>> result = new ArrayList<>();

    if (pEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
      CFunctionCall fCall = ((CFunctionCallEdge) pEdge).getSummaryEdge().getExpression();
      if (isThreadCreateFunction(fCall)) {
        CThreadCreateStatement tCall = (CThreadCreateStatement) fCall;
        result.add(Pair.of(tCall.getVariableName(), pEdge.getSuccessor().getFunctionName()));
      }
    }
    if (pEdge instanceof CFunctionSummaryStatementEdge) {
      CFunctionCall functionCall = ((CFunctionSummaryStatementEdge) pEdge).getFunctionCall();
      if (isThreadCreateFunction(functionCall)) {
        CThreadCreateStatement tCall = (CThreadCreateStatement) functionCall;
        result.add(
            Pair.of(
                tCall.getVariableName(),
                ((CFunctionSummaryStatementEdge) pEdge).getFunctionName()));
      }
    }
    return result;
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
