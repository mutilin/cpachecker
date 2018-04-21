/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa.postprocessing.function;

import java.util.Collection;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFACreationUtils;
import org.sosy_lab.cpachecker.cfa.MutableCFA;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpressionBuilder;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.model.AbstractCFAEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.util.CFAUtils;

@Options
public abstract class EdgeReplacer {

  @Option(
    secure = true,
    name = "analysis.functionPointerEdgesForUnknownPointer",
    description = "Create edge for skipping a function pointer call if its value is unknown."
  )
  protected boolean createUndefinedFunctionCall = true;

  private final MutableCFA cfa;
  private final LogManager logger;

  public EdgeReplacer(MutableCFA pCfa, Configuration config, LogManager pLogger)
      throws InvalidConfigurationException {
    cfa = pCfa;
    logger = pLogger;
    config.inject(this);
  }

  /** @category helper */
  private CFANode newCFANode(final String functionName) {
    assert cfa != null;
    CFANode nextNode = new CFANode(functionName);
    cfa.addNode(nextNode);
    return nextNode;
  }

  /**
   * This method adds 2 edges to the cfa: 1. trueEdge from rootNode to thenNode and 2. falseEdge
   * from rootNode to elseNode.
   *
   * @category conditions
   */
  private void addConditionEdges(
      CExpression nameExp,
      CUnaryExpression amper,
      CFANode rootNode,
      CFANode thenNode,
      CFANode elseNode,
      FileLocation fileLocation) {

    final CBinaryExpressionBuilder binExprBuilder =
        new CBinaryExpressionBuilder(cfa.getMachineModel(), logger);
    CBinaryExpression condition =
        binExprBuilder.buildBinaryExpressionUnchecked(nameExp, amper, BinaryOperator.EQUALS);

    // edge connecting condition with thenNode
    final CAssumeEdge trueEdge =
        new CAssumeEdge(condition.toASTString(), fileLocation, rootNode, thenNode, condition, true);
    CFACreationUtils.addEdgeToCFA(trueEdge, logger);

    // edge connecting condition with elseNode
    final CAssumeEdge falseEdge =
        new CAssumeEdge(
            "!(" + condition.toASTString() + ")",
            fileLocation,
            rootNode,
            elseNode,
            condition,
            false);
    CFACreationUtils.addEdgeToCFA(falseEdge, logger);
  }

  private CFunctionCall createRegularCall(
      CFunctionCall functionCall, CFunctionCallExpression newCallExpr) {
    if (functionCall instanceof CFunctionCallAssignmentStatement) {
      CFunctionCallAssignmentStatement asgn = (CFunctionCallAssignmentStatement) functionCall;
      return new CFunctionCallAssignmentStatement(
          functionCall.getFileLocation(), asgn.getLeftHandSide(), newCallExpr);
    } else if (functionCall instanceof CFunctionCallStatement) {
      return new CFunctionCallStatement(functionCall.getFileLocation(), newCallExpr);
    } else {
      throw new AssertionError("Unknown CFunctionCall subclass.");
    }
  }

  @SuppressWarnings("unused")
  protected boolean shouldBeInstrumented(CFunctionCall functionCall) {
    return true;
  }

  protected abstract CFunctionCallExpression createNewCallExpression(
      CFunctionCallExpression oldCallExpr,
      CExpression nameExp,
      FunctionEntryNode fNode,
      CIdExpression func);

  protected abstract AbstractCFAEdge createSummaryEdge(
      CStatementEdge statement, CFANode rootNode, CFANode end);

  public void instrument(
      CStatementEdge statement, Collection<CFunctionEntryNode> funcs, CExpression nameExp) {
    CFunctionCall functionCall = (CFunctionCall) statement.getStatement();

    if (funcs.isEmpty() || !shouldBeInstrumented(functionCall)) {
      return;
    }

    CFunctionCallExpression oldCallExpr = functionCall.getFunctionCallExpression();
    FileLocation fileLocation = statement.getFileLocation();
    CFANode start = statement.getPredecessor();
    CFANode end = statement.getSuccessor();

    CFACreationUtils.removeEdgeFromNodes(statement);

    CFANode rootNode = start;
    for (FunctionEntryNode fNode : funcs) {
      CFANode thenNode = newCFANode(start.getFunctionName());
      CFANode elseNode = newCFANode(start.getFunctionName());

      CIdExpression func =
          new CIdExpression(
              nameExp.getFileLocation(),
              (CType) fNode.getFunctionDefinition().getType(),
              fNode.getFunctionName(),
              (CSimpleDeclaration) fNode.getFunctionDefinition());
      CUnaryExpression amper =
          new CUnaryExpression(
              nameExp.getFileLocation(),
              func.getExpressionType(),
              func,
              CUnaryExpression.UnaryOperator.AMPER);
      CFANode retNode = newCFANode(start.getFunctionName());

      addConditionEdges(nameExp, amper, rootNode, thenNode, elseNode, fileLocation);

      String pRawStatement =
          "pointer call(" + fNode.getFunctionName() + ") " + statement.getRawStatement();
      CFunctionCallExpression newCallExpr =
          createNewCallExpression(oldCallExpr, nameExp, fNode, func);
      CFunctionCall regularCall = createRegularCall(functionCall, newCallExpr);

      CStatementEdge callEdge =
          new CStatementEdge(pRawStatement, regularCall, fileLocation, thenNode, retNode);
      CFACreationUtils.addEdgeUnconditionallyToCFA(callEdge);

      BlankEdge be = new BlankEdge("skip " + statement, fileLocation, retNode, end, "skip");
      CFACreationUtils.addEdgeUnconditionallyToCFA(be);

      rootNode = elseNode;
    }

    if (createUndefinedFunctionCall) {
      CFAEdge ae = createSummaryEdge(statement, rootNode, end);
      rootNode.addLeavingEdge(ae);
      end.addEnteringEdge(ae);
    } else {
      for (CFAEdge edge : CFAUtils.enteringEdges(rootNode)) {
        CFACreationUtils.removeEdgeFromNodes(edge);
      }
      cfa.removeNode(rootNode);
    }
  }
}
