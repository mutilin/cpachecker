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
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Triple;

public class SpoilerFunctionStrategy
    extends GenericCFAMutationStrategy<
        String, Triple<FunctionEntryNode, SortedSet<CFANode>, Collection<CFAEdge>>> {

  private final FunctionStrategy functionRemover;

  public SpoilerFunctionStrategy(
      Configuration pConfig, LogManager pLogger, int pRate, boolean ptryAllAtFirst)
      throws InvalidConfigurationException {
    super(pLogger, pRate, ptryAllAtFirst, "Spoiler functions");
    functionRemover = new FunctionStrategy(pConfig, pLogger, 0, false);
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
      return false;
    }
    // can remove this function only if it calls another function
    return getOnlyCallIn(parseResult, pObject) != null;
  }

  @Override
  protected void removeObject(ParseResult parseResult, String pFunctionName) {
    logger.logf(Level.INFO, "removing %s as spoiler function", pFunctionName);
    Triple<FunctionEntryNode, SortedSet<CFANode>, Collection<CFAEdge>> fullInfo =
        getRollbackInfo(parseResult, pFunctionName);
    CFAEdge innerCall = getOnlyCallIn(parseResult, pFunctionName);
    logger.logf(
        Level.INFO,
        "spoiler calls %s",
        ((AFunctionCall) ((AStatementEdge) innerCall).getStatement()).getFunctionCallExpression());

    for (CFAEdge outerCall : fullInfo.getThird()) {
      CFAEdge newEdge = dupEdge(innerCall, outerCall.getPredecessor(), outerCall.getSuccessor());
      logger.logf(Level.INFO, "replacing call %s as %s", outerCall, newEdge);
      disconnectEdge(outerCall);
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
        case BlankEdge:
        case DeclarationEdge:
          continue; // skipping blank edges
        case StatementEdge:
          if (((AStatementEdge) leavingEdge).getStatement() instanceof AFunctionCall) {
            if (found != null) { // if more than one call
              return null;
            }
            found = leavingEdge;
            continue;
          }
          return null;
        case ReturnStatementEdge:
          AExpression expr = ((AReturnStatementEdge) leavingEdge).getExpression().orNull();
          if (expr != null && expr instanceof AFunctionCallExpression) {
            if (found != null) { // if more than one call
              return null;
            }
            found = leavingEdge;
            continue;
          }
          return null;
        default:
          return null;
      }
    }
    return found;
  }

  private Collection<CFAEdge> getAllCallsTo(ParseResult parseResult, String pFunctionName) {
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
