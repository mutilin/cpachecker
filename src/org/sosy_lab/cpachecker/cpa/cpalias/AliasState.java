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
import java.util.logging.Level;
import org.sosy_lab.common.log.LogManager;
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

  public static void addToRCU(AliasState pResult, AbstractIdentifier pId, LogManager pLogger){
    Set<AbstractIdentifier> old = new HashSet<>(pResult.rcu);
    pResult.rcu.add(pId);
    if (!old.containsAll(pResult.rcu)) {
      Set<AbstractIdentifier> alias = pResult.alias.get(pId);
      pLogger.log(Level.ALL, "Added synonyms for <" + (pId == null ? "NULL" : pId.toString()) +
          "> which are: " + (alias == null? "NULL" : alias.toString()));
      if (alias != null && !alias.isEmpty()) {
        for (AbstractIdentifier ai : alias) {
          addToRCU(pResult, ai, pLogger);
        }
      }
      for (AbstractIdentifier key : pResult.alias.keySet()) {
        if (pResult.alias.get(key).contains(pId)) {
          addToRCU(pResult, key, pLogger);
        }
      }
    }
  }

  public void addAlias(AbstractIdentifier key, AbstractIdentifier value, LogManager logger) {
    if (!this.alias.containsKey(key)) {
      this.alias.put(key, new HashSet<>());
    }
    if (value != null) {
      this.alias.get(key).add(value);
    }
    logger.log(Level.ALL, "Added alias <" + (value == null? "NULL" : value.toString()) + "> for "
        + "key <" + key.toString() + ">");
  }

  public void clearAlias(AbstractIdentifier key) {
    this.alias.get(key).clear();
  }

  @Override
  public AliasState join(AliasState other) {
    Map<AbstractIdentifier, Set<AbstractIdentifier>> alias = new HashMap<>();
    Set<AbstractIdentifier> rcu;
    for (AbstractIdentifier id : this.alias.keySet()) {
      alias.put(id, this.alias.get(id));
      alias.get(id).addAll(other.alias.get(id));
    }

    for (AbstractIdentifier id : other.alias.keySet()) {
      if (!this.alias.containsKey(id)) {
        alias.put(id, other.alias.get(id));
      }
    }

    rcu = new HashSet<>(this.rcu);
    rcu.addAll(other.rcu);

    AliasState newState = new AliasState(alias, rcu);
    if (newState.equals(this)){
      return this;
    } else {
      return newState;
    }
  }

  @Override
  public boolean isLessOrEqual(AliasState other)
      throws CPAException, InterruptedException {
    boolean sameAlias = false;
    boolean sameRcu = false;

    if (this.alias.isEmpty() && other.alias.isEmpty()) {
      sameAlias = true;
    } else if (other.alias.keySet().containsAll(this.alias.keySet()) &&
        containsAll(other.alias, this.alias)) {
      sameAlias = true;
    }

    if (other.rcu.isEmpty() && this.rcu.isEmpty()) {
      sameRcu = true;
    } else if (other.rcu.containsAll(this.rcu)) {
      sameRcu = true;
    }

    return sameAlias && sameRcu;
  }

  private boolean containsAll(
      Map<AbstractIdentifier, Set<AbstractIdentifier>> greater,
      Map<AbstractIdentifier, Set<AbstractIdentifier>> lesser) {
    for (AbstractIdentifier id : lesser.keySet()) {
      if (!greater.get(id).containsAll(lesser.get(id))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean equals(Object pO) {
    if (this == pO) {
      return true;
    }
    if (pO == null || getClass() != pO.getClass()) {
      return false;
    }

    AliasState that = (AliasState) pO;

    if (!alias.equals(that.alias)) {
      return false;
    }
    if (!rcu.equals(that.rcu)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = alias.hashCode();
    result = 31 * result + rcu.hashCode();
    return result;
  }

  public String getContents() {
    return "\nAlias: " + alias.toString() + "\nRCU: " + rcu.toString();
  }

  @Override
  public String toString() {
    return getContents();
  }
}
