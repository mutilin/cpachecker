// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.lock;

import org.sosy_lab.cpachecker.cpa.lock.LockState.LockTreeNode;
import org.sosy_lab.cpachecker.cpa.usage.storage.Delta;

public class LockNodeDelta implements Delta<LockTreeNode> {

  private final LockTreeNode delta;

  public LockNodeDelta(LockTreeNode pSet) {
    delta = pSet;
  }

  @Override
  public LockTreeNode apply(LockTreeNode pState) {
    if (delta.isEmpty()) {
      return pState;
    }
    if (pState.isEmpty()) {
      return delta;
    }

    LockTreeNode newSet = new LockTreeNode(delta);
    newSet.addAll(pState);
    return newSet;
  }

  @Override
  public boolean covers(Delta<LockTreeNode> pDelta) {
    LockNodeDelta pOther = (LockNodeDelta) pDelta;
    return delta.cover(pOther.delta);
  }

  @Override
  public LockNodeDelta add(Delta<LockTreeNode> pDelta) {
    LockNodeDelta pOther = (LockNodeDelta) pDelta;
    if (pOther.delta.isEmpty()) {
      return this;
    }
    if (delta.isEmpty()) {
      return pOther;
    }
    LockTreeNode newSet = new LockTreeNode(delta);
    newSet.addAll(pOther.delta);
    return new LockNodeDelta(newSet);
  }

}
