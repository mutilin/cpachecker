/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
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
 */
package org.sosy_lab.cpachecker.cfa.mutation.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.AExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.AVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFATraversal.TraversalProcess;
import org.sosy_lab.cpachecker.util.CFAUtils;

public class DeclarationStrategy extends SingleNodeStrategy {

  private class UsedInExpressionFinder extends CFATraversal.DefaultCFAVisitor {

    public boolean found = false;
    private final String name;

    public UsedInExpressionFinder(String pName) {
      name = pName;
    }

    @Override
    public TraversalProcess visitEdge(CFAEdge edge) {
      Collection<CExpression> exps = new ArrayList<>();
      switch (edge.getEdgeType()) {
        case BlankEdge:
          return TraversalProcess.CONTINUE;

        case AssumeEdge:
          exps.add((CExpression) ((AssumeEdge) edge).getExpression());
          break;

        case DeclarationEdge:
          final CInitializer init =
              ((CVariableDeclaration) ((CDeclarationEdge) edge).getDeclaration()).getInitializer();
          if (init == null) {
            return TraversalProcess.CONTINUE;
          }
          exps.add(((CInitializerExpression) init).getExpression());
          break;

        case StatementEdge:
          CStatement stat = ((CStatementEdge) edge).getStatement();
          if (stat instanceof AExpressionStatement) {
            exps.add(((CExpressionStatement) stat).getExpression());
          } else if (stat instanceof CExpressionAssignmentStatement) {
            exps.add(((CExpressionAssignmentStatement) stat).getLeftHandSide());
            exps.add(((CExpressionAssignmentStatement) stat).getRightHandSide());
          } else if (stat instanceof CFunctionCallStatement) {
            final CFunctionCallExpression fCall =
                ((CFunctionCallStatement) stat).getFunctionCallExpression();
            exps.add(fCall.getFunctionNameExpression());
            exps.addAll(fCall.getParameterExpressions());
          } else if (stat instanceof CFunctionCallAssignmentStatement) {
            exps.add(((CFunctionCallAssignmentStatement) stat).getLeftHandSide());
            final CFunctionCallExpression fCall =
                ((CFunctionCallAssignmentStatement) stat).getRightHandSide();
            exps.add(fCall.getFunctionNameExpression());
            exps.addAll(fCall.getParameterExpressions());
          }
          break;

        case ReturnStatementEdge:
          CExpression retval = ((CReturnStatementEdge) edge).getExpression().orNull();
          if (retval == null) {
            return TraversalProcess.CONTINUE;
          }
          exps.add(retval);
          break;

        case CallToReturnEdge:
        case FunctionCallEdge:
        case FunctionReturnEdge:
          assert false;
      }

      for (CExpression exp : exps) {
        try {
          if (CFAUtils.getVariableNamesOfExpression(exp).contains(name)) {
            found = true;
            return TraversalProcess.ABORT;
          }
        } catch (NullPointerException e) {
          logger.logfUserException(
              Level.WARNING,
              e,
              "No declaration found for a name on edge %s with expressions %s",
              edge,
              exps);
        }
      }
      return TraversalProcess.CONTINUE;
    }
  }

  public DeclarationStrategy(Configuration pConfig, LogManager pLogger)
      throws InvalidConfigurationException {
    super(pConfig, pLogger, "Declaration edges");
  }

  @Override
  protected boolean canRemove(CFANode pNode) {
    if (!super.canRemove(pNode)) {
      return false;
    }
    CFAEdge edge = pNode.getLeavingEdge(0);
    if (!(edge instanceof ADeclarationEdge)) {
      return false;
    }

    CFANode successor = edge.getSuccessor();
    AVariableDeclaration decl = (AVariableDeclaration) ((ADeclarationEdge) edge).getDeclaration();
    String qName = decl.getQualifiedName();
    boolean used = varIsUsedAfter(successor, qName);
    logger.logf(
        logDetails,
        "Variable %s is%s used after %s (in function %s)",
        qName,
        used ? "" : " not",
        edge,
        successor.getFunctionName());
    return !used;
  }

  private boolean varIsUsedAfter(CFANode pNode, String pName) {
    final UsedInExpressionFinder vis = new UsedInExpressionFinder(pName);
    CFATraversal.dfs().traverseOnce(pNode, vis);
    return vis.found;
  }
}
