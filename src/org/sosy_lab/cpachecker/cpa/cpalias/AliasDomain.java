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

import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;

public class AliasDomain implements AbstractDomain {
  @Override
  public AbstractState join(
      AbstractState state1, AbstractState state2) throws CPAException, InterruptedException {
    return null;
  }

  @Override
  public boolean isLessOrEqual(
      AbstractState state1, AbstractState state2) throws CPAException, InterruptedException {
    AliasState st1 = (AliasState) state1;
    AliasState st2 = (AliasState) state2;
    return st2.getAlias().keySet().containsAll(st1.getAlias().keySet()) &&
        containsAll(st2.getAlias(), st1.getAlias()) &&
        st2.getPrcu().containsAll(st1.getPrcu());
  }

  private boolean containsAll(Map<AbstractIdentifier, Set<AbstractIdentifier>> mG,
                              Map<AbstractIdentifier, Set<AbstractIdentifier>> mL) {
    for (AbstractIdentifier id : mL.keySet()) {
      if (!mG.get(id).containsAll(mL.get(id))) {
        return false;
      }
    }
    return true;
  }
}
