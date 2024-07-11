// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.signal;

public abstract class StoreSignalIdEffect implements SignalEffect {

  public static class StoreLocalIdEffect extends StoreSignalIdEffect {
    StoreLocalIdEffect(String pName, int pId) {
      super(pName, pId);
    }

    @Override
    public SignalState apply(SignalState pState) {
      return pState.addLocalSignalId(varName, signalId);
    }

    @Override
    public String toString() {
      return "Store local " + varName + " as " + signalId;
    }
  }

  public static class StoreGlobalIdEffect extends StoreSignalIdEffect {
    StoreGlobalIdEffect(String pName, int pId) {
      super(pName, pId);
    }

    @Override
    public SignalState apply(SignalState pState) {
      pState.addGlobalSignalId(varName, signalId);
      return pState;
    }

    @Override
    public String toString() {
      return "Store global " + varName + " as " + signalId;
    }
  }

  protected String varName;
  protected int signalId;

  StoreSignalIdEffect(String pName, int pId) {
    varName = pName;
    signalId = pId;
  }
}
