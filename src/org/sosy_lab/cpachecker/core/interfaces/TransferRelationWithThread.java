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
package org.sosy_lab.cpachecker.core.interfaces;

import java.util.Collection;
import java.util.Collections;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

public interface TransferRelationWithThread extends TransferRelation {

  default Collection<? extends AbstractState> performTransferInEnvironment(
      AbstractState state,
      AbstractState stateInEnv,
      Precision precision)
          throws CPATransferException, InterruptedException {
    throw new CPATransferException("The transition without edge is not supported");
  }

  default Collection<? extends AbstractState> performTransferInEnvironment(
      AbstractState state,
      AbstractState stateInEnv,
      CFAEdge edge,
      Precision precision)
          throws CPATransferException, InterruptedException {
    return Collections.singleton(state);
  }

  default boolean isCompatible(AbstractState state1, AbstractState state2) { return true; }

  default boolean isValueableTransition(AbstractState state, AbstractState child) { return false; }
  default boolean isValueableState(AbstractState state) { return false; }
}
