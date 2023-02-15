// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.signal;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.ForOverride;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleNode;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleState;
import org.sosy_lab.cpachecker.cpa.usage.storage.Delta;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.Pair;

public class SignalState
    implements LatticeAbstractState<SignalState>, CompatibleNode, Delta<SignalState> {

  public enum Action {
    SEND,
    RECEIVE,
  }

  private static SignalStatistics stats;
  private final static int LIMIT = 10;

  private final ImmutableList<Pair<Integer, Action>> signals;
  // Global variables
  private final static Map<String, Integer> globalIds = new TreeMap<>();
  private final Map<String, Integer> localIds;

  public SignalState() {
    this(ImmutableList.of(), new TreeMap<>());
  }

  protected SignalState(
      ImmutableList<Pair<Integer, Action>> pSignals,
      Map<String, Integer> pPreparedId) {
    signals = pSignals;
    localIds = pPreparedId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(signals, localIds);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof SignalState)) {
      return false;
    }
    SignalState other = (SignalState) obj;
    return Objects.equals(signals, other.signals) && Objects.equals(localIds, other.localIds);
  }

  private SignalState addSignal(String varName, Action pAction) {
    if (signals.size() > LIMIT) {
      return this;
    }
    if (!containsId(varName)) {
      // TODO complicated cases with resend received signals
      // Skip for now
      return this;
    }
    Integer id = getId(varName);
    Pair<Integer, Action> newItem = Pair.of(id, pAction);
    List<Pair<Integer, Action>> list = getNewList();
    if (!addSignalTo(list, newItem)) {
      return this;
    }
    Map<String, Integer> newIds = new TreeMap<>(localIds);
    newIds.remove(varName);
    return copy(ImmutableList.copyOf(list), newIds);
  }

  @ForOverride
  protected List<Pair<Integer, Action>> getNewList() {
    return new ArrayList<>(signals);
  }

  @ForOverride
  protected SignalState
      copy(ImmutableList<Pair<Integer, Action>> pSignals, Map<String, Integer> pPreparedId) {
    return new SignalState(pSignals, pPreparedId);
  }

  @ForOverride
  protected SignalState copy() {
    return new SignalState();
  }

  private boolean addSignalTo(List<Pair<Integer, Action>> pList, Pair<Integer, Action> newItem) {
    if (!pList.isEmpty() && pList.contains(newItem)) {
      // TODO skip cases [(0, send), (1, receive), ...]
      // Avoid send in loops
      return false;
    }
    pList.add(newItem);
    return true;
  }

  private static void initStats(SignalStatistics pStats) {
    if (stats == null) {
      // Only in tests the method is called several times
      stats = pStats;
    }
  }

  public static SignalState createInitialState(SignalStatistics pStats) {
    initStats(pStats);
    return new SignalState();
  }

  SignalState addSendSignal(String varName) {
    stats.sendSignals.inc();
    return addSignal(varName, Action.SEND);
  }

  SignalState addReceiveSignal(String varName) {
    stats.receiveSignals.inc();
    return addSignal(varName, Action.RECEIVE);
  }

  void addGlobalSignalId(String varName, int id) {
    globalIds.put(varName, id);
    stats.createdGlobalSignals.inc();
  }

  SignalState addLocalSignalId(String varName, int id) {
    Map<String, Integer> newIds = new TreeMap<>(localIds);
    newIds.put(varName, id);
    stats.createdLocalSignals.inc();
    return copy(signals, newIds);
  }

  @Override
  public int compareTo(CompatibleState pArg0) {
    SignalState other = (SignalState) pArg0;
    int result = this.signals.size() - other.signals.size();
    if (result != 0) {
      return result;
    }
    for (int i = 0; i < signals.size(); i++) {
      Pair<Integer, Action> pair1 = signals.get(i);
      Pair<Integer, Action> pair2 = other.signals.get(i);
      result = pair1.getFirst().compareTo(pair2.getFirst());
      if (result != 0) {
        return result;
      }
      result = pair1.getSecond().compareTo(pair2.getSecond());
      if (result != 0) {
        return result;
      }
    }
    return 0;
  }

  @Override
  public String toString() {
    return signals.toString() + " :: " + localIds;
  }

  @Override
  public boolean cover(CompatibleNode pNode) {
    return ((SignalState) pNode).isLessOrEqual(this);
  }

  @Override
  public SignalState join(SignalState pOther) throws CPAException, InterruptedException {
    throw new UnsupportedOperationException("Join is not supported for SignalCPA");
  }

  @Override
  public boolean isLessOrEqual(SignalState pOther) {
    try {
      // TODO Race in UsageExtractor
      // stats.lessOrEquals.start();
      if (signals.size() > pOther.signals.size()) {
        return false;
      }

      boolean res = pOther.localIds.entrySet().containsAll(this.localIds.entrySet());
      if (!res) {
        return false;
      }

      if (signals.size() == pOther.signals.size()) {
        return this.equals(pOther);
      } else {
        List<Pair<Integer, Action>> prefix = pOther.signals.subList(0, signals.size());
        if (prefix.equals(signals)) {
          return true;
        }
        List<Pair<Integer, Action>> suffix =
            pOther.signals.subList(pOther.signals.size() - signals.size(), pOther.signals.size());
        return suffix.equals(signals);
      }
    } finally {
      // stats.lessOrEquals.stop();
    }
  }

  public List<Pair<Integer, Action>> getSignals() {
    return signals;
  }

  public SignalState expand(SignalState root) {
    if (signals.isEmpty()) {
      return root;
    }
    boolean changed = false;
    List<Pair<Integer, Action>> list = root.getNewList();
    for (Pair<Integer, Action> pair : this.signals) {
      if (addSignalTo(list, pair)) {
        changed = true;
      }
    }
    if (changed) {
      return copy(ImmutableList.copyOf(list), root.localIds);
    }
    return root;
  }

  public SignalState reduce() {
    return copy();
  }

  @Override
  public SignalState getDeltaBetween(CompatibleNode pOther) {
    SignalState root = (SignalState) pOther;
    assert this.signals.isEmpty();
    assert this.localIds.isEmpty();
    return root;
  }

  @Override
  public SignalState apply(SignalState pState) {
    return pState.expand(pState);
  }

  @Override
  public boolean covers(Delta<SignalState> pDelta) {
    return ((SignalState) pDelta).isLessOrEqual(this);
  }

  @Override
  public SignalState add(Delta<SignalState> pDelta) {
    return ((SignalState) pDelta).expand(this);
  }

  private boolean containsId(String varName) {
    return localIds.containsKey(varName) || globalIds.containsKey(varName);
  }

  private Integer getId(String varName) {
    if (localIds.containsKey(varName)) {
      return localIds.get(varName);
    }
    return globalIds.get(varName);
  }

  public static class InversionSignalState extends SignalState {

    public InversionSignalState(
        ImmutableList<Pair<Integer, Action>> pSignals,
        Map<String, Integer> pPreparedId) {
      super(pSignals, pPreparedId);
    }

    public InversionSignalState() {
    }

    @Override
    protected List<Pair<Integer, Action>> getNewList() {
      // store only last event
      return new ArrayList<>();
    }

    @Override
    protected InversionSignalState
        copy(ImmutableList<Pair<Integer, Action>> pSignals, Map<String, Integer> pPreparedId) {
      return new InversionSignalState(pSignals, pPreparedId);
    }

    @Override
    @ForOverride
    protected InversionSignalState copy() {
      return new InversionSignalState();
    }

    public static InversionSignalState createInitialState(SignalStatistics pStats) {
      initStats(pStats);
      return new InversionSignalState();
    }
  }
}
