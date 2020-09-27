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

import java.util.ArrayDeque;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;

public class Chain extends ArrayDeque<CFANode> {

  private static final long serialVersionUID = -1849261707800370541L;

  public CFAEdge getEnteringEdge() {
    CFANode firstNode = peekFirst();
    if (firstNode == null) {
      return null;
    }
    assert firstNode.getNumEnteringEdges() == 1;
    return firstNode.getEnteringEdge(0);
  }

  public CFANode getPredecessor() {
    CFAEdge e = getEnteringEdge();
    return e == null ? null : e.getPredecessor();
  }

  public CFAEdge getLeavingEdge() {
    CFANode lastNode = peekLast();
    if (lastNode == null) {
      return null;
    }
    assert lastNode.getNumLeavingEdges() == 1;
    return lastNode.getLeavingEdge(0);
  }

  public CFANode getSuccessor() {
    CFAEdge e = getLeavingEdge();
    return e == null ? null : e.getSuccessor();
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

  @Override
  public int hashCode() {
    int result = 37;
    result += peekFirst() == null ? 0 : peekFirst().hashCode();
    result *= 37;
    result += peekLast() == null ? 0 : peekLast().hashCode();
    return result;
  }

  @Override
  public boolean equals(Object pObj) {
    return super.equals(pObj);
  }
}
