/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.lockstatistics;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelationWithThread;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState.LockStatisticsStateBuilder;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;

@Options(prefix="cpa.lockStatistics")
public class LockStatisticsTransferRelation implements TransferRelationWithThread
{

  private final static String LOCK = "pthread_mutex_lock";
  private final static String UNLOCK = "pthread_mutex_unlock";

  private final LogManager logger;

  public LockStatisticsTransferRelation(Configuration config, LogManager logger) throws InvalidConfigurationException {
    config.inject(this);
    this.logger = logger;
  }

  @Override
  public Collection<LockStatisticsState> getAbstractSuccessorsForEdge(AbstractState element, Precision pPrecision
      , CFAEdge cfaEdge) throws UnrecognizedCCodeException {

    LockStatisticsState lockStatisticsElement     = (LockStatisticsState)element;

    final LockStatisticsStateBuilder builder = lockStatisticsElement.builder();
    //Firstly, determine operations with locks
    determineOperations(builder, cfaEdge);


    LockStatisticsState successor = builder.build();

    if (successor != null) {
      return Collections.singleton(successor);
    } else {
      return Collections.emptySet();
    }
  }

  private void determineOperations(LockStatisticsStateBuilder builder, CFAEdge cfaEdge) throws UnrecognizedCCodeException {
    switch (cfaEdge.getEdgeType()) {

      case FunctionCallEdge:
        handleFunctionCall(builder, (CFunctionCallEdge)cfaEdge);
        break;

      case StatementEdge:
        CStatement statement = ((CStatementEdge)cfaEdge).getStatement();
        handleStatement(builder, statement);
        break;

      default:
        break;
    }
  }


  private void handleFunctionCallExpression(LockStatisticsStateBuilder builder, CFunctionCallExpression function) {
    String functionName = function.getFunctionNameExpression().toASTString();
    if (functionName.equals(LOCK)) {
      builder.add(LockIdentifier.of(LOCK));
    } else if (functionName.equals(UNLOCK)) {
      builder.free(LockIdentifier.of(LOCK));
    }
  }

  private void handleStatement(LockStatisticsStateBuilder builder, CStatement statement) {
    if (statement instanceof CFunctionCallStatement) {
      CFunctionCallStatement funcStatement = (CFunctionCallStatement) statement;
      handleFunctionCallExpression(builder, funcStatement.getFunctionCallExpression());
    }
  }

  private void handleFunctionCall(LockStatisticsStateBuilder builder, CFunctionCallEdge callEdge) {
    handleFunctionCallExpression(builder, callEdge.getSummaryEdge().getExpression().getFunctionCallExpression());
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(AbstractState pState, Precision pPrecision)
      throws CPATransferException, InterruptedException {
    throw new UnsupportedOperationException(
        "The " + this.getClass().getSimpleName()
        + " expects to be called with a CFA edge supplied"
        + " and does not support configuration where it needs to"
        + " return abstract states for any CFA edge.");
  }

  private String formatCaption(Set<LockIdentifier> set) {

    if (set.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Change states for locks ");
    for (LockIdentifier lInfo : set) {
      sb.append(lInfo + ", ");
    }
    sb.delete(sb.length() - 2, sb.length());
    return sb.toString();
  }

  @Override
  public Collection<? extends AbstractState> performTransferInEnvironment(AbstractState pState,
      AbstractState pStateInEnv, Precision pPrecision)
      throws CPATransferException, InterruptedException {
    throw new CPATransferException("Not supported");
  }

  @Override
  public Collection<? extends AbstractState> performTransferInEnvironment(AbstractState pState,
      AbstractState pStateInEnv, CFAEdge pEdge, Precision pPrecision)
      throws CPATransferException, InterruptedException {
    return Collections.singleton(pState);
  }

  @Override
  public boolean isCompatible(AbstractState pState1, AbstractState pState2) {
    LockStatisticsState state1 = (LockStatisticsState) pState1;
    LockStatisticsState state2 = (LockStatisticsState) pState2;
    return !state1.intersects(state2);
  }
}
