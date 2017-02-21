/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.thread;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CThreadCreateStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelationWithThread;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackTransferRelation;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.cpa.location.LocationTransferRelation;
import org.sosy_lab.cpachecker.cpa.thread.ThreadLabel.LabelStatus;
import org.sosy_lab.cpachecker.cpa.thread.ThreadState.ThreadStateBuilder;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;


public class ThreadTransferRelation extends SingleEdgeTransferRelation implements TransferRelationWithThread {
  private final LocationTransferRelation locationTransfer;
  private final CallstackTransferRelation callstackTransfer;
  private final ThreadCPAStatistics threadStatistics;

  private static String JOIN = "pthread_join";
  private static String JOIN_SELF_PARALLEL = "ldv_thread_join_N";

  private boolean resetCallstacksFlag;

  public ThreadTransferRelation(TransferRelation l,
      TransferRelation c, Configuration pConfiguration) {
    locationTransfer = (LocationTransferRelation)l;
    callstackTransfer = (CallstackTransferRelation)c;
    threadStatistics = new ThreadCPAStatistics();
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(AbstractState pState,
      Precision pPrecision, CFAEdge pCfaEdge) throws CPATransferException, InterruptedException {

    threadStatistics.transfer.start();
    ThreadState tState = (ThreadState)pState;
    LocationState oldLocationState = tState.getLocationState();
    CallstackState oldCallstackState = tState.getCallstackState();

    ThreadStateBuilder builder = tState.getBuilder();

    //Try to join non-created thread
    boolean successfulTransition = true;
    switch (pCfaEdge.getEdgeType()) {
      case FunctionCallEdge :
        successfulTransition = handleFunctionCall((CFunctionCallEdge)pCfaEdge, builder);
        break;
      case StatementEdge :
        if (pCfaEdge instanceof CFunctionSummaryStatementEdge) {
          CFunctionCall functionCall = ((CFunctionSummaryStatementEdge)pCfaEdge).getFunctionCall();
          if (isThreadCreateFunction(functionCall)) {
            String functionName = ((CFunctionSummaryStatementEdge)pCfaEdge).getFunctionName();
            CThreadCreateStatement thrCreate = (CThreadCreateStatement) ((CFunctionSummaryStatementEdge)pCfaEdge).getFunctionCall();
            builder.addToThreadSet(new ThreadLabel(functionName, thrCreate.getVariableName(), LabelStatus.PARENT_THREAD));
            resetCallstacksFlag = true;
            callstackTransfer.enableRecursiveContext();
          }
        }
        CStatement stmnt = ((CStatementEdge)pCfaEdge).getStatement();
        if (stmnt instanceof CFunctionCallStatement){
          CFunctionCallExpression expr = ((CFunctionCallStatement)stmnt).getFunctionCallExpression();
          successfulTransition = checkForJoin(expr, builder);
        }
        break;
      case FunctionReturnEdge :
        CFunctionCall functionCall = ((CFunctionReturnEdge)pCfaEdge).getSummaryEdge().getExpression();
        successfulTransition = !isThreadCreateFunction(functionCall);
        break;
      default:
        break;
    }
    if (!successfulTransition) {
      threadStatistics.transfer.stop();
      return Collections.emptySet();
    }

    Collection<? extends AbstractState> newLocationStates = locationTransfer.getAbstractSuccessorsForEdge(oldLocationState,
        SingletonPrecision.getInstance(), pCfaEdge);
    Collection<? extends AbstractState> newCallstackStates = callstackTransfer.getAbstractSuccessorsForEdge(oldCallstackState,
        SingletonPrecision.getInstance(), pCfaEdge);


    Set<ThreadState> resultStates = new HashSet<>();
    for (AbstractState lState : newLocationStates) {
      for (AbstractState cState : newCallstackStates) {
        builder.setWrappedStates((LocationState)lState, (CallstackState)cState);
        resultStates.add(builder.build());
      }
    }
    if (resetCallstacksFlag) {
      callstackTransfer.disableRecursiveContext();
      resetCallstacksFlag = false;
    }
    threadStatistics.transfer.stop();
    return resultStates;
  }

  private boolean checkForJoin(CFunctionCallExpression pExpr, ThreadStateBuilder builder) throws CPATransferException {
    boolean success = true;
    String functionName = pExpr.getFunctionNameExpression().toASTString();
    if (functionName.equals(JOIN) || functionName.equals(JOIN_SELF_PARALLEL)) {
      threadStatistics.threadJoins.inc();
      String varName = getThreadVariableName(pExpr).getName();
      success = builder.removeFromThreadSet(new ThreadLabel(varName, LabelStatus.PARENT_THREAD));
    }
    return success;
  }

  private boolean handleFunctionCall(CFunctionCallEdge pCfaEdge,
      ThreadStateBuilder builder) throws CPATransferException {
    String functionName = pCfaEdge.getSuccessor().getFunctionName();

    boolean success = true;
    CFunctionCall fCall = pCfaEdge.getSummaryEdge().getExpression();
    if (isThreadCreateFunction(fCall)) {
      threadStatistics.threadCreates.inc();
      LabelStatus status =  ((CThreadCreateStatement)fCall).isSelfParallel() ? LabelStatus.SELF_PARALLEL_THREAD : LabelStatus.CREATED_THREAD;
      String var = ((CThreadCreateStatement)fCall).getVariableName();
      builder.addToThreadSet(new ThreadLabel(functionName, var, status));
      //Just to statistics
      ThreadState tmpState = builder.build();
      threadStatistics.maxNumberOfThreads.setNextValue(tmpState.getThreadSet().size());
    } else {
      success = checkForJoin(fCall.getFunctionCallExpression(), builder);
    }
    return success;
  }

  private boolean isThreadCreateFunction(CFunctionCall statement) {
    return (statement instanceof CThreadCreateStatement);
    //return functionName.equals(CREATE) || functionName.equals(CREATE_SELF_PARALLEL);
  }

  private CIdExpression getThreadVariableName(CFunctionCallExpression fCall) throws CPATransferException {
    CExpression var = fCall.getParameterExpressions().get(0);

    while (!(var instanceof CIdExpression)) {
      if (var instanceof CUnaryExpression) {
        //&t
        var = ((CUnaryExpression)var).getOperand();
      } else {
        throw new CPATransferException("Unsupported parameter expression " + var);
      }
    }
    return (CIdExpression) var;
  }

  public Statistics getStatistics() {
    return threadStatistics;
  }

  @Override
  public Collection<? extends AbstractState> performTransferInEnvironment(AbstractState pState,
      AbstractState pStateInEnv, Precision pPrecision)
      throws CPATransferException, InterruptedException {
    throw new CPATransferException("Not yet supported");
  }

  @Override
  public Collection<? extends AbstractState> performTransferInEnvironment(AbstractState pState,
      AbstractState pStateInEnv, CFAEdge pEdge, Precision pPrecision)
      throws CPATransferException, InterruptedException {
    /*if (pEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
      if (isThreadCreateFunction(((CFunctionCallEdge)pEdge).getSummaryEdge().getExpression())) {
        //skip it
        return Collections.singleton(pState);
      }
    }
    ThreadState state = (ThreadState) pState;
    ThreadStateBuilder builder = state.getBuilder();
    LocationState newLoc = locationTransfer. */
    return Collections.singleton(pState);
  }

  @Override
  public boolean isCompatible(AbstractState pState1, AbstractState pState2) {
    ThreadState state1 = (ThreadState) pState1;
    ThreadState state2 = (ThreadState) pState2;
    threadStatistics.compatibility.start();
    try {
      threadStatistics.threadComp.start();
      boolean r = state1.isCompatibleWith(state2);
      threadStatistics.threadComp.stop();
      if (!r) {
        threadStatistics.uncompatibleStates.inc();
        return false;
      }
      if (!locationTransfer.isCompatible(state1.getLocationState(), state2.getLocationState())) {
        threadStatistics.uncompatibleStates.inc();
        return false;
      }
      if (!callstackTransfer.isCompatible(state1.getCallstackState(), state2.getCallstackState())) {
        threadStatistics.uncompatibleStates.inc();
        return false;
      }
      threadStatistics.compatibleStates.inc();
      return true;
    } finally {
      threadStatistics.compatibility.stop();
    }
  }
}
