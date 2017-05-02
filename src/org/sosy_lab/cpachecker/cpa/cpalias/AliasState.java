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
package org.sosy_lab.cpachecker.cpa.cpalias;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;

public class AliasState implements LatticeAbstractState<AliasState> {
  private Map<AbstractIdentifier, Set<AbstractIdentifier>> alias;
  private Set<AbstractIdentifier> rcu;

  public AliasState(
      Map<AbstractIdentifier, Set<AbstractIdentifier>> palias,
      Set<AbstractIdentifier> prcu) {
    alias = palias;
    rcu = prcu;
  }

  //TODO implement add() (and others) in state, not use get() to add smth directly to map
  private Map<AbstractIdentifier, Set<AbstractIdentifier>> getAlias() {
    return alias;
  }
  private Set<AbstractIdentifier> getRcu() {
    return rcu;
  }

  public static void addToRCU(AliasState pResult, AbstractIdentifier pId){
    Set<AbstractIdentifier> old = pResult.getRcu();
    pResult.getRcu().add(pId);
    if (!pResult.getRcu().equals(old)) {
      Set<AbstractIdentifier> alias = pResult.getAlias().get(pId);
      for (AbstractIdentifier ai : alias) {
        addToRCU(pResult, ai);
      }
    }
  }

  public void addAlias(AbstractIdentifier key, AbstractIdentifier value) {
    if (!this.alias.containsKey(key)) {
      this.alias.put(key, new HashSet<>());
    }
    if (value != null) {
      this.alias.get(key).add(value);
    }
  }

  public void clearAlias(AbstractIdentifier key) {
    this.alias.get(key).clear();
  }

  @Override
  public AliasState join(AliasState other) {
    Map<AbstractIdentifier, Set<AbstractIdentifier>> alias = new HashMap<>();
    Set<AbstractIdentifier> rcu;
    for (AbstractIdentifier id : this.getAlias().keySet()) {
      alias.put(id, this.getAlias().get(id));
      alias.get(id).addAll(other.getAlias().get(id));
    }

    for (AbstractIdentifier id : other.getAlias().keySet()) {
      if (!this.getAlias().containsKey(id)) {
        alias.put(id, other.getAlias().get(id));
      }
    }

    rcu = this.getRcu();
    rcu.addAll(other.getRcu());

    return new AliasState(alias, rcu);
  }

  @Override
  public boolean isLessOrEqual(AliasState other)
      throws CPAException, InterruptedException {
    return other.getAlias().keySet().containsAll(this.getAlias().keySet()) &&
        containsAll(other.getAlias(), this.getAlias()) &&
        other.getRcu().containsAll(this.getRcu());
  }

  private boolean containsAll(
      Map<AbstractIdentifier, Set<AbstractIdentifier>> mG,
      Map<AbstractIdentifier, Set<AbstractIdentifier>> mL) {
    for (AbstractIdentifier id : mL.keySet()) {
      if (!mG.get(id).containsAll(mL.get(id))) {
        return false;
      }
    }
    return true;
  }

}
