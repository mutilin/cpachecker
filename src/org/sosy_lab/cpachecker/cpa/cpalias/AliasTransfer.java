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
package org.sosy_lab.cpachecker.cpa.cpalias;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.IdentifierCreator;

public class AliasTransfer extends SingleEdgeTransferRelation {
  private static final String assign = "rcu_assign_pointer";
  private static final String deref = "rcu_dereference";

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState state, Precision precision, CFAEdge cfaEdge)
      throws CPATransferException, InterruptedException {

    AliasState result = (AliasState) state;

    switch (cfaEdge.getEdgeType()) {
      case DeclarationEdge:
        CDeclaration cdecl = ((CDeclarationEdge) cfaEdge).getDeclaration();
        handleDeclaration(result, cdecl);
        break;
      case StatementEdge:
        CStatement st = ((CStatementEdge) cfaEdge).getStatement();
        handleStatement(result, st);
        break;
      case FunctionCallEdge:
        CFunctionCallExpression fce = ((CFunctionCallEdge) cfaEdge).getSummaryEdge()
            .getExpression().getFunctionCallExpression();
        handleFunctionCall(result, fce);
        break;
    }

    return Collections.singleton(result);
  }

  private void handleStatement(AliasState pResult, CStatement pSt) {
    if (pSt instanceof CExpressionAssignmentStatement) {
      IdentifierCreator ic = new IdentifierCreator();

      CExpressionAssignmentStatement as = (CExpressionAssignmentStatement) pSt;
      AbstractIdentifier ail = as.getLeftHandSide().accept(ic);
      if (ail.isPointer()) {
        ic.clearDereference();
        AbstractIdentifier air = as.getRightHandSide().accept(ic);
        if (!pResult.getAlias().containsKey(ail)) {
          pResult.getAlias().put(ail, new HashSet<>());
        }
        pResult.getAlias().get(ail).add(air);
      }
    } else if (pSt instanceof CFunctionCallAssignmentStatement) {
      IdentifierCreator ic = new IdentifierCreator();

      CFunctionCallAssignmentStatement fca = (CFunctionCallAssignmentStatement) pSt;
      AbstractIdentifier ail = fca.getLeftHandSide().accept(ic);
      handleFunctionCall(pResult, fca.getRightHandSide());
      if (!pResult.getAlias().containsKey(ail)) {
        pResult.getAlias().put(ail, new HashSet<>());
      }
      ic.clearDereference();
      AbstractIdentifier fi = fca.getFunctionCallExpression().getFunctionNameExpression().accept(ic);
      pResult.getAlias().get(ail).add(fi);
      // p = rcu_dereference(gp);
      if (fca.getRightHandSide().getDeclaration().getName().equals(deref)) {
        addToRCU(pResult, ail);
      }
    }
  }

  private void addToRCU(AliasState pResult, AbstractIdentifier pId) {
    Set<AbstractIdentifier> alias = pResult.getAlias().get(pId);
    pResult.getPrcu().add(pId);
    for (AbstractIdentifier ai : alias) {
      addToRCU(pResult, ai);
    }
  }


  private void handleFunctionCall(AliasState pResult, CFunctionCallExpression pRhs) {
    IdentifierCreator ic = new IdentifierCreator();

    CFunctionDeclaration fd = pRhs.getDeclaration();
    List<CParameterDeclaration> formParams = fd.getParameters();
    List<CExpression> factParams = pRhs.getParameterExpressions();

    assert formParams.size() == factParams.size();

    AbstractIdentifier form;
    AbstractIdentifier fact;

    ic.clear(fd.getName());

    for (int i = 0; i < formParams.size(); ++i) {
      ic.clearDereference();
      form = handleDeclaration(pResult, formParams.get(i).asVariableDeclaration());
      ic.clearDereference();
      fact = factParams.get(i).accept(ic);
      pResult.getAlias().get(form).add(fact);
      // rcu_assign_pointer(gp, p); || rcu_dereference(gp);
      if (fd.getName().equals(assign) || fd.getName().equals(deref)) {
        addToRCU(pResult, fact);
      }
    }
  }

  private AbstractIdentifier handleDeclaration(AliasState pResult, CDeclaration pCdecl) {
    if (pCdecl instanceof CVariableDeclaration) {
      IdentifierCreator ic = new IdentifierCreator();

      CVariableDeclaration var = (CVariableDeclaration) pCdecl;
      //TODO: replace with smth adequate
      String functionName = AbstractStates.extractStateByType(pResult, CallstackState.class)
          .getCurrentFunction();
      AbstractIdentifier ail = ic.createIdentifier(var, functionName, 0);
      CInitializer init = var.getInitializer();
      AbstractIdentifier air;
      if (init != null) {
        if (init instanceof CInitializerExpression) {
          air = ((CInitializerExpression) init).getExpression().accept(ic);
          if (!pResult.getAlias().containsKey(ail)) {
            pResult.getAlias().put(ail, new HashSet<>());
          }
          pResult.getAlias().get(ail).add(air);
        }
      } else {
        if (!pResult.getAlias().containsKey(ail)) {
          pResult.getAlias().put(ail, new HashSet<>());
        }
      }
      return ail;
    }
    return null;
  }
}
