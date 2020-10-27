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

import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFATraversal.TraversalProcess;

class ChainVisitor extends CFATraversal.DefaultCFAVisitor {
  private Chain chainNodes = new Chain();
  public boolean forwards;

  private ChainVisitor(boolean pForwards) {
    forwards = pForwards;
  }

  // can delete node with its only leaving edge and reconnect entering edge instead
  private static boolean canDeleteNode(CFANode pNode) {
    if (pNode instanceof FunctionEntryNode || pNode instanceof FunctionExitNode) {
      return false;
    }
    if (pNode.getNumLeavingEdges() != 1) {
      return false;
    }
    if (pNode.getNumEnteringEdges() != 1) {
      return false;
    }

    CFANode successor = pNode.getLeavingEdge(0).getSuccessor();
    CFANode predecessor = pNode.getEnteringEdge(0).getPredecessor();
    // and chains from with predecessor and successor are checked in getObjects
    return !predecessor.hasEdgeTo(successor);
  }

  @Override
  public TraversalProcess visitNode(CFANode pNode) {
    assert !chainNodes.contains(pNode) : pNode.toString() + " is already in chain " + chainNodes;

    if (!canDeleteNode(pNode)) {
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

  public static Chain getChainWith(CFANode pNode) {
    if (!canDeleteNode(pNode)) {
      return new Chain();
    }

    ChainVisitor chainVisitor = new ChainVisitor(false);
    CFATraversal.dfs().backwards().traverse(pNode, chainVisitor);

    if (pNode.getNumLeavingEdges() != 1) {
      return chainVisitor.chainNodes;
    }
    CFANode successor = pNode.getLeavingEdge(0).getSuccessor();
    if (successor.getNumEnteringEdges() > 1) {
      return chainVisitor.chainNodes;
    }

    chainVisitor.changeDirection();
    CFATraversal.dfs().traverse(successor, chainVisitor);
    return chainVisitor.chainNodes;
  }
}
