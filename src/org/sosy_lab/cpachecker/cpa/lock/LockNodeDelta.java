// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.lock;

import edu.stanford.CVC4.UnsafeInterruptException;
import java.util.Objects;
import org.sosy_lab.cpachecker.cpa.lock.LockState.LockTreeNode;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleNode;
import org.sosy_lab.cpachecker.cpa.usage.storage.Delta;
import org.sosy_lab.cpachecker.cpa.usage.storage.GenericDelta;

public class LockNodeDelta implements Delta<CompatibleNode> {
  private final LockTreeNode delta;

  public LockNodeDelta(LockTreeNode pSet) {
    delta = pSet;
  }

  @Override
  public CompatibleNode apply(CompatibleNode pState) {
    if (delta.isEmpty()) {
      return pState;
    }
    LockTreeNode pNode = (LockTreeNode) pState;
    if (pNode.isEmpty()) {
      return delta;
    }

    LockTreeNode newSet = new LockTreeNode(delta);
    newSet.addAll(pNode);
    return newSet;
  }

  @Override
  public boolean covers(Delta<CompatibleNode> pDelta) {
    if (pDelta instanceof LockNodeDelta) {
      LockNodeDelta pOther = (LockNodeDelta) pDelta;
      return delta.cover(pOther.delta);
    } else if (pDelta == GenericDelta.getInstance()) {
      // Empty lock sets do not cover
      return false;
    } else {
      throw new UnsafeInterruptException("Unknown delta " + pDelta.getClass());
    }
  }

  @Override
  public Delta<CompatibleNode> add(Delta<CompatibleNode> pDelta) {
    if (pDelta instanceof LockNodeDelta) {
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
    } else if (pDelta == GenericDelta.getInstance()) {
      // Empty lock sets do not cover
      return this;
    } else {
      throw new UnsafeInterruptException("Unknown delta " + pDelta.getClass());
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(delta);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    return obj instanceof LockNodeDelta other && delta.equals(other.delta);
  }
}
