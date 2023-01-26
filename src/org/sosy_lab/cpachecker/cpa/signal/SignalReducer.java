// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.signal;

import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.core.defaults.GenericReducer;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.interfaces.Precision;

public class SignalReducer extends GenericReducer<SignalState, SingletonPrecision> {

  @Override
  protected SignalState
      getVariableReducedState0(SignalState pExpandedState, Block pContext, CFANode pCallNode)
          throws InterruptedException {
    return pExpandedState.reduce();
  }

  @Override
  protected SignalState getVariableExpandedState0(
      SignalState pRootState,
      Block pReducedContext,
      SignalState pReducedState)
      throws InterruptedException {
    return pReducedState.expand(pRootState);
  }

  @Override
  protected Object getHashCodeForState0(SignalState pStateKey, SingletonPrecision pPrecisionKey) {
    return pStateKey;
  }

  @Override
  protected Precision getVariableReducedPrecision0(SingletonPrecision pPrecision, Block pContext) {
    return pPrecision;
  }

  @Override
  protected SingletonPrecision getVariableExpandedPrecision0(
      SingletonPrecision pRootPrecision,
      Block pRootContext,
      SingletonPrecision pReducedPrecision) {
    return pRootPrecision;
  }

  @Override
  protected SignalState rebuildStateAfterFunctionCall0(
      SignalState pRootState,
      SignalState pEntryState,
      SignalState pExpandedState,
      FunctionExitNode pExitLocation) {
    return pExpandedState;
  }

}
