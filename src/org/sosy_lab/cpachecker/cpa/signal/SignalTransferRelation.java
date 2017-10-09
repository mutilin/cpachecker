/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.signal;

import java.util.Collection;
import java.util.Collections;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;

public class SignalTransferRelation extends SingleEdgeTransferRelation {

  private final static String RECEIVE ="receive";
  private final static String SEND = "send";

  public SignalTransferRelation(Configuration pConfig, LogManager pLogger) {
    // TODO Auto-generated constructor stub
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(AbstractState pState,
      Precision pPrecision, CFAEdge pCfaEdge) throws CPATransferException, InterruptedException {

    SignalState oldState = (SignalState) pState;

    switch (pCfaEdge.getEdgeType()) {

      case FunctionCallEdge:
        return handleFunctionCall(oldState, (CFunctionCallEdge)pCfaEdge);

      case FunctionReturnEdge:
        return handleFunctionReturnEdge(oldState, (CFunctionReturnEdge)pCfaEdge);

      case StatementEdge:
        CStatement statement = ((CStatementEdge)pCfaEdge).getStatement();
        return handleStatement(oldState, statement);

      case AssumeEdge:
      case BlankEdge:
      case ReturnStatementEdge:
      case DeclarationEdge:
      case CallToReturnEdge:
        break;

      default:
        throw new UnrecognizedCCodeException("Unknown edge type", pCfaEdge);
    }
    return Collections.singleton(pState);
  }

  private Collection<? extends AbstractState> handleStatement(SignalState pState, CStatement pStatement) {
    // TODO Auto-generated method stub
    return null;
  }

  private Collection<? extends AbstractState> handleFunctionReturnEdge(SignalState pState,
      CFunctionReturnEdge pCfaEdge) {
    // TODO Check
    return null;
  }

  private Collection<? extends AbstractState> handleFunctionCall(SignalState pState, CFunctionCallEdge pCfaEdge) {
    String functionName = pCfaEdge.getSuccessor().getFunctionName();

    if (functionName.equals(RECEIVE)) {
      return Collections.singleton(pState.receiveSignal());
    } else if (functionName.equals(SEND)) {
      return Collections.singleton(pState.sendSignal());
    }
    return Collections.singleton(pState);
  }

}
