// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.thread;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CThreadOperationStatement.CThreadCreateStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CThreadOperationStatement.CThreadJoinStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.thread.ThreadAbstractEdge.ThreadAction;
import org.sosy_lab.cpachecker.cpa.thread.ThreadCPA.SelfSupport;
import org.sosy_lab.cpachecker.cpa.thread.ThreadState.ThreadStatus;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;

public class ThreadTransferRelation implements TransferRelation {
  private final ThreadCPAStatistics threadStatistics;
  private final SelfSupport handleSameThreads;

  public ThreadTransferRelation(SelfSupport pSelf, ThreadCPAStatistics tStats) {
    handleSameThreads = pSelf;
    threadStatistics = tStats;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
      throws CPATransferException, InterruptedException {

    threadStatistics.transfer.start();
    ThreadState tState = (ThreadState) pState;
    ThreadState newState = tState;

    if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
      newState = handleFunctionCall(tState, (CFunctionCallEdge) pCfaEdge);
    } else if (pCfaEdge instanceof CFunctionSummaryStatementEdge) {
      CFunctionCall functionCall = ((CFunctionSummaryStatementEdge) pCfaEdge).getFunctionCall();
      if (isThreadCreateFunction(functionCall)) {
        newState = handleParentThread(tState, (CThreadCreateStatement) functionCall);
      }
    } else if (pCfaEdge.getEdgeType() == CFAEdgeType.StatementEdge) {
      CStatement stmnt = ((CStatementEdge) pCfaEdge).getStatement();
      if (stmnt instanceof CThreadJoinStatement) {
        threadStatistics.threadJoins.inc();
        newState = joinThread(tState, (CThreadJoinStatement) stmnt);
      }
    } else if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionReturnEdge) {
      CFunctionCall functionCall =
          ((CFunctionReturnEdge) pCfaEdge).getSummaryEdge().getExpression();
      if (isThreadCreateFunction(functionCall)) {
        newState = null;
      }
    }
    threadStatistics.transfer.stop();
    if (newState != null) {
      return Collections.singleton(newState);
    } else {
      return ImmutableSet.of();
    }
  }

  private ThreadState handleFunctionCall(ThreadState state, CFunctionCallEdge pCfaEdge)
      throws CPATransferException {

    ThreadState newState = state;
    CFunctionCall fCall = pCfaEdge.getFunctionCall();
    if (isThreadCreateFunction(fCall)) {
      newState = handleChildThread(state, (CThreadCreateStatement) fCall);
      if (threadStatistics.createdThreads.add(
          Pair.of(
              pCfaEdge.getSuccessor().getFunctionName(),
              ((CThreadCreateStatement) fCall).getPriority()))) {
        threadStatistics.threadCreates.inc();
        // Just to statistics
        threadStatistics.maxNumberOfThreads.setNextValue(state.getThreadSize());
      }
    } else if (isThreadJoinFunction(fCall)) {
      threadStatistics.threadJoins.inc();
      newState = joinThread(state, (CThreadJoinStatement) fCall);
    }
    return newState;
  }

  private ThreadState handleParentThread(ThreadState state, CThreadCreateStatement tCall)
      throws CPATransferException {
    return createThread(state, tCall, ThreadStatus.PARENT_THREAD);
  }

  private ThreadState handleChildThread(ThreadState state, CThreadCreateStatement tCall)
      throws CPATransferException {
    return createThread(
        state,
        tCall,
        tCall.isSelfParallel() ? ThreadStatus.SELF_PARALLEL_THREAD : ThreadStatus.CREATED_THREAD);
  }

  private ThreadState createThread(
      ThreadState state, CThreadCreateStatement tCall, ThreadStatus pParentThread)
      throws CPATransferException {
    final String pVarName = tCall.getVariableName();
    // Just to info
    final String pFunctionName =
        tCall.getFunctionCallExpression().getFunctionNameExpression().toASTString();

    Map<String, ThreadStatus> tSet = state.getThreadSet();
    ThreadStatus status = pParentThread;
    // Note, there is possible to have thread_create_N function
    // We handle it as self_parallel independently from options
    if (tSet.containsKey(pVarName)) {
      if (handleSameThreads == SelfSupport.SUPPORT) {
        status = ThreadStatus.SELF_PARALLEL_THREAD;
      } else if (handleSameThreads == SelfSupport.SKIP) {
        return null;
      } else if (handleSameThreads == SelfSupport.FAIL) {
        throw new CPATransferException(
            "Can not create thread "
                + pFunctionName
                + ", it was already created. Line "
                + tCall.getFileLocation().getStartingLineNumber());
      }
    }

    if (!tSet.isEmpty()) {
      if (tSet.get(state.getCurrentId()) == ThreadStatus.SELF_PARALLEL_THREAD) {
        // Can add only the same status
        status = ThreadStatus.SELF_PARALLEL_THREAD;
      }
    }
    Map<String, ThreadStatus> newSet = new TreeMap<>(tSet);
    newSet.put(pVarName, status);
    String currentId;
    int prio;
    if (pParentThread == ThreadStatus.PARENT_THREAD) {
      currentId = state.getCurrentThread();
      prio = state.getPriority();
    } else {
      currentId = pVarName;
      prio = tCall.getPriority();
    }
    return state.copyWith(currentId, pFunctionName, prio, newSet);
  }

  public ThreadState joinThread(ThreadState state, CThreadJoinStatement jCall) {
    // If we found several labels for different functions
    // it means, that there are several thread created for one thread variable.
    // Not a good situation, but it is not forbidden, so join the last assigned thread
    Map<String, ThreadStatus> tSet = state.getThreadSet();

    String var = jCall.getVariableName();
    if (tSet.containsKey(var) && tSet.get(var) != ThreadStatus.CREATED_THREAD) {
      Map<String, ThreadStatus> newSet = new TreeMap<>(tSet);
      newSet.remove(var);
      return state.copyWith(newSet);
    }
    return state;
  }

  public static boolean isThreadCreateFunction(CFunctionCall statement) {
    return (statement instanceof CThreadCreateStatement);
  }

  private boolean isThreadJoinFunction(CFunctionCall statement) {
    return statement instanceof CThreadJoinStatement;
  }

  public Statistics getStatistics() {
    return threadStatistics;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(
      AbstractState pState, Precision pPrecision)
      throws CPATransferException, InterruptedException {

    ThreadTMStateWithEdge stateWithEdge = (ThreadTMStateWithEdge) pState;
    ThreadAbstractEdge edge = stateWithEdge.getAbstractEdge();
    Map<String, ThreadStatus> tSet = stateWithEdge.getThreadSet();
    Map<String, ThreadStatus> newSet = tSet;

    if (edge != null) {
      ThreadAction action = edge.getAction().getFirst();
      String threadId = edge.getAction().getSecond();

      // TMP implementation
      ThreadStatus status = ThreadStatus.CREATED_THREAD;
      if (action == ThreadAction.CREATE) {
        // We may already apply the create thread
        if (!tSet.containsKey(threadId)) {
          newSet = new TreeMap<>(tSet);
          newSet.put(threadId, status);
        }
      } else if (action == ThreadAction.JOIN) {
        if (stateWithEdge.getCurrentId().equals(threadId)) {
          // Means someone wants to join current thread. Stops the branch, as the case is
          // impossible
          return ImmutableList.of();
        } else {
          newSet = new TreeMap<>(tSet);
          newSet.remove(threadId);
        }
      } else {
        throw new UnsupportedOperationException("Unsupported action " + action);
      }
    }

    // To reset the edge
    return Collections.singleton(stateWithEdge.copyWith(newSet));
  }
}
