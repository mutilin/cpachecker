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

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.MutableCFA;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpressionBuilder;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;

@Options
public class EdgeReplacerFunctionPointer extends EdgeReplacer {
  public EdgeReplacerFunctionPointer(MutableCFA pCfa, Configuration config, LogManager pLogger) throws InvalidConfigurationException {
    super(pCfa, config, pLogger);
  }

  private CIdExpression createIdExpression(CExpression nameExp,
      FunctionEntryNode fNode) {
    return new CIdExpression(nameExp.getFileLocation(),
        nameExp.getExpressionType(),
        fNode.getFunctionName(),
        (CSimpleDeclaration)fNode.getFunctionDefinition());
  }

  private CFunctionCall createRegularCall(CFunctionCall functionCall, FunctionEntryNode fNode) {
    CFunctionCallExpression oldCallExpr = functionCall.getFunctionCallExpression();
    CFunctionCallExpression newCallExpr = new CFunctionCallExpression(oldCallExpr.getFileLocation(), oldCallExpr.getExpressionType(),
        createIdExpression(oldCallExpr.getFunctionNameExpression(), fNode),
        oldCallExpr.getParameterExpressions(), (CFunctionDeclaration)fNode.getFunctionDefinition());

    return createRegularCallCommon(functionCall, newCallExpr);
  }

  @Override
  protected void createEdge(CStatementEdge statement, CFunctionCall functionCall,
      CExpression nameExp, CUnaryExpression amper, FunctionEntryNode fNode, CFANode rootNode, CFANode thenNode,
      CFANode elseNode, CFANode retNode, FileLocation fileLocation, CIdExpression func, CBinaryExpressionBuilder binExprBuilder, CExpression param) {
    CBinaryExpression condition = binExprBuilder.buildBinaryExpressionUnchecked(nameExp, amper, BinaryOperator.EQUALS);
    addConditionEdges(condition, rootNode, thenNode, elseNode, fileLocation);
    String pRawStatement = "pointer call(" + fNode.getFunctionName() + ") " + statement.getRawStatement();
    CFunctionCall regularCall = createRegularCall(functionCall, fNode);
    createCallEdge(fileLocation, pRawStatement, thenNode, retNode, regularCall);
  }
}
