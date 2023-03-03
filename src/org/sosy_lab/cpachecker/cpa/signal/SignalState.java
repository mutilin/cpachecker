// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.signal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.stanford.CVC4.UnsafeInterruptException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleNode;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleState;
import org.sosy_lab.cpachecker.cpa.usage.storage.Delta;
import org.sosy_lab.cpachecker.cpa.usage.storage.GenericDelta;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class SignalState
    implements LatticeAbstractState<SignalState>, CompatibleNode, Delta<CompatibleNode> {

  public enum Action {
    SEND,
    RECEIVE,
  }

  public static class SignalDesc {
    public final int num;
    public final Action direction;
    public final String target;

    public SignalDesc(int pNum, Action pDirect, String pTarget) {
      num = pNum;
      direction = pDirect;
      target = pTarget;
    }

    @Override
    public int hashCode() {
      return Objects.hash(direction, num, target);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (!(obj instanceof SignalDesc)) {
        return false;
      }
      SignalDesc other = (SignalDesc) obj;
      return direction == other.direction
          && num == other.num
          && Objects.equals(target, other.target);
    }
  }

  private static SignalStatistics stats;
  private final static int LIMIT = 10;

  protected final ImmutableList<SignalDesc> signals;
  // Global variables
  private final static Map<String, Integer> globalIds = new TreeMap<>();
  protected final Map<String, Integer> localIds;
  protected final String targetTo;

  public SignalState() {
    this(ImmutableList.of(), new TreeMap<>(), "");
  }

  protected SignalState(
      ImmutableList<SignalDesc> pSignals,
      Map<String, Integer> pPreparedId,
      String pTo) {
    signals = pSignals;
    localIds = pPreparedId;
    targetTo = pTo;
  }

  @Override
  public int hashCode() {
    return Objects.hash(signals, localIds, targetTo);
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
    return Objects.equals(signals, other.signals)
        && Objects.equals(targetTo, other.targetTo)
        && Objects.equals(localIds, other.localIds);
  }

  private SignalState addSignal(String varName, Action pAction) {
    if (signals.size() > LIMIT) {
      return this;
    }
    Integer id;
    if (!containsId(varName)) {
      // TODO complicated cases with resend received signals
      // Unknown signal
      id = -2;
    } else {
      id = getId(varName);
    }
    SignalDesc newItem = new SignalDesc(id, pAction, targetTo);
    List<SignalDesc> list = getNewList();
    if (!addSignalTo(list, newItem)) {
      return this;
    }
    Map<String, Integer> newIds = new TreeMap<>(localIds);
    newIds.remove(varName);
    // Reset targetTo
    return copy(ImmutableList.copyOf(list), newIds, "");
  }

  protected List<SignalDesc> getNewList() {
    return new ArrayList<>(signals);
  }

  protected SignalState
      copy(
          ImmutableList<SignalDesc> pSignals,
          Map<String, Integer> pPreparedId,
          String pTo) {
    return new SignalState(pSignals, pPreparedId, pTo);
  }

  protected SignalState copy() {
    return new SignalState();
  }

  protected SignalState resetIfNeeded() {
    return this;
  }

  private boolean addSignalTo(List<SignalDesc> pList, SignalDesc newItem) {
    if (!pList.isEmpty() && pList.contains(newItem)) {
      // TODO skip cases [(0, send), (1, receive), ...]
      // Avoid send in loops
      return false;
    }
    pList.add(newItem);
    return true;
  }

  protected static void initStats(SignalStatistics pStats) {
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
    return copy(signals, newIds, this.targetTo);
  }

  SignalState addTargetTo(@SuppressWarnings("unused") String pName) {
    assert this.targetTo.isEmpty();
    return copy(signals, localIds, pName);
  }

  @Override
  public int compareTo(CompatibleState pArg0) {
    SignalState other = (SignalState) pArg0;
    int result = this.signals.size() - other.signals.size();
    if (result != 0) {
      return result;
    }
    for (int i = 0; i < signals.size(); i++) {
      SignalDesc pair1 = signals.get(i);
      SignalDesc pair2 = other.signals.get(i);
      result = pair1.num - pair2.num;
      if (result != 0) {
        return result;
      }
      result = pair1.direction.compareTo(pair2.direction);
      if (result != 0) {
        return result;
      }
    }
    return targetTo.compareTo(other.targetTo);
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

      // Unknown covers concrete
      if (this.targetTo.isEmpty() && !pOther.targetTo.isEmpty()) {
        return false;
      }

      if (signals.size() == pOther.signals.size()) {
        return this.equals(pOther);
      } else {
        List<SignalDesc> prefix = pOther.signals.subList(0, signals.size());
        if (prefix.equals(signals)) {
          return true;
        }
        List<SignalDesc> suffix =
            pOther.signals.subList(pOther.signals.size() - signals.size(), pOther.signals.size());
        return suffix.equals(signals);
      }
    } finally {
      // stats.lessOrEquals.stop();
    }
  }

  public List<SignalDesc> getSignals() {
    return signals;
  }

  public SignalState expand(SignalState root) {
    if (signals.isEmpty()) {
      return root;
    }
    boolean changed = false;
    List<SignalDesc> list = root.getNewList();
    for (SignalDesc pair : this.signals) {
      if (addSignalTo(list, pair)) {
        changed = true;
      }
    }
    if (changed) {
      return copy(ImmutableList.copyOf(list), root.localIds, root.targetTo);
    }
    return root;
  }

  public SignalState reduce() {
    return copy();
  }

  @Override
  public Delta<CompatibleNode> getDeltaBetween(CompatibleNode pOther) {
    SignalState root = (SignalState) pOther;
    assert this.signals.isEmpty();
    if (root.signals.isEmpty()) {
      return GenericDelta.getInstance();
    }
    return root;
  }

  @Override
  public CompatibleNode apply(CompatibleNode pState) {
    return ((SignalState) pState).expand(this);
  }

  @Override
  public boolean covers(Delta<CompatibleNode> pDelta) {
    if (pDelta instanceof SignalState) {
      return ((SignalState) pDelta).isLessOrEqual(this);
    } else if (pDelta == GenericDelta.getInstance()) {
      return true;
    } else {
      throw new UnsafeInterruptException("Unknown delta " + pDelta.getClass());
    }
  }

  @Override
  public Delta<CompatibleNode> add(Delta<CompatibleNode> pDelta) {
    if (pDelta instanceof SignalState) {
      return ((SignalState)pDelta).expand(this);
    } else if (pDelta == GenericDelta.getInstance()) {
      return this;
    } else {
      throw new UnsafeInterruptException("Unknown delta " + pDelta.getClass());
    }
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

  @Override
  public CompatibleNode getCompatibleNode() {
    return new SignalState(signals, ImmutableMap.of(), "");
  }
}
