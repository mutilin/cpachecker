/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2019  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.notbam;

import com.google.common.base.Function;
import java.util.Optional;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;

public class NBAMPrecisionAdjustment implements PrecisionAdjustment {
  private NBAMCacheManager cacheManager;
  private PrecisionAdjustment wrappedPrecisionAdjustment;

  @SuppressWarnings("unused")
  private BlockPartitioning blockPartitioning;

  public NBAMPrecisionAdjustment(NBAMCacheManager pCacheManager,
    PrecisionAdjustment pWrappedPrecisionAdjustment, BlockPartitioning pBlockPartitioning)
  {
    wrappedPrecisionAdjustment = pWrappedPrecisionAdjustment;
    cacheManager = pCacheManager;
    blockPartitioning = pBlockPartitioning;
  }

  @Override
  public Optional<PrecisionAdjustmentResult> prec(AbstractState state, Precision precision,
      UnmodifiableReachedSet states, Function<AbstractState, AbstractState> stateProjection,
      AbstractState fullState) throws CPAException, InterruptedException
  {
    // precision might be outdated, if comes from a block-start and the inner part was refined.
    // so lets use the (expanded) inner precision.
    Precision validPrecision = precision;
    Precision expandedPrecision = cacheManager.extendedState(state).getExpandedPrecision();

    if (expandedPrecision != null) {
      validPrecision = expandedPrecision;
    }

    Optional<PrecisionAdjustmentResult> result = wrappedPrecisionAdjustment.prec(state,
        validPrecision, states, stateProjection, fullState);

    if (!result.isPresent()) {
      return result;
    }

    AbstractState newState = result.get().abstractState();
    if (!state.equals(newState)) {
      cacheManager.replaceState(state, newState);
    }

    return result;
  }
}
