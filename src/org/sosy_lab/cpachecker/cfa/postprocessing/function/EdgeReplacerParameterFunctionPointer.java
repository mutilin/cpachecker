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

import java.util.ArrayList;
import java.util.List;
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
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CFunctionTypeWithNames;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;

@Options
public class EdgeReplacerParameterFunctionPointer extends EdgeReplacer {
  public EdgeReplacerParameterFunctionPointer(MutableCFA pCfa, Configuration config, LogManager pLogger) throws InvalidConfigurationException {
    super(pCfa, config, pLogger);
  }

  private CFunctionCall createRegularCallWithParameter(CFunctionCall functionCall, FunctionEntryNode fNode, CExpression oldParam, CIdExpression newParam) {
    CFunctionCallExpression oldCallExpr = functionCall.getFunctionCallExpression();
    List<CExpression> params = new ArrayList<>();
    for (CExpression param : oldCallExpr.getParameterExpressions()) {
      if (param == oldParam) {
        params.add(newParam);
      } else {
        params.add(param);
      }
    }

    CFunctionCallExpression newCallExpr = new CFunctionCallExpression(oldCallExpr.getFileLocation(), oldCallExpr.getExpressionType(),
        oldCallExpr.getFunctionNameExpression(),
        params, (CFunctionDeclaration)fNode.getFunctionDefinition());

    if (functionCall instanceof CFunctionCallAssignmentStatement) {
      CFunctionCallAssignmentStatement asgn = (CFunctionCallAssignmentStatement)functionCall;
      return new CFunctionCallAssignmentStatement(functionCall.getFileLocation(),
          asgn.getLeftHandSide(), newCallExpr);
    } else if (functionCall instanceof CFunctionCallStatement) {
      return new CFunctionCallStatement(functionCall.getFileLocation(), newCallExpr);
    }
    return new CFunctionCallStatement(functionCall.getFileLocation(), newCallExpr);
  }

  private CExpression functionArgumentPointerCall(CFunctionCall call)
  {
    for (CExpression param : call.getFunctionCallExpression().getParameterExpressions()) {
      if (param.getExpressionType() instanceof CPointerType
          && ((CPointerType) param.getExpressionType()).getType() instanceof CFunctionTypeWithNames
          && ((param instanceof CIdExpression && ((CIdExpression) param).getDeclaration().getType() instanceof CPointerType)
          || (param instanceof CFieldReference))) {
        return param;
      }
    }
    return null;
  }

  @Override
  protected void createEdge(CStatementEdge statement, CFunctionCall functionCall, MutableCFA cfa, LogManager logger,
      CExpression nameExp, CUnaryExpression amper, FunctionEntryNode fNode, CFANode rootNode, CFANode thenNode,
      CFANode elseNode, CFANode retNode, FileLocation fileLocation, CIdExpression func)
  {
    CExpression param = functionArgumentPointerCall(functionCall);
    final CBinaryExpressionBuilder binExprBuilder = new CBinaryExpressionBuilder(cfa.getMachineModel(), logger);
    CBinaryExpression condition = binExprBuilder.buildBinaryExpressionUnchecked(param, amper, BinaryOperator.EQUALS);
    addConditionEdges(condition, rootNode, thenNode, elseNode, fileLocation);

    String pRawStatement = "pointer call(" + nameExp + ") " + statement.getRawStatement();
    CFunctionCall regularCall = createRegularCallWithParameter(functionCall, fNode, param, func);
    createCallEdge(fileLocation, pRawStatement, thenNode, retNode, regularCall);
  }
}
