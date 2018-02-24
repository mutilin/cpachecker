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
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;

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
        params, oldCallExpr.getDeclaration());

    return createRegularCallCommon(functionCall, newCallExpr);
  }

  @Override
  protected void createEdge(CFunctionCall functionCall, CExpression nameExp, FunctionEntryNode fNode, CFANode thenNode,
      CFANode retNode, FileLocation fileLocation, CIdExpression func, String pRawStatement) {
    CFunctionCallExpression fExp = functionCall.getFunctionCallExpression();
    CFunctionCall regularCall = createRegularCallWithParameter(functionCall, fNode, nameExp, func);
    createCallEdge(fileLocation, pRawStatement, thenNode, retNode, regularCall);
  }
}
