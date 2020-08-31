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

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.FunctionCallCollector;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Triple;

public class SpoilerFunctionStrategy
    extends GenericCFAMutationStrategy<
        String, Triple<FunctionEntryNode, SortedSet<CFANode>, Collection<CFAEdge>>> {

  private final FunctionStrategy functionRemover;

  public SpoilerFunctionStrategy(Configuration pConfig, LogManager pLogger, int pStartRate)
      throws InvalidConfigurationException {
    super(pLogger, pStartRate, "Spoiler functions");
    functionRemover = new FunctionStrategy(pConfig, pLogger, 0, ImmutableSet.of("main"));
  }

  @Override
  protected Collection<String> getAllObjects(ParseResult pParseResult) {
    List<String> answer = new ArrayList<>();
    for (String name : functionRemover.getAllObjects(pParseResult)) {
      if (canRemove(pParseResult, name)) {
        answer.add(name);
      }
    }
    return answer;
  }

  protected boolean canRemove(ParseResult parseResult, String pObject) {
    // can't remove function that is not called (e.g. main)
    if (getAllCallsTo(parseResult, pObject).isEmpty()) {
      logger.logf(Level.FINE, "No calls to %s", pObject);
      return false;
    }

    // can remove this function only if it calls any function once
    // what TODO with recursion?
    CFAEdge innerCallEdge = getOnlyCallIn(parseResult, pObject);
    if (innerCallEdge == null) {
      return false;
    }

    CFunctionCall innerCall = (CFunctionCall) ((AStatementEdge) innerCallEdge).getStatement();
    logger.logf(Level.FINE, "spoilered callee is %s", innerCall.getFunctionCallExpression());
    List<CExpression> params = innerCall.getFunctionCallExpression().getParameterExpressions();
    if (!params.isEmpty()) {
      // TODO args replacing
      // Simple case: if inner call uses only constants and spoilers' args
      // as its actual parameters, subst spoilers' actual parameters where needed.
      // For now there can be no parameters.
      logger.log(Level.FINE, "Can't remove spoiler because of parameters.");
      return false;
    }
    return true;
  }

  @Override
  protected void removeObject(ParseResult parseResult, String pFunctionName) {
    logger.logf(Level.INFO, "removing %s as spoiler function", pFunctionName);
    Triple<FunctionEntryNode, SortedSet<CFANode>, Collection<CFAEdge>> fullInfo =
        getRollbackInfo(parseResult, pFunctionName);
    CFAEdge innerCallEdge = getOnlyCallIn(parseResult, pFunctionName);
    CFunctionCall innerCall = (CFunctionCall) ((AStatementEdge) innerCallEdge).getStatement();
    // remove left side if it was an assignment
    if (innerCall instanceof CFunctionCallAssignmentStatement) {
      innerCall =
          new CFunctionCallStatement(
              innerCall.getFileLocation(), innerCall.getFunctionCallExpression());
    }
    logger.logf(Level.INFO, "spoilered callee is %s", innerCall.getFunctionCallExpression());
    List<CExpression> iParams = innerCall.getFunctionCallExpression().getParameterExpressions();
    assert iParams.isEmpty() : "TODO args replacing";

    for (CFAEdge outerCallEdge : fullInfo.getThird()) {
      CFAEdge newEdge =
          new CStatementEdge(
              innerCallEdge.getRawStatement(),
              innerCall,
              innerCallEdge.getFileLocation(),
              outerCallEdge.getPredecessor(),
              outerCallEdge.getSuccessor());
      logger.logf(Level.INFO, "replacing call %s as %s", outerCallEdge, newEdge);
      disconnectEdge(outerCallEdge);
      connectEdge(newEdge);
    }
    functionRemover.removeObject(parseResult, pFunctionName);
  }

  @Override
  protected void returnObject(
      ParseResult pParseResult,
      Triple<FunctionEntryNode, SortedSet<CFANode>, Collection<CFAEdge>> pRollbackInfo) {
    Pair<FunctionEntryNode, SortedSet<CFANode>> pair =
        Pair.of(pRollbackInfo.getFirst(), pRollbackInfo.getSecond());
    functionRemover.returnObject(pParseResult, pair);
    for (CFAEdge outerCall : pRollbackInfo.getThird()) {
      CFANode predecessor = outerCall.getPredecessor();
      CFANode successor = outerCall.getSuccessor();
      CFAEdge insertedEdge = predecessor.getEdgeTo(successor);
      disconnectEdge(insertedEdge);
      connectEdge(outerCall);
    }
  }

  @Override
  protected Triple<FunctionEntryNode, SortedSet<CFANode>, Collection<CFAEdge>> getRollbackInfo(
      ParseResult parseResult, String pFunctionName) {
    Pair<FunctionEntryNode, SortedSet<CFANode>> pair =
        functionRemover.getRollbackInfo(parseResult, pFunctionName);
    return Triple.of(pair.getFirst(), pair.getSecond(), getAllCallsTo(parseResult, pFunctionName));
  }

  private CFAEdge getOnlyCallIn(ParseResult parseResult, String pObject) {
    CFAEdge found = null;
    for (CFANode node : parseResult.getCFANodes().get(pObject)) {
      if (node.getNumLeavingEdges() != 1) {
        continue; // skipping assume edges and termination nodes
      }
      CFAEdge leavingEdge = node.getLeavingEdge(0);
      switch (leavingEdge.getEdgeType()) {
        case StatementEdge:
          if (((AStatementEdge) leavingEdge).getStatement() instanceof AFunctionCall) {
            if (found != null) { // if more than one call
              logger.logf(Level.FINE, "Found another call %s", leavingEdge);
              return null;
            }
            found = leavingEdge;
            logger.logf(Level.FINE, "Found call %s", found);
          }
          continue;
        case ReturnStatementEdge:
          AExpression expr = ((AReturnStatementEdge) leavingEdge).getExpression().orNull();
          if (expr != null && expr instanceof AFunctionCallExpression) {
            if (found != null) { // if more than one call
              logger.logf(Level.FINE, "Found another call %s", leavingEdge);
              return null;
            }
            found = leavingEdge;
            logger.logf(Level.FINE, "Found call %s", found);
          }
          continue;
        default:
          continue;
      }
    }
    if (found == null) {
      logger.logf(Level.FINE, "No inner call found");
    }
    return found;
  }

  public static Collection<CFAEdge> getAllCallsTo(ParseResult parseResult, String pFunctionName) {
    Collection<CFAEdge> calls = new ArrayList<>();

    final FunctionCallCollector fCallCollector = new FunctionCallCollector();
    for (FunctionEntryNode startingNode : parseResult.getFunctions().values()) {
      CFATraversal.dfs().traverseOnce(startingNode, fCallCollector);
    }

    for (CFAEdge callEdge : fCallCollector.getFunctionCalls()) {
      AFunctionDeclaration d =
          ((AFunctionCall) ((AStatementEdge) callEdge).getStatement())
              .getFunctionCallExpression()
              .getDeclaration();

      if (d != null && d.getName().equals(pFunctionName)) {
        calls.add(callEdge);
      }
    }
    return calls;
  }
}
