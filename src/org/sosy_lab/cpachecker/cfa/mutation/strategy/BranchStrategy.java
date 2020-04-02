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

  public BranchStrategy(LogManager pLogger, int pRate, boolean pPtryAllAtFirst) {
    super(pLogger, pRate, pPtryAllAtFirst, "Branches");
  }

  @Override
  protected Collection<Pair<CFANode, Chain>> getAllObjects(ParseResult pParseResult) {
    Collection<Pair<CFANode, Chain>> answer = new ArrayList<>();

    final ChainStrategy cs = new ChainStrategy(logger, 0, false);

    for (CFANode node : pParseResult.getCFANodes().values()) {
      if (node.getNumLeavingEdges() != 2) {
        continue;
      }

      CFANode s0 = node.getLeavingEdge(0).getSuccessor();
      CFANode s1 = node.getLeavingEdge(1).getSuccessor();

      Chain chain0 = new Chain();
      if (s0.getNumEnteringEdges() == 1 && s0.getNumLeavingEdges() == 1) {
        chain0 = cs.getChainWith(s0);
      }
      Chain chain1 = new Chain();
      if (s1.getNumEnteringEdges() == 1 && s1.getNumLeavingEdges() == 1) {
        chain1 = cs.getChainWith(s1);
      }

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
    int found = 0;

    for (Pair<CFANode, Chain> p : getAllObjects(pParseResult)) {
      if (!canRemove(pParseResult, p)) {
        continue;
      }

      if (starts.contains(p.getFirst())) {
        continue;
      }

      CFANode end = p.getSecond().getLast();
      if (!(end instanceof CFATerminationNode)) {
        end = end.getLeavingEdge(0).getSuccessor();
      }
      if (ends.contains(end)) {
        continue;
      }

      starts.add(p.getFirst());
      ends.add(end);
      answer.add(p);
      if (++found >= pCount) {
        break;
      }
    }

    return answer;
  }

  @Override
  protected Pair<CFANode, Chain> getRollbackInfo(
      ParseResult pParseResult, Pair<CFANode, Chain> pObject) {
    return pObject;
  }

  @Override
  protected void removeObject(ParseResult pParseResult, Pair<CFANode, Chain> pObject) {
    CFANode branchingPoint = pObject.getFirst();
    Chain pChain = pObject.getSecond();

    logger.logf(Level.INFO, "removing branching on node %s with chain %s", branchingPoint, pChain);

    CFAEdge edgeToChain = pChain.getEnteringEdge();
    CFAEdge leavingEdge = CFAUtils.getComplimentaryAssumeEdge((AssumeEdge) edgeToChain);
    CFANode successor = leavingEdge.getSuccessor();

    disconnectEdgeFromNode(leavingEdge, successor);

    for (CFAEdge enteringEdge : CFAUtils.enteringEdges(branchingPoint)) {
      CFANode predecessor = enteringEdge.getPredecessor();
      disconnectEdgeFromNode(enteringEdge, predecessor);
      connectEdge(dupEdge(enteringEdge, successor));
    }

    removeNodeFromParseResult(pParseResult, branchingPoint);
    for (CFANode node : pChain) {
      removeNodeFromParseResult(pParseResult, node);
    }

    CFANode lastNode = pChain.getLast();
    for (CFAEdge edgeFromChain : CFAUtils.leavingEdges(lastNode)) {
      disconnectEdgeFromNode(edgeFromChain, edgeFromChain.getSuccessor());
    }
  }

  @Override
  protected void returnObject(ParseResult pParseResult, Pair<CFANode, Chain> pRollbackInfo) {
    CFANode branchingPoint = pRollbackInfo.getFirst();
    Chain pChain = pRollbackInfo.getSecond();

    logger.logf(Level.INFO, "returning branching on node %s with chain %s", branchingPoint, pChain);

    CFAEdge edgeToChain = pChain.getEnteringEdge();
    CFAEdge leavingEdge = CFAUtils.getComplimentaryAssumeEdge((AssumeEdge) edgeToChain);
    CFANode successor = leavingEdge.getSuccessor();

    for (CFAEdge enteringEdge : CFAUtils.enteringEdges(branchingPoint)) {
      CFANode predecessor = enteringEdge.getPredecessor();
      disconnectEdge(predecessor.getEdgeTo(successor));
      connectEdgeToNode(enteringEdge, predecessor);
    }

    connectEdgeToNode(leavingEdge, successor);

    addNodeToParseResult(pParseResult, branchingPoint);
    for (CFANode node : pChain) {
      addNodeToParseResult(pParseResult, node);
    }

    CFANode lastNode = pChain.getLast();
    for (CFAEdge edgeFromChain : CFAUtils.leavingEdges(lastNode)) {
      connectEdgeToNode(edgeFromChain, edgeFromChain.getSuccessor());
    }
  }
}
