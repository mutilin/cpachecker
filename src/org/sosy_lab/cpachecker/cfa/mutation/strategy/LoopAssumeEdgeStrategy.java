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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.Triple;

public class LoopAssumeEdgeStrategy
    extends GenericCFAMutationStrategy<Chain, Triple<CFAEdge, CFAEdge, CFAEdge>> {

  public LoopAssumeEdgeStrategy(Configuration pConfig, LogManager pLogger)
      throws InvalidConfigurationException {
    super(pConfig, pLogger, "Branch-loops");
  }

  private Collection<CFAEdge> getBackwardEnteringEdges(final CFANode pNode) {
    return Lists.newArrayList(
        CFAUtils.enteringEdges(pNode).filter(SimpleAssumeEdgeStrategy::isBackwardEdge));
  }

  @Override
  protected Collection<Chain> getAllObjects(ParseResult pParseResult) {
    Collection<Chain> answer = new ArrayList<>();
    for (CFANode node : pParseResult.getCFANodes().values()) {
      if (node.getNumLeavingEdges() != 2) {
        continue;
      }

      Collection<CFAEdge> backwardEnteringEdges = getBackwardEnteringEdges(node);
      if (backwardEnteringEdges.isEmpty()) {
        continue;
      }

      Chain chain = null;
      // continue if the only entering chain does not start on node
      if (backwardEnteringEdges.size() == 1) {
        CFANode onlyBackwardPredecessor =
            Iterables.getOnlyElement(backwardEnteringEdges).getPredecessor();
        chain = getChainFor(onlyBackwardPredecessor, node, Chain::getPredecessor);
        if (chain != null) {
          answer.add(chain);
        }
        continue;
      }

      // try chains on assume edges
      CFANode successor = node.getLeavingEdge(0).getSuccessor();
      chain = getChainFor(successor, node, Chain::getSuccessor);

      successor = node.getLeavingEdge(1).getSuccessor();
      Chain otherChain = getChainFor(successor, node, Chain::getSuccessor);

      if (chain == null && otherChain == null) {
        continue;
      } else if (chain == null) {
        chain = otherChain;
      } else if (otherChain != null) {
          logger.logf(
              Level.SEVERE,
              "Got two loop chains at one branching point:\n%s\n%s",
              chain,
              otherChain);
          continue;
      }

      if (chain != null) {
        answer.add(chain);
      }
    }

    return answer;
  }

  private Chain getChainFor(CFANode chainNode, CFANode target, Function<Chain, CFANode> check) {
    Chain chain = null;
    if (chainNode.getNumEnteringEdges() == 1 && chainNode.getNumLeavingEdges() == 1) {
      chain = ChainVisitor.getChainWith(chainNode);
      if (check.apply(chain) != target) {
        chain = null;
      }
    }
    return chain;
  }

  @Override
  protected Triple<CFAEdge, CFAEdge, CFAEdge> removeObject(ParseResult pParseResult, Chain pChain) {
    CFAEdge edgeToChain = pChain.getEnteringEdge();
    CFANode branchingPoint = edgeToChain.getPredecessor();
    logger.log(logObjects, "removing " + pChain + " at " + branchingPoint);
    CFAEdge leavingEdge = CFAUtils.getComplimentaryAssumeEdge((AssumeEdge) edgeToChain);
    CFANode successor = leavingEdge.getSuccessor();
    CFAEdge edgeFromChain = pChain.getLeavingEdge();

    removeNodeFromParseResult(pParseResult, branchingPoint);
    disconnectEdgeFromSuccessor(leavingEdge);
    replaceEdgeTo(edgeFromChain, successor);

    disconnectEdgeFromSuccessor(edgeToChain);
    for (CFAEdge enteringEdge : CFAUtils.enteringEdges(branchingPoint)) {
      replaceEdgeByPredecessor(enteringEdge, pChain.get(0));
    }

    return Triple.of(edgeToChain, leavingEdge, edgeFromChain);
  }

  @Override
  protected void returnObject(
      ParseResult pParseResult, Triple<CFAEdge, CFAEdge, CFAEdge> pRollbackInfo) {
    CFAEdge edgeToChain = pRollbackInfo.getFirst();
    CFAEdge leavingEdge = pRollbackInfo.getSecond();
    CFAEdge edgeFromChain = pRollbackInfo.getThird();
    CFANode branchingPoint = leavingEdge.getPredecessor();
    logger.log(logObjects, "returning chain at " + branchingPoint);
    CFANode firstNode = edgeToChain.getSuccessor();
    CFANode successor = edgeFromChain.getSuccessor();

    disconnectEdge(edgeFromChain.getPredecessor().getEdgeTo(successor));
    connectEdgeToNode(leavingEdge, successor);
    connectEdge(edgeFromChain);

    for (CFAEdge enteringEdge : CFAUtils.enteringEdges(branchingPoint)) {
      disconnectEdge(enteringEdge.getPredecessor().getEdgeTo(firstNode));
      connectEdgeToNode(enteringEdge, enteringEdge.getPredecessor());
    }
    connectEdgeToNode(edgeToChain, firstNode);
    addNodeToParseResult(pParseResult, branchingPoint);
  }
}
