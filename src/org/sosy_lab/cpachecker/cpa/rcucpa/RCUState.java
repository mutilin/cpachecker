/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.rcucpa;

import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import static com.google.common.collect.FluentIterable.from;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleState;
import org.sosy_lab.cpachecker.cpa.usage.UsageTreeNode;
import org.sosy_lab.cpachecker.cpa.usage.refinement.LocalInfoProvider;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GeneralIdentifier;

public class RCUState implements LatticeAbstractState<RCUState>, CompatibleState, UsageTreeNode,
                                 LocalInfoProvider {
  private final Map<AbstractIdentifier, Set<AbstractIdentifier>> rcuRelations;
  private final Set<AbstractIdentifier> outdatedRCU;
  private final Set<AbstractIdentifier> localAgain;
  private final LockStateRCU lockState;

  RCUState(LockStateRCU pLockState, Map<AbstractIdentifier, Set<AbstractIdentifier>> pRcuRel,
                  Set<AbstractIdentifier> pOutdatedRCU, Set<AbstractIdentifier> pLocalAgain) {
    rcuRelations = pRcuRel;
    outdatedRCU = pOutdatedRCU;
    localAgain = pLocalAgain;
    lockState = pLockState;
  }

  RCUState() {
    this(new LockStateRCU(), new HashMap<>(), new HashSet<>(), new HashSet<>());
  }

  @Override
  public RCUState join(RCUState other) {
    return null;
  }

  @Override
  public boolean isLessOrEqual(RCUState other) throws CPAException, InterruptedException {
    if (!lockState.isLessOrEqual(other.lockState)) {
      return false;
    }

    Set<AbstractIdentifier> sub = new HashSet<>(rcuRelations.keySet());
    sub.retainAll(other.rcuRelations.keySet());
    if (sub.size() < rcuRelations.keySet().size()
        && sub.size() < other.rcuRelations.keySet().size()) {
      return false;
    } else {
      // TODO: ...
    }

    sub = new HashSet<>(outdatedRCU);
    sub.retainAll(other.outdatedRCU);
    if (sub.size() < outdatedRCU.size() && sub.size() < other.outdatedRCU.size()) {
      return false;
    }

    sub = new HashSet<>(localAgain);
    sub.retainAll(other.localAgain);
    if (sub.size() < localAgain.size() && sub.size() < other.localAgain.size()) {
      return false;
    }

    return true;
  }

  LockStateRCU getLockState() {
    return lockState;
  }

  void fillLocal() {
    localAgain.addAll(outdatedRCU);
    outdatedRCU.clear();
  }

  void addToOutdated(AbstractIdentifier pRcuPtr) {
    outdatedRCU.add(pRcuPtr);
    for (AbstractIdentifier id : rcuRelations.keySet()) {
      if (rcuRelations.get(id).contains(pRcuPtr)) {
        outdatedRCU.add(id);
      }
    }
  }

  void addToRelations(AbstractIdentifier pAil, AbstractIdentifier pInit) {
    if (!rcuRelations.containsKey(pAil)) {
      rcuRelations.put(pAil, new HashSet<>());
    }
    if (pInit != null) {
      rcuRelations.get(pAil).add(pInit);
    }
  }

  @Override
  public boolean isCompatibleWith(CompatibleState state) {
    Preconditions.checkArgument(state instanceof RCUState);
    System.out.println("TOP_COMP");
    System.out.println("This state:");
    System.out.println(this);
    System.out.println();
    System.out.println("Other state:");
    System.out.println((RCUState) state);
    System.out.println();
    return lockState.isCompatible(((RCUState) state).lockState);
  }

  @Override
  public CompatibleState prepareToStore() {
    return this;
  }

  @Override
  public UsageTreeNode getTreeNode() {
    return this;
  }

  @Override
  public int compareTo(CompatibleState o) {
    // TODO: implement this
    try {
      if (this.isLessOrEqual((RCUState) o)) {
        return 0;
      } else {
        return 1;
      }
    } catch (CPAException pE) {
      pE.printStackTrace();
    } catch (InterruptedException pE) {
      pE.printStackTrace();
    }
    return -1;
  }

  @Override
  public String toString() {
    String result = "Lock state: " + lockState.toString()
        + "\nRCU relations: " + rcuRelations
        + "\nOutdated RCU: " + outdatedRCU
        + "\nLocal Again: " + localAgain;
    return result;
  }

  public static RCUState copyOf(RCUState pState) {
    return new RCUState(pState.lockState, pState.rcuRelations, pState.outdatedRCU, pState.localAgain);
  }

  @Override
  public boolean cover(UsageTreeNode node) {
    // TODO: possible optimization
    return false;
  }

  @Override
  public boolean isLocal(GeneralIdentifier id) {
    if (!localAgain.isEmpty()) {
      FluentIterable<GeneralIdentifier> genIds = from(localAgain).transform
          (AbstractIdentifier::getGeneralId);
      if (genIds.anyMatch(i -> i.equals(id))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object pO) {
    if (this == pO) {
      return true;
    }
    if (pO == null || getClass() != pO.getClass()) {
      return false;
    }

    RCUState rcuState = (RCUState) pO;

    if (!rcuRelations.equals(rcuState.rcuRelations)) {
      return false;
    }
    if (!outdatedRCU.equals(rcuState.outdatedRCU)) {
      return false;
    }
    if (!localAgain.equals(rcuState.localAgain)) {
      return false;
    }
    if (!lockState.equals(rcuState.lockState)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = rcuRelations.hashCode();
    result = 31 * result + outdatedRCU.hashCode();
    result = 31 * result + localAgain.hashCode();
    result = 31 * result + lockState.hashCode();
    return result;
  }
}
