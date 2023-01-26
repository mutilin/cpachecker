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

  private final Map<String, CFAEdge> threadSet;
  private final String currentThread;
  private final boolean selfParallel;

  protected final static String MAIN_THREAD = "main";

  protected ThreadAbstractionState(
      Map<String, CFAEdge> Tset,
      String pCurrent,
      boolean pSelfParallel) {
    threadSet = Tset;
    currentThread = pCurrent;
    selfParallel = pSelfParallel;
  }

  public static ThreadAbstractionState emptyState() {
    return new ThreadAbstractionState(ImmutableMap.of(), MAIN_THREAD, false);
  }

  public String getCurrentThread() {
    return currentThread;
  }

  public boolean isSelfParallel() {
    return selfParallel;
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
    return new ThreadAbstractionState(Tset, this.currentThread, this.selfParallel);
  }

  @Override
  public ThreadAbstractionState join(ThreadAbstractionState pOther) {
    throw new UnsupportedOperationException("Join is not implemented for ThreadAbstractionCPA");
  }

  public boolean isCompatibleWith(ThreadAbstractionState pOther) {
    return pOther.threadSet.equals(threadSet)
        && ((this.isSelfParallel() && pOther.isSelfParallel())
            || !currentThread.equals(pOther.currentThread));
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
