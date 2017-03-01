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
package org.sosy_lab.cpachecker.cpa.arg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelationWithThread;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCodeException;

public class ARGTransferRelation implements TransferRelationWithThread {

  private final TransferRelation transferRelation;

  public ARGTransferRelation(TransferRelation tr) {
    transferRelation = tr;
  }

  @Override
  public Collection<ARGState> getAbstractSuccessors(
      AbstractState pElement, Precision pPrecision)
      throws CPATransferException, InterruptedException {
    ARGState element = (ARGState)pElement;

    // covered elements may be in the reached set, but should always be ignored
    if (element.isCovered()) {
      return Collections.emptySet();
    }

    element.markExpanded();

    AbstractState wrappedState = element.getWrappedState();
    Collection<? extends AbstractState> successors;
    try {
      successors = transferRelation.getAbstractSuccessors(wrappedState, pPrecision);
    } catch (UnsupportedCodeException e) {
      // setting parent of this unsupported code part
      e.setParentState(element);
      throw e;
    }

    if (successors.isEmpty()) {
      return Collections.emptySet();
    }

    Collection<ARGState> wrappedSuccessors = new ArrayList<>();
    for (AbstractState absElement : successors) {
      ARGState successorElem = new ARGState(absElement, element);
      wrappedSuccessors.add(successorElem);
    }

    return wrappedSuccessors;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge) {

    throw new UnsupportedOperationException(
        "ARGCPA needs to be used as the outer-most CPA,"
        + " thus it does not support returning successors for a single edge.");
  }

  @Override
  public Collection<? extends AbstractState> performTransferInEnvironment(
      AbstractState state,
      AbstractState stateInEnv,
      Precision precision)
          throws CPATransferException, InterruptedException {
    assert transferRelation instanceof TransferRelationWithThread;
    ARGState element = (ARGState)state;
    ARGState envElement = (ARGState)stateInEnv;

    // covered elements may be in the reached set, but should always be ignored
    if (element.isCovered()) {
      return Collections.emptySet();
    }

    element.markExpanded();

    AbstractState wrappedState = element.getWrappedState();
    AbstractState wrappedEnvState = envElement.getWrappedState();
    Collection<? extends AbstractState> successors;
    try {
      successors = ((TransferRelationWithThread)transferRelation).performTransferInEnvironment(wrappedState, wrappedEnvState, precision);
    } catch (UnsupportedCodeException e) {
      // setting parent of this unsupported code part
      e.setParentState(element);
      throw e;
    }

    if (successors.isEmpty()) {
      return Collections.emptySet();
    }

    Collection<ARGState> wrappedSuccessors = new ArrayList<>();
    for (AbstractState absElement : successors) {
      ARGState successorElem = new ARGState(absElement, element);
      wrappedSuccessors.add(successorElem);
    }

    return wrappedSuccessors;
  }

  @Override
  public Collection<? extends AbstractState> performTransferInEnvironment(
      AbstractState state,
      AbstractState stateInEnv,
      CFAEdge edge,
      Precision precision)
          throws CPATransferException, InterruptedException {
    assert transferRelation instanceof TransferRelationWithThread;
    ARGState element = (ARGState)state;
    ARGState envElement = (ARGState)stateInEnv;

    // covered elements may be in the reached set, but should always be ignored
    if (element.isCovered()) {
      return Collections.emptySet();
    }

    element.markExpanded();

    AbstractState wrappedState = element.getWrappedState();
    AbstractState wrappedEnvState = envElement.getWrappedState();
    Collection<? extends AbstractState> successors;
    try {
      successors = ((TransferRelationWithThread)transferRelation).performTransferInEnvironment(wrappedState, wrappedEnvState, edge, precision);
    } catch (UnsupportedCodeException e) {
      // setting parent of this unsupported code part
      e.setParentState(element);
      throw e;
    }

    if (successors.isEmpty()) {
      return Collections.emptySet();
    }

    Collection<ARGState> wrappedSuccessors = new ArrayList<>();
    for (AbstractState absElement : successors) {
      ARGState successorElem = new ARGState(absElement, element);
      wrappedSuccessors.add(successorElem);
    }

    return wrappedSuccessors;
  }

  @Override
  public boolean isCompatible(AbstractState state1, AbstractState state2) {
    assert transferRelation instanceof TransferRelationWithThread;
    ARGState argState1 = (ARGState) state1;
    ARGState argState2 = (ARGState) state2;
    return ((TransferRelationWithThread)transferRelation).isCompatible(argState1.getWrappedState(), argState2.getWrappedState());
  }

  @Override
  public boolean isValueableTransition(AbstractState state, AbstractState child) {
    assert transferRelation instanceof TransferRelationWithThread;
    ARGState argState1 = (ARGState) state;
    ARGState argState2 = (ARGState) child;
    return ((TransferRelationWithThread)transferRelation).isValueableTransition(argState1.getWrappedState(), argState2.getWrappedState());
  }

  @Override
  public boolean isValueableState(AbstractState state) {
    assert transferRelation instanceof TransferRelationWithThread;
    ARGState argState = (ARGState) state;
    return ((TransferRelationWithThread)transferRelation).isValueableState(argState.getWrappedState());
  }
}
