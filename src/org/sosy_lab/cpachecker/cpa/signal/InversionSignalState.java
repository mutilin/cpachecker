// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.signal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleNode;

public class InversionSignalState extends SignalState {
  public InversionSignalState(
      ImmutableList<SignalDesc> pSignals,
      Map<String, Integer> pPreparedId,
      String pTo) {
    super(pSignals, pPreparedId, pTo);
  }

  public InversionSignalState() {
  }

  @Override
  protected List<SignalDesc> getNewList() {
    // store only last event
    return new ArrayList<>();
  }

  @Override
  protected InversionSignalState
      copy(ImmutableList<SignalDesc> pSignals, Map<String, Integer> pPreparedId, String pTo) {
    return new InversionSignalState(pSignals, pPreparedId, pTo);
  }

  @Override
  protected InversionSignalState copy() {
    return new InversionSignalState();
  }

  public static InversionSignalState createInitialState(SignalStatistics pStats) {
    initStats(pStats);
    return new InversionSignalState();
  }

  @Override
  protected SignalState resetIfNeeded() {
    // We need signals only once for inversion detection.
    // Do not store them further
    return copy(ImmutableList.of(), this.localIds, "");
  }

  @Override
  public CompatibleNode getCompatibleNode() {
    return new InversionSignalState(signals, ImmutableMap.of(), "");
  }
}