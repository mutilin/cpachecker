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

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleNode;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleState;
import org.sosy_lab.cpachecker.cpa.usage.refinement.AliasInfoProvider;
import org.sosy_lab.cpachecker.cpa.usage.refinement.LocalInfoProvider;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GeneralIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

public class RCUState implements LatticeAbstractState<RCUState>,
    CompatibleNode, LocalInfoProvider, AliasInfoProvider {
  private final ImmutableMultimap<AbstractIdentifier, AbstractIdentifier> rcuRelations;
  private final ImmutableSet<AbstractIdentifier> outdatedRCU;
  private final ImmutableSet<AbstractIdentifier> localAgain;
  private final LockStateRCU lockState;
  private final ImmutableMap<AbstractIdentifier, AbstractIdentifier> temporaryIds;

  RCUState(
      LockStateRCU pLockState,
      ImmutableMultimap<AbstractIdentifier, AbstractIdentifier> pRcuRel,
      ImmutableSet<AbstractIdentifier> pOutdatedRCU,
      ImmutableSet<AbstractIdentifier> pLocalAgain,
      ImmutableMap<AbstractIdentifier, AbstractIdentifier> pTmpMapping) {
    lockState = pLockState;
    rcuRelations = pRcuRel;
    outdatedRCU = pOutdatedRCU;
    localAgain = pLocalAgain;
    temporaryIds = pTmpMapping;
  }

  RCUState() {
    this(
        new LockStateRCU(),
        ImmutableMultimap.of(),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableMap.of());
  }

  @Override
  public RCUState join(RCUState other) {
    Multimap<AbstractIdentifier, AbstractIdentifier> newRel = HashMultimap.create(rcuRelations);
    for (Entry<AbstractIdentifier, AbstractIdentifier> entry : other.rcuRelations.entries()) {
      newRel.put(entry.getKey(), entry.getValue());
    }
    Set<AbstractIdentifier> newOutdated = new TreeSet<>(this.outdatedRCU);
    newOutdated.addAll(other.outdatedRCU);

    Set<AbstractIdentifier> newLocal = new TreeSet<>(this.localAgain);
    newLocal.addAll(other.localAgain);

    Map<AbstractIdentifier, AbstractIdentifier> newTmp = new TreeMap<>(this.temporaryIds);
    for (Entry<AbstractIdentifier, AbstractIdentifier> entry : other.temporaryIds.entrySet()) {
      newTmp.putIfAbsent(entry.getKey(), entry.getValue());
    }

    LockStateRCU newLock = this.lockState.join(other.lockState);

    return new RCUState(
        newLock,
        ImmutableMultimap.copyOf(newRel),
        ImmutableSet.copyOf(newOutdated),
        ImmutableSet.copyOf(newLocal),
        ImmutableMap.copyOf(newTmp));
  }

  @Override
  public boolean isLessOrEqual(RCUState other) {
    // TODO comparing by sizes looks strange
    if (!lockState.isLessOrEqual(other.lockState)) {
      return false;
    }

    Set<AbstractIdentifier> sub = new TreeSet<>(rcuRelations.keySet());
    sub.retainAll(other.rcuRelations.keySet());
    if (sub.size() < rcuRelations.keySet().size()
        && sub.size() < other.rcuRelations.keySet().size()) {
      return false;
    } else {
      // TODO: ...
    }

    sub = new TreeSet<>(outdatedRCU);
    sub.retainAll(other.outdatedRCU);
    if (sub.size() < outdatedRCU.size() && sub.size() < other.outdatedRCU.size()) {
      return false;
    }

    sub = new TreeSet<>(localAgain);
    sub.retainAll(other.localAgain);
    if (sub.size() < localAgain.size() && sub.size() < other.localAgain.size()) {
      return false;
    }

    return true;
  }

  RCUState fillLocal() {
    Set<AbstractIdentifier> local = new TreeSet<>(localAgain);
    local.addAll(outdatedRCU);
    return new RCUState(lockState, rcuRelations,
        ImmutableSet.of(),
        ImmutableSet.copyOf(local),
        temporaryIds);
  }

  RCUState addToOutdated(AbstractIdentifier pRcuPtr) {
    Set<AbstractIdentifier> outdated = new TreeSet<>(outdatedRCU);
    outdated.add(pRcuPtr);
    for (Entry<AbstractIdentifier, AbstractIdentifier> entry : rcuRelations.entries()) {
      if (entry.getValue().equals(pRcuPtr)) {
        outdated.add(entry.getKey());
      }
    }
    return new RCUState(lockState, rcuRelations,
        ImmutableSet.copyOf(outdated),
        localAgain,
        temporaryIds);
  }

  RCUState addToRelations(AbstractIdentifier pAil, AbstractIdentifier pInit) {
    if (pInit != null) {
      Multimap<AbstractIdentifier, AbstractIdentifier> relations = LinkedListMultimap.create(rcuRelations);
      relations.put(pAil, pInit);
      return new RCUState(
          lockState,
          ImmutableMultimap.copyOf(relations),
                          outdatedRCU, localAgain, temporaryIds);
    }
    return this;
  }

  @Override
  public boolean isCompatibleWith(CompatibleState state) {
    Preconditions.checkArgument(state instanceof RCUState);
    return lockState.isCompatible(((RCUState) state).lockState);
  }

  @Override
  public int compareTo(CompatibleState o) {
    // TODO: implement this
    if (this.isLessOrEqual((RCUState) o)) {
      return 0;
    } else {
      return 1;
    }
  }

  @Override
  public String toString() {
    String result = "Lock state: " + lockState.toString()
        + "\nRCU relations: " + rcuRelations
        + "\nOutdated RCU: " + outdatedRCU
        + "\nLocal Again: " + localAgain
        + "\nTmp mapping: " + temporaryIds;
    return result;
  }

  public static RCUState copyOf(RCUState pState) {
    return new RCUState(
        pState.lockState,
                        pState.rcuRelations,
                        pState.outdatedRCU,
                        pState.localAgain,
                        pState.temporaryIds);
  }

  @Override
  public boolean cover(CompatibleNode node) {
    // TODO: possible optimization
    return false;
  }

  @Override
  public boolean isLocal(GeneralIdentifier id) {
    if (!localAgain.isEmpty()) {
      FluentIterable<GeneralIdentifier> genIds =
          from(localAgain).transform(AbstractIdentifier::getGeneralId);
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

    // TODO: Problems with equals of Multimap
    if (!rcuRelations.asMap().equals(rcuState.rcuRelations.asMap())) {
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
    if (!temporaryIds.equals(rcuState.temporaryIds)) {
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
    result = 31 * result + temporaryIds.hashCode();
    return result;
  }

  @Override
  public Set<AbstractIdentifier> getAllPossibleIds(AbstractIdentifier id) {
    Set<AbstractIdentifier> result = new TreeSet<>();

    if (id instanceof SingleIdentifier) {
      SingleIdentifier sid = (SingleIdentifier) id;
      if (sid.getDereference() > 0) {
        for (int i = 0; i <= sid.getDereference(); ++i) {
          AbstractIdentifier clone = sid.cloneWithDereference(i);
          if (rcuRelations.containsKey(clone)) {
            result.addAll(rcuRelations.get(clone));
          }
          if (rcuRelations.containsValue(clone)) {
            for (Entry<AbstractIdentifier, AbstractIdentifier> entry : rcuRelations.entries()) {
              if (entry.getValue().equals(clone)) {
                result.add(entry.getKey());
              }
            }
          }
        }
      }
    }

    return result;
  }

  @Override
  public Set<AbstractIdentifier> getUnnecessaryIds(AbstractIdentifier pIdentifier, Set<AbstractIdentifier> pSet) {
    return ImmutableSet.of();
  }

  RCUState incRCURead() {
    return new RCUState(
        lockState.incRCURead(),
        rcuRelations,
        outdatedRCU,
        localAgain,
        temporaryIds);
  }

  RCUState decRCURead() {
    return new RCUState(
        lockState.decRCURead(),
        rcuRelations,
        outdatedRCU, localAgain, temporaryIds);
  }

  RCUState markRead() {
    return new RCUState(
        lockState.markRead(),
        rcuRelations,
        outdatedRCU,
        localAgain,
        temporaryIds);
  }

  RCUState markWrite() {
    return new RCUState(lockState.markWrite(), rcuRelations,
        outdatedRCU, localAgain, temporaryIds);
  }

  RCUState clearLock() {
    return new RCUState(lockState.clearLock(), rcuRelations,
        outdatedRCU, localAgain, temporaryIds);
  }

  RCUState addTmpMapping(AbstractIdentifier tmp, AbstractIdentifier nonTmp) {
    Map<AbstractIdentifier, AbstractIdentifier> map = new TreeMap<>(temporaryIds);
    map.put(tmp, nonTmp);
    return new RCUState(lockState, rcuRelations,
        outdatedRCU,
        localAgain,
        ImmutableMap.copyOf(map));
  }

  public AbstractIdentifier getNonTemporaryId(AbstractIdentifier pId) {
    return temporaryIds.get(pId);
  }
}
