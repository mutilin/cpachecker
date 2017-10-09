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
package org.sosy_lab.cpachecker.cpa.signal;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleState;
import org.sosy_lab.cpachecker.cpa.usage.UsageTreeNode;

public class SignalState implements LatticeAbstractState<SignalState>, CompatibleState, UsageTreeNode {

  private final String signalType;
  private final Set<String> sentSignals;
  private final Set<String> receivedSignals;

  public SignalState() {
    signalType = null;
    sentSignals = new HashSet<>();
    receivedSignals = new HashSet<>();
  }

  private SignalState(String pType, Set<String> pSent, Set<String> pReceived) {
    signalType = pType;
    sentSignals = new HashSet<>(pSent);
    receivedSignals = new HashSet<>(pReceived);
  }

  public SignalState sendSignal(String pType) {
    Set<String> result = new HashSet<>(sentSignals);
    result.add(pType);
    return new SignalState(null, result, receivedSignals);
  }

  public SignalState sendSignal() {
    return sendSignal(signalType);
  }

  public SignalState receiveSignal(String pType) {
    Set<String> result = new HashSet<>(receivedSignals);
    result.add(pType);
    return new SignalState(null, sentSignals, result);
  }

  public SignalState receiveSignal() {
    return receiveSignal(signalType);
  }

  public SignalState setSignalType(String pType) {
    return new SignalState(pType, sentSignals, receivedSignals);
  }

  @Override
  public int compareTo(CompatibleState pArg0) {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public boolean isCompatibleWith(CompatibleState pState) {
    return true;
  }

  @Override
  public CompatibleState prepareToStore() {
    return this;
  }

  @Override
  public UsageTreeNode getTreeNode() {
    return this;
  }

  @Override
  public SignalState join(SignalState pOther) {
    Set<String> newSentSignals = Sets.union(this.sentSignals, pOther.sentSignals);
    Set<String> newReceivedSignals = Sets.union(this.receivedSignals, pOther.receivedSignals);
    String newType = null;
    if (this.signalType == null && pOther.signalType != null) {
      newType = pOther.signalType;
    } else if (this.signalType != null && pOther.signalType == null) {
      newType = this.signalType;
    } else {
      assert false : "join of unsupported signal types: " + this.signalType + " and " + pOther.signalType;
    }
    return new SignalState(newType, newSentSignals, newReceivedSignals);
  }

  @Override
  public boolean isLessOrEqual(SignalState pOther) {
    return this.signalType == pOther.signalType &&
           pOther.sentSignals.containsAll(sentSignals) &&
           pOther.receivedSignals.containsAll(receivedSignals);
  }

  @Override
  public boolean cover(UsageTreeNode pNode) {
    Preconditions.checkArgument(pNode instanceof SignalState);
    return ((SignalState)pNode).isLessOrEqual(this);
  }

}
