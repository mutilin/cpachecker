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
import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;

public class AliasMerge implements MergeOperator {
  @Override
  public AbstractState merge(
      AbstractState state1, AbstractState state2, Precision precision)
      throws CPAException, InterruptedException {
    AliasState st2 = (AliasState) state2;
    AliasState st1 = (AliasState) state1;

    Map<AbstractIdentifier, Set<AbstractIdentifier>> alias = new HashMap<>();
    Set<AbstractIdentifier> rcu;

    //TODO implement cloning carefully, now aliases are added to the state1
    for (AbstractIdentifier id : st1.getAlias().keySet()) {
      alias.put(id, st1.getAlias().get(id));
      alias.get(id).addAll(st2.getAlias().get(id));
    }

    for (AbstractIdentifier id : st2.getAlias().keySet()) {
      if (!st1.getAlias().containsKey(id)) {
        alias.put(id, st2.getAlias().get(id));
      }
    }

    rcu = st1.getPrcu();
    rcu.addAll(st2.getPrcu());

    return new AliasState(alias, rcu);
  }
}

