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
import java.util.logging.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
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
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.IdentifierCreator;

//TODO is it possible to use ForwardingTransferRelation?
@Options(prefix = "cpa.alias")
public class AliasTransfer extends SingleEdgeTransferRelation {

  @Option(secure = true, name = "rcu_assign", description = "Name of a function responsible for "
      + "assignments to RCU pointers")
  private String assign = "rcu_assign_pointer";

  @Option(secure = true, name = "rcu_deref", description = "Name of a function responsible for "
      + "dereferences of RCU pointers")
  private String deref = "rcu_dereference";

  @Option(secure = true, name = "flowSense", description = "enable analysis flow sensitivity")
  private boolean flowSense = false;

  private final LogManager log;

  public AliasTransfer(Configuration config, LogManager log) throws InvalidConfigurationException {
    config.inject(this);
    this.log = log;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState state, Precision precision, CFAEdge cfaEdge)
      throws CPATransferException, InterruptedException {

    IdentifierCreator ic = new IdentifierCreator();
    AliasState result = (AliasState) state;

    switch (cfaEdge.getEdgeType()) {
      case DeclarationEdge:
        CDeclaration cdecl = ((CDeclarationEdge) cfaEdge).getDeclaration();
        handleDeclaration(result, cdecl, ic);
        break;
      case StatementEdge:
        CStatement st = ((CStatementEdge) cfaEdge).getStatement();
        handleStatement(result, st, ic);
        break;
      case FunctionCallEdge:
        CFunctionCallExpression fce = ((CFunctionCallEdge) cfaEdge).getSummaryEdge()
            .getExpression().getFunctionCallExpression();
        handleFunctionCall(result, fce, ic);
        break;
      case AssumeEdge:
      case CallToReturnEdge:
      case FunctionReturnEdge:
      case ReturnStatementEdge:
      case BlankEdge:
        break;
      default:
        throw new UnrecognizedCCodeException("Unrecognized CFA edge.", cfaEdge);
    }

    return Collections.singleton(result);
  }

  private void handleStatement(AliasState pResult, CStatement pSt, IdentifierCreator ic) {
    if (pSt instanceof CExpressionAssignmentStatement) {
      CExpressionAssignmentStatement as = (CExpressionAssignmentStatement) pSt;
      AbstractIdentifier ail = as.getLeftHandSide().accept(ic);
      if (ail.isPointer()) {
        ic.clearDereference();
        AbstractIdentifier air = as.getRightHandSide().accept(ic);
        if (!pResult.getAlias().containsKey(ail)) {
          pResult.getAlias().put(ail, new HashSet<>());
        }
        if (flowSense) {
          pResult.getAlias().get(ail).clear();
        }
        pResult.getAlias().get(ail).add(air);
      }
    } else if (pSt instanceof CFunctionCallAssignmentStatement) {
      CFunctionCallAssignmentStatement fca = (CFunctionCallAssignmentStatement) pSt;
      AbstractIdentifier ail = fca.getLeftHandSide().accept(ic);
      if (ail.isPointer()) {
        handleFunctionCall(pResult, fca.getRightHandSide(), ic);
        if (!pResult.getAlias().containsKey(ail)) {
          pResult.getAlias().put(ail, new HashSet<>());
        }
        ic.clearDereference();
        AbstractIdentifier fi =
            fca.getFunctionCallExpression().getFunctionNameExpression().accept(ic);
        if (flowSense) {
          pResult.getAlias().get(ail).clear();
        }
        pResult.getAlias().get(ail).add(fi);
        // p = rcu_dereference(gp);
        if (fca.getRightHandSide().getDeclaration().getName().contains(deref)) {
          addToRCU(pResult, ail);
        }
      }
    }
  }

  private void addToRCU(AliasState pResult, AbstractIdentifier pId) {
    //TODO Does it work? Seems, this is an infinite recursion loop.
    Set<AbstractIdentifier> old = pResult.getPrcu();
    pResult.getPrcu().add(pId);
    if (!pResult.getPrcu().equals(old)) {
      Set<AbstractIdentifier> alias = pResult.getAlias().get(pId);
      for (AbstractIdentifier ai : alias) {
        addToRCU(pResult, ai);
      }
    }
  }


  private void handleFunctionCall(AliasState pResult, CFunctionCallExpression pRhs,
                                  IdentifierCreator ic) {
    CFunctionDeclaration fd = pRhs.getDeclaration();
    List<CParameterDeclaration> formParams = fd.getParameters();
    List<CExpression> factParams = pRhs.getParameterExpressions();

    assert formParams.size() == factParams.size();

    AbstractIdentifier form;
    AbstractIdentifier fact;

    ic.clear(fd.getName());

    for (int i = 0; i < formParams.size(); ++i) {
      ic.clearDereference();
      form = handleDeclaration(pResult, formParams.get(i).asVariableDeclaration(), ic);
      if (form != null && form.isPointer()) {
        ic.clearDereference();
        fact = factParams.get(i).accept(ic);
        if (flowSense) {
          pResult.getAlias().get(form).clear();
        }
        pResult.getAlias().get(form).add(fact);
        // rcu_assign_pointer(gp, p); || rcu_dereference(gp);
        if (fd.getName().contains(assign) || fd.getName().contains(deref)) {
          addToRCU(pResult, fact);
        }
      }
    }
  }

  private AbstractIdentifier handleDeclaration(AliasState pResult, CDeclaration pCdecl,
                                               IdentifierCreator ic) {
    if (pCdecl instanceof CVariableDeclaration) {
      CVariableDeclaration var = (CVariableDeclaration) pCdecl;
      String functionName = AbstractStates.extractLocation(pResult).getFunctionName();
      AbstractIdentifier ail = IdentifierCreator.createIdentifier(var, functionName, 0);
      if (ail.isPointer()) {
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
    }
    return null;
  }
}
