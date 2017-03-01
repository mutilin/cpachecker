/*
 *  CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.core.reachedset;

import java.util.HashSet;
import java.util.Set;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.waitlist.Waitlist.WaitlistFactory;

public class ValuableTransitionReachedSet extends PartitionedReachedSet {

  Set<AbstractState> valueableStates = new HashSet<>();
  Set<AbstractState> valueableTransitions = new HashSet<>();

  public ValuableTransitionReachedSet(WaitlistFactory pWaitlistFactory) {
    super(pWaitlistFactory);
  }

  public void addStateAsValuable(AbstractState state) {
    valueableStates.add(state);
  }

  public Iterable<AbstractState> getValueableStates() {
    return valueableStates;
  }

  public void addTransitionAsValuable(AbstractState state) {
    valueableTransitions.add(state);
  }

  public Iterable<AbstractState> getValueableTransitions() {
    return valueableTransitions;
  }

}
