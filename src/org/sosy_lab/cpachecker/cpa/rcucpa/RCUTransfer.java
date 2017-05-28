/*
 * CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.rcucpa;

import java.util.Collection;
import java.util.Collections;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.IdentifierCreator;

@Options(prefix = "cpa.rcucpa")
public class RCUTransfer extends SingleEdgeTransferRelation{

  private final String readLockName = "rcu_read_lock";
  private final String readUnlockName = "rcu_read_unlock";
  private final String sync = "synchronize_rcu";

  @Option(name = "assign", secure = true, description = "Name of a function responsible for "
      + "assignment to RCU pointers")
  private String assign = "rcu_assign_pointer";

  @Option(secure = true, name = "deref", description = "Name of a function responsible for "
      + "dereferences of RCU pointers")
  private String deref = "rcu_dereference";

  @Option(name = "fictReadLock", secure = true, description = "Name of a function marking a call "
      + "to a fictional read lock of RCU pointer")
  private String fictReadLock = "rlock_rcu";

  @Option(name = "fictReadUnlock", secure = true, description = "Name of a function marking a call "
      + "to a fictional read unlock of RCU pointer")
  private String fictReadUnlock = "runlock_rcu";

  @Option(name = "fictWriteLock", secure = true, description = "Name of a function marking a call "
      + "to a fictional write lock of RCU pointer")
  private String fictWriteLock = "wlock_rcu";

  @Option(name = "fictWriteUnlock", secure = true, description = "Name of a function marking a "
      + "call to a fictional write unlock of RCU pointer")
  private String fictWriteUnlock = "wunlock_rcu";

  private final LogManager logger;

  public RCUTransfer(Configuration pConfig, LogManager pLogger) throws InvalidConfigurationException {
    logger = pLogger;
    pConfig.inject(this);
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState state, Precision precision, CFAEdge cfaEdge)
      throws CPATransferException, InterruptedException {
    RCUState result = (RCUState) state;
    IdentifierCreator ic = new IdentifierCreator();

    switch (cfaEdge.getEdgeType()) {
      case DeclarationEdge:
        handleDeclaration(((CDeclarationEdge) cfaEdge).getDeclaration(), result, precision, ic,
            cfaEdge.getPredecessor().getFunctionName());
        break;
      case StatementEdge:
        CStatement statement = ((CStatementEdge) cfaEdge).getStatement();
        if (statement instanceof CExpressionAssignmentStatement) {
          handleAssignment((CExpressionAssignmentStatement) statement, result, precision, ic,
              cfaEdge.getPredecessor().getFunctionName());
        } else if (statement instanceof CFunctionCallAssignmentStatement) {
          handleFunctionCallAssignment((CFunctionCallAssignmentStatement) statement, result, ic,
              cfaEdge.getPredecessor().getFunctionName());
        } else {
          break;
        }
        break;
      case FunctionCallEdge:
        CFunctionCallExpression callExpression =
            ((CFunctionCallEdge) cfaEdge).getSummaryEdge().getExpression()
                .getFunctionCallExpression();
        handleFunctionCall(callExpression, result, precision, ic,
            cfaEdge.getPredecessor().getFunctionName());
        break;
      case ReturnStatementEdge:
        break;
      case FunctionReturnEdge:
      case CallToReturnEdge:
      case AssumeEdge:
      case BlankEdge:
        break;
      default:
        throw new UnrecognizedCFAEdgeException(cfaEdge);
    }

    return Collections.singleton(result);
  }

  private void handleFunctionCall(CFunctionCallExpression pCallExpression, RCUState pResult,
                                  Precision pPrecision, IdentifierCreator pIc,
                                  String pFunctionName) {
    CFunctionDeclaration fd = pCallExpression.getDeclaration();

    if (fd != null) {
      String fName = fd.getName();

      if (fName.equals(readLockName)) {
        pResult.getLockState().incRCURead();
      } else if (fName.equals(readUnlockName)) {
        pResult.getLockState().decRCURead();
      } else if (fName.equals(fictReadLock)) {
        pResult.getLockState().markRead();
      } else if (fName.equals(fictWriteLock)) {
        pResult.getLockState().markWrite();
      } else if (fName.equals(fictReadUnlock) || fName.equals(fictWriteUnlock)) {
        pResult.getLockState().clearLock();
      } else if (fName.equals(sync)) {
        pResult.fillLocal();
      } else if (fName.equals(assign)) {
        pIc.clear(pFunctionName);
        AbstractIdentifier rcuPtr = pCallExpression.getParameterExpressions().get(0).accept(pIc);
        pResult.addToOutdated(rcuPtr);
      }
    }
  }

  private void handleFunctionCallAssignment(CFunctionCallAssignmentStatement assignment,
                                            RCUState pResult, IdentifierCreator pIc,
                                            String functionName) {
    // This case is covered by the normal assignment expression
    /*
    CFunctionDeclaration functionDeclaration = assignment.getFunctionCallExpression().getDeclaration();
    if (functionDeclaration != null && functionDeclaration.getName().equals(deref)) {
      pIc.clear(functionName);
      AbstractIdentifier ail = assignment.getLeftHandSide().accept(pIc);
      pIc.clearDereference();
      AbstractIdentifier air = assignment.getFunctionCallExpression()
                                .getParameterExpressions().get(0).accept(pIc);
      pResult.addToRelations(ail, air);
    }
    */
  }

  private void handleAssignment(CExpressionAssignmentStatement assignment,
                                RCUState pResult, Precision pPrecision,
                                IdentifierCreator pIc, String functionName) {
    RCUPrecision precision = (RCUPrecision) pPrecision;
    pIc.clear(functionName);
    AbstractIdentifier ail = assignment.getLeftHandSide().accept(pIc);
    pIc.clearDereference();
    AbstractIdentifier air = assignment.getRightHandSide().accept(pIc);

    if (precision.getRcuPtrs().contains(ail) || precision.getRcuPtrs().contains(air)) {
      pResult.addToRelations(ail, air);
    }
  }

  private void handleDeclaration(CDeclaration pDeclaration, RCUState pResult, Precision pPrecision,
                                 IdentifierCreator pIc, String pFunctionName) {
    if (pDeclaration != null && pDeclaration instanceof CVariableDeclaration) {
      CVariableDeclaration var = (CVariableDeclaration) pDeclaration;
      AbstractIdentifier ail = IdentifierCreator.createIdentifier(var, pFunctionName, 0);
      RCUPrecision precision = (RCUPrecision) pPrecision;
      if (precision.getRcuPtrs().contains(ail)) {
        CInitializer initializer = ((CVariableDeclaration) pDeclaration).getInitializer();
        if (initializer != null && initializer instanceof CInitializerExpression) {
          pIc.clearDereference();
          AbstractIdentifier init = ((CInitializerExpression) initializer).getExpression().accept(pIc);
          pResult.addToRelations(ail, init);
        }
        pResult.addToRelations(ail, null);
      }
    }
  }


}
