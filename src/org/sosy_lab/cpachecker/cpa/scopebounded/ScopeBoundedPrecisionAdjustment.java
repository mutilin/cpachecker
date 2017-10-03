/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.scopebounded;

import com.google.common.base.Function;
import java.util.Optional;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.exceptions.CPAException;

class ScopeBoundedPrecisionAdjustment implements PrecisionAdjustment {

  final PrecisionAdjustment wrappedAdjustment;

  ScopeBoundedPrecisionAdjustment(final PrecisionAdjustment pWrappedAdjustment) {
    wrappedAdjustment = pWrappedAdjustment;
  }

  @Override
  public Optional<PrecisionAdjustmentResult> prec(
      final AbstractState pElement,
      final Precision pPrecision,
      final UnmodifiableReachedSet pElements,
      final Function<AbstractState, AbstractState> pProjection,
      final AbstractState fullState)
      throws CPAException, InterruptedException {

    return wrappedAdjustment.prec(fullState, pPrecision, pElements, pProjection, fullState);
  }
}
