// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.threadabstraction;

import static org.sosy_lab.cpachecker.cpa.thread.ThreadTransferRelation.isThreadCreateFunction;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CThreadOperationStatement.CThreadCreateStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.location.LocationApplyOperator;
import org.sosy_lab.cpachecker.cpa.threadabstraction.ThreadAbstractionOptions.AddProjections;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

public class ThreadAbstractionTransferRelation implements TransferRelation {

  final ThreadAbstractionOptions options;

  public ThreadAbstractionTransferRelation(Configuration pConfig)
      throws InvalidConfigurationException {
    options = new ThreadAbstractionOptions();
    pConfig.inject(options);
  }

  /** This method gets successors only for applied states. */
  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessors(AbstractState pState, Precision pPrecision)
          throws CPATransferException, InterruptedException {

    // Applied state is TAstate with edge such that thread of the edge is not null
    assert (pState instanceof ThreadAbstractionStateWithEdge);
    ThreadAbstractionStateWithEdge state = (ThreadAbstractionStateWithEdge) pState;

    assert (state.getAbstractEdge().getThread() != null);
    CFAEdge edge = state.getAbstractEdge().getCFAEdge();
    String thread = state.getAbstractEdge().getThread();

    // CFunctionCallEdges are not projected (only CFunctionSummaryStatementEdge are),
    // thus they cannot appear here
    if (edge instanceof CFunctionCallEdge) {
      CFunctionCall fCall = ((CFunctionCallEdge) edge).getSummaryEdge().getExpression();
      assert (!isThreadCreateFunction(fCall));
    }
    return getSuccessors(state, edge, thread, pPrecision);
  }

  /** This method gets successors for regular states, but not applied states. */
  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessorsForEdge(AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
          throws CPATransferException, InterruptedException {

    assert (pState instanceof ThreadAbstractionState
        && !(pState instanceof ThreadAbstractionStateWithEdge));

    ThreadAbstractionState TAstate = (ThreadAbstractionState) pState;

    // CFunctionCallEdges are the only case in which we change currentThread
    if (pCfaEdge instanceof CFunctionCallEdge) { // child thread
      CFunctionCall fCall = ((CFunctionCallEdge) pCfaEdge).getSummaryEdge().getExpression();
      if (isThreadCreateFunction(fCall)) {
        String newThread = ((CThreadCreateStatement) fCall).getVariableName();
        boolean selfParallel = !isNewThread(newThread, TAstate);
        // We add FunctionSummaryStatementEdge instead of FunctionCallEdge for the compatible
        // operator
        return ImmutableSet.of(
            new ThreadAbstractionState(
                getNewTSet(
                    TAstate,
                    pCfaEdge.getPredecessor().getLeavingSummaryEdge(),
                    TAstate.getCurrentThread(),
                    newThread),
                newThread,
                selfParallel));
      }
    }

    return getSuccessors(TAstate, pCfaEdge, TAstate.getCurrentThread(), pPrecision);
  }

  private Collection<ThreadAbstractionState>
      getSuccessors(
          ThreadAbstractionState state,
          CFAEdge pCfaEdge,
          String activeThread,
          Precision pPrecision) {

    if (pCfaEdge instanceof CFunctionSummaryStatementEdge) { // parent thread
      CFunctionCall functionCall = ((CFunctionSummaryStatementEdge) pCfaEdge).getFunctionCall();
      if (isThreadCreateFunction(functionCall)) {
        String newThread = ((CThreadCreateStatement) functionCall).getVariableName();
        return ImmutableSet
            .of(state.copyWith(getNewTSet(state, pCfaEdge, activeThread, newThread)));
      }
    } else if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionReturnEdge) {
      CFunctionCall functionCall =
          ((CFunctionReturnEdge) pCfaEdge).getSummaryEdge().getExpression();
      if (isThreadCreateFunction(functionCall)) {
        return ImmutableSet.of();
      }
    }

    if (options.addProjectionsToPrecision().equals(AddProjections.EVERYTHING_IN_PRECISION)) {
      // we do not track RedundantEdges since LocationCPA does not allow to project them
      if (LocationApplyOperator.isRedundantEdge(pCfaEdge)) {
        if (state instanceof ThreadAbstractionStateWithEdge) {
          return ImmutableSet
              .of(((ThreadAbstractionStateWithEdge) state).getThreadAbstractionState());
        }
        return ImmutableSet.of(state);
      }
      return ImmutableSet.of(state.copyWith(getNewTSet(state, pCfaEdge, activeThread, null)));
    }

    ThreadAbstractionPrecision precision = (ThreadAbstractionPrecision) pPrecision;
    if (!precision.contains(pCfaEdge)) {
      if (state instanceof ThreadAbstractionStateWithEdge) {
        return ImmutableSet
            .of(((ThreadAbstractionStateWithEdge) state).getThreadAbstractionState());
      }
      return ImmutableSet.of(state);
    }
    return ImmutableSet.of(state.copyWith(getNewTSet(state, pCfaEdge, activeThread, null)));
  }

  private Map<String, CFAEdge> getNewTSet(
      ThreadAbstractionState state,
      CFAEdge pCfaEdge,
      String activeThread,
      String newThread) {
    Map<String, CFAEdge> newTset = new TreeMap<>(state.getThreadSet());
    newTset.put(activeThread, pCfaEdge);
    if (newThread != null) {
      newTset.put(newThread, ThreadAbstractionEdge.getInitialEdge());
    }
    return newTset;
  }

  private boolean isNewThread(String thread, ThreadAbstractionState state) {
    return !state.getThreadSet().containsKey(thread);
  }
}
