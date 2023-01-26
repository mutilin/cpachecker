// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.signal;

public abstract class AccessSignalEffect implements SignalEffect {

  public static class SendSignalEffect extends AccessSignalEffect {
    public SendSignalEffect(String pId) {
      super(pId);
    }

    @Override
    public SignalState apply(SignalState pState) {
      return pState.addSendSignal(varName);
    }

    @Override
    public String toString() {
      return "Send " + varName;
    }
  }

  public static class ReceiveSignalEffect extends AccessSignalEffect {
    public ReceiveSignalEffect(String pId) {
      super(pId);
    }

    @Override
    public SignalState apply(SignalState pState) {
      return pState.addReceiveSignal(varName);
    }

    @Override
    public String toString() {
      return "Receive " + varName;
    }
  }

  protected final String varName;

  public AccessSignalEffect(String pId) {
    varName = pId;
  }

}
