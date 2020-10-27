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
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.CFATerminationNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.Pair;

public class BranchStrategy
    extends GenericCFAMutationStrategy<Pair<CFANode, Chain>, Pair<CFANode, Chain>> {

  public BranchStrategy(Configuration pConfig, LogManager pLogger)
      throws InvalidConfigurationException {
    super(pConfig, pLogger, "Branches");
  }

  @Override
  protected Collection<Pair<CFANode, Chain>> getAllObjects(ParseResult pParseResult) {
    Collection<Pair<CFANode, Chain>> answer = new ArrayList<>();

    for (CFANode node : pParseResult.getCFANodes().values()) {
      if (node.getNumLeavingEdges() != 2) {
        continue;
      }

      CFANode s0 = node.getLeavingEdge(0).getSuccessor();
      CFANode s1 = node.getLeavingEdge(1).getSuccessor();

      Chain chain0 = ChainVisitor.getChainWith(s0);
      Chain chain1 = ChainVisitor.getChainWith(s1);

      if (chain0.isEmpty() && chain1.isEmpty()) {
        continue;
      }

      if (!chain0.isEmpty() && !chain1.isEmpty()) {
        // if both branches end with the same node
        if (chain0.getSuccessor().equals(chain1.getSuccessor())) {
          answer.add(Pair.of(node, chain0));
          answer.add(Pair.of(node, chain1));
          continue;
        }
      }

      CFANode end = chain0.isEmpty() ? s0 : chain0.getSuccessor();
      if (end instanceof CFATerminationNode) {
        chain0.add(end);
        answer.add(Pair.of(node, chain0));
      } else if (end instanceof FunctionExitNode && end.getNumEnteringEdges() > 1) {
        answer.add(Pair.of(node, chain0));
      }

      end = chain1.isEmpty() ? s1 : chain1.getSuccessor();
      if (end instanceof CFATerminationNode) {
        chain1.add(end);
        answer.add(Pair.of(node, chain1));
      } else if (end instanceof FunctionExitNode && end.getNumEnteringEdges() > 1) {
        answer.add(Pair.of(node, chain1));
      }
    }

    return answer;
  }

  @Override
  protected Collection<Pair<CFANode, Chain>> getObjects(ParseResult pParseResult, int pCount) {
    Collection<Pair<CFANode, Chain>> answer = new ArrayList<>();
    Set<CFANode> starts = new HashSet<>();
    Set<CFANode> ends = new HashSet<>();
    int counter = 0;

    for (Pair<CFANode, Chain> p : getAllObjects(pParseResult)) {
      if (alreadyTried(p)) {
        continue;
      }

      if (starts.contains(p.getFirst())) {
        continue;
      }

      CFANode end = p.getSecond().get(p.getSecond().size() - 1);
      if (!(end instanceof CFATerminationNode)) {
        end = end.getLeavingEdge(0).getSuccessor();
      }
      if (ends.contains(end)) {
        continue;
      }

      starts.add(p.getFirst());
      ends.add(end);
      answer.add(p);
      if (++counter >= pCount) {
        break;
      }
    }

    return answer;
  }

  @Override
  protected Pair<CFANode, Chain> removeObject(
      ParseResult pParseResult, Pair<CFANode, Chain> pObject) {
    CFANode branchingPoint = pObject.getFirst();
    Chain pChain = pObject.getSecond();

    logger.logf(
        Level.FINE,
        "removing branching on node %s:%s with chain %s",
        branchingPoint.getFunctionName(),
        branchingPoint,
        pChain);
    CFAEdge edgeToChain = pChain.getEnteringEdge();
    logger.logf(Level.FINE, "\ttochain %s", edgeToChain);
    for (CFAEdge e : CFAUtils.enteringEdges(edgeToChain.getPredecessor())) {
      logger.logf(Level.FINE, "\t\tinb4: %s", e);
    }
    CFAEdge leavingEdge = CFAUtils.getComplimentaryAssumeEdge((AssumeEdge) edgeToChain);
    logger.logf(Level.FINE, "\tleaving %s", leavingEdge);
    CFANode successor = leavingEdge.getSuccessor();

    disconnectEdgeFromSuccessor(leavingEdge);

    CFANode lastNode = pChain.get(pChain.size() - 1);
    for (CFAEdge edgeFromChain : CFAUtils.leavingEdges(lastNode)) {
      disconnectEdgeFromSuccessor(edgeFromChain);
    }

    for (CFAEdge enteringEdge : CFAUtils.enteringEdges(branchingPoint)) {
      logger.logf(Level.FINE, "\tentering %s", enteringEdge);
      replaceEdgeByPredecessor(enteringEdge, successor);
    }

    removeNodeFromParseResult(pParseResult, branchingPoint);
    for (CFANode node : pChain) {
      removeNodeFromParseResult(pParseResult, node);
    }

    return pObject;
  }

  @Override
  protected void returnObject(ParseResult pParseResult, Pair<CFANode, Chain> pRollbackInfo) {
    CFANode branchingPoint = pRollbackInfo.getFirst();
    Chain pChain = pRollbackInfo.getSecond();

    logger.logf(Level.INFO, "returning branching on node %s with chain %s", branchingPoint, pChain);

    CFAEdge edgeToChain = pChain.getEnteringEdge();
    CFAEdge leavingEdge = CFAUtils.getComplimentaryAssumeEdge((AssumeEdge) edgeToChain);
    CFANode successor = leavingEdge.getSuccessor();

    logger.logf(Level.INFO, "\ttochain %s", edgeToChain);
    logger.logf(Level.INFO, "\tleaving %s", leavingEdge);

    for (CFAEdge enteringEdge : CFAUtils.enteringEdges(branchingPoint)) {
      logger.logf(Level.INFO, "\tentering %s", enteringEdge);
      CFANode predecessor = enteringEdge.getPredecessor();
      disconnectEdge(predecessor.getEdgeTo(successor));
      connectEdgeToNode(enteringEdge, predecessor);
    }

    connectEdgeToNode(leavingEdge, successor);

    addNodeToParseResult(pParseResult, branchingPoint);
    for (CFANode node : pChain) {
      addNodeToParseResult(pParseResult, node);
    }

    CFANode lastNode = pChain.get(pChain.size() - 1);
    for (CFAEdge edgeFromChain : CFAUtils.leavingEdges(lastNode)) {
      connectEdgeToNode(edgeFromChain, edgeFromChain.getSuccessor());
    }
  }
}
