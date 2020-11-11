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
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;

/**
 * A chain is a sequence of nodes, joined with edges. Every node in this chain has only one entering
 * and only one leaving edge.
 */
public class Chain extends ArrayList<CFANode> {

  private static final long serialVersionUID = -1849261707800370541L;

  public Chain(Collection<CFANode> nodes) {
    super(nodes);
  }

  public CFAEdge getEnteringEdge() {
    CFANode firstNode = get(0);
    assert firstNode.getNumEnteringEdges() == 1;
    return firstNode.getEnteringEdge(0);
  }

  public CFANode getPredecessor() {
    return getEnteringEdge().getPredecessor();
  }

  public CFAEdge getLeavingEdge() {
    CFANode lastNode = get(size() - 1);
    assert lastNode.getNumLeavingEdges() == 1;
    return lastNode.getLeavingEdge(0);
  }

  public CFANode getSuccessor() {
    return getLeavingEdge().getSuccessor();
  }

  public String getDescription() {
    String desc;
    StringBuilder sb = new StringBuilder();
    for (CFANode n : this) {
      desc = n.getEnteringEdge(0).getDescription();
      if (!desc.isEmpty()) {
        sb.append("\n").append(desc);
      }
    }
    desc = getLeavingEdge().getDescription();
    if (!desc.isEmpty()) {
      sb.append("\n").append(desc);
    }
    if (sb.length() > 0) {
      sb.deleteCharAt(0);
    }
    return sb.toString();
  }
}
