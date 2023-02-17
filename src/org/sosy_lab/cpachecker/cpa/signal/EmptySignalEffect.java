// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.signal;

public class EmptySignalEffect implements SignalEffect {

  private final static EmptySignalEffect INSTANCE = new EmptySignalEffect();

  public static EmptySignalEffect getInstance() {
    return INSTANCE;
  }

  @Override
  public SignalState apply(SignalState pState) {
    return pState.resetIfNeeded();
  }

}
