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

import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.Deque;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFATraversal.TraversalProcess;

class ChainVisitor extends CFATraversal.DefaultCFAVisitor {
  private Deque<CFANode> chainNodes = new ArrayDeque<>();
  private boolean forwards;

  private ChainVisitor() {
    forwards = false;
  }

  public static boolean chainNode(CFANode pNode) {
    return pNode.getNumLeavingEdges() == 1 && pNode.getNumEnteringEdges() == 1;
  }

  @Override
  public TraversalProcess visitNode(CFANode pNode) {
    assert !chainNodes.contains(pNode) : pNode.toString() + " is already in chain " + chainNodes;

    if (!chainNode(pNode)) {
      return TraversalProcess.SKIP;
    }

    if (forwards) {
      chainNodes.addLast(pNode);
    } else {
      chainNodes.addFirst(pNode);
    }
    return TraversalProcess.CONTINUE;
  }

  private void changeDirection() {
    forwards = !forwards;
  }

  private Chain prepareChain() {
    return new Chain(chainNodes);
  }

  public static Chain getChainWith(CFANode pNode) {
    if (!chainNode(pNode)) {
      // if the node can't be in chain, there is no chain
      return new Chain(ImmutableList.of());
    }

    // Add the chain 'before' node
    ChainVisitor chainVisitor = new ChainVisitor();
    CFATraversal.dfs().backwards().traverse(pNode, chainVisitor);

    // Add the second part of the chain
    chainVisitor.changeDirection();
    CFANode successor = pNode.getLeavingEdge(0).getSuccessor();
    CFATraversal.dfs().traverse(successor, chainVisitor);
    return chainVisitor.prepareChain();
  }
}
