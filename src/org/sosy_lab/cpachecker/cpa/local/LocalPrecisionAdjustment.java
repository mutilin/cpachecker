// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.local;

import com.google.common.base.Function;
import java.util.Optional;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class LocalPrecisionAdjustment implements PrecisionAdjustment {

  @Override
  public Optional<PrecisionAdjustmentResult> prec(
      AbstractState pState,
      Precision pPrecision,
      UnmodifiableReachedSet pStates,
      Function<AbstractState, AbstractState> pStateProjection,
      AbstractState pFullState)
      throws CPAException, InterruptedException {

    if (pPrecision instanceof SingletonPrecision) {
      // No refinement
      return Optional.of(PrecisionAdjustmentResult.create(pState, pPrecision, Action.CONTINUE));
    }

    LocalState oldState = (LocalState) pState;
    LocalPrecision localPrecision = (LocalPrecision) pPrecision;
    LocalState resultState = oldState.forget(localPrecision.getIds());

    resultState = resultState.equals(oldState) ? oldState : resultState;

    return Optional.of(PrecisionAdjustmentResult.create(resultState, pPrecision, Action.CONTINUE));
  }

}
