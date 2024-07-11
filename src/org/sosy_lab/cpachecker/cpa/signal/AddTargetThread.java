// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.signal;

public class AddTargetThread implements SignalEffect {
  private final String target;

  public AddTargetThread(String pTo) {
    target = pTo;
  }

  @Override
  public SignalState apply(SignalState pState) {
    return pState.addTargetTo(target);
  }
}
