// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.local;

import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.core.defaults.GenericReducer;
import org.sosy_lab.cpachecker.core.interfaces.Precision;

public class LocalReducer extends GenericReducer<LocalState, Precision> {

  @Override
  protected LocalState getVariableReducedState0(
      LocalState pExpandedState, Block pContext, CFANode pCallNode) throws InterruptedException {
    return pExpandedState.reduce();
  }

  @Override
  protected LocalState getVariableExpandedState0(
      LocalState pRootState, Block pReducedContext, LocalState pReducedState)
      throws InterruptedException {
    return pReducedState.expand(pRootState);
  }

  @Override
  protected Object getHashCodeForState0(LocalState pStateKey, Precision pPrecisionKey) {
    return pStateKey.hashCode();
  }

  @Override
  protected Precision getVariableReducedPrecision0(Precision pPrecision, Block pContext) {
    return pPrecision;
  }

  @Override
  protected Precision getVariableExpandedPrecision0(
      Precision pRootPrecision,
      Block pRootContext,
      Precision pReducedPrecision) {
    return pRootPrecision;
  }

  @Override
  protected LocalState rebuildStateAfterFunctionCall0(
      LocalState pRootState,
      LocalState pEntryState,
      LocalState pExpandedState,
      FunctionExitNode pExitLocation) {
    return pExpandedState;
  }
}
