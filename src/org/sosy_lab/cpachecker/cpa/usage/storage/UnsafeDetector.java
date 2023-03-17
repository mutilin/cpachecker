// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.storage;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.sosy_lab.cpachecker.cpa.lock.DeadLockState.DeadLockTreeNode;
import org.sosy_lab.cpachecker.cpa.lock.LockIdentifier;
import org.sosy_lab.cpachecker.cpa.signal.InversionSignalState;
import org.sosy_lab.cpachecker.cpa.signal.SignalState;
import org.sosy_lab.cpachecker.cpa.signal.SignalState.Action;
import org.sosy_lab.cpachecker.cpa.signal.SignalState.SignalDesc;
import org.sosy_lab.cpachecker.cpa.thread.SimpleThreadState;
import org.sosy_lab.cpachecker.cpa.thread.ThreadState;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo.Access;
import org.sosy_lab.cpachecker.util.Pair;

public class UnsafeDetector {

  public enum UnsafeMode {
    RACE,
    DEADLOCKCIRCULAR,
    DEADLOCKDISPATCH,
    DEADLOCKSIGNAL,
    SIGNALINVERSION,
  }

  private final UsageConfiguration config;
  private final UnrefinedUsagePointSet anyIds;

  public UnsafeDetector(UsageConfiguration pConfig, UnrefinedUsagePointSet pSet) {
    config = pConfig;
    anyIds = pSet;
  }

  public boolean isUnsafe(AbstractUsagePointSet set) {
    if (set instanceof RefinedUsagePointSet) {
      return true;
    }
    return isUnsafe((UnrefinedUsagePointSet)set);
  }

  private boolean isUnsafe(UnrefinedUsagePointSet set) {

    return isUnsafe(getTopPoints(set));
  }

  private NavigableSet<UsagePoint> getTopPoints(UnrefinedUsagePointSet set) {
    NavigableSet<UsagePoint> points;
    if (anyIds.size() == 0) {
      points = set.getTopUsages();
    } else {
      points = new TreeSet<>(set.getTopUsages());
      points.addAll(anyIds.getTopUsages());
    }
    return points;
  }

  public Pair<UsageInfo, UsageInfo> getUnsafePair(AbstractUsagePointSet set) {
    assert isUnsafe(set);

    if (set instanceof RefinedUsagePointSet) {
      return ((RefinedUsagePointSet)set).getUnsafePair();
    } else {
      UnrefinedUsagePointSet unrefinedSet = (UnrefinedUsagePointSet) set;
      Pair<UsagePoint, UsagePoint> result = getUnsafePair(getTopPoints(unrefinedSet));

      assert result != null;

      UsageInfoSet uset1 = unrefinedSet.getUsageInfo(result.getFirst());
      if (uset1 == null) {
        // Likely it is from anyIds
        uset1 = anyIds.getUsageInfo(result.getFirst());
        assert (uset1 != null);
      }

      UsageInfoSet uset2 = unrefinedSet.getUsageInfo(result.getSecond());
      if (uset2 == null) {
        // Likely it is from anyIds
        uset2 = anyIds.getUsageInfo(result.getSecond());
        assert (uset2 != null);
      }

      return Pair.of(uset1.getOneExample(), uset2.getOneExample());
    }
  }

  private boolean isUnsafe(NavigableSet<UsagePoint> points) {
    for (UsagePoint point1 : points) {
      for (UsagePoint point2 : points) {
        if (isUnsafePair(point1, point2)) {
          return true;
        }
      }
    }
    return false;
  }

  private Pair<UsagePoint, UsagePoint> getUnsafePair(NavigableSet<UsagePoint> set) {

    Pair<UsagePoint, UsagePoint> unsafePair = null;

    for (UsagePoint point1 : set) {
      // Operation is not commutative, not to optimize
      for (UsagePoint point2 : set) {
        if (isUnsafePair(point1, point2)) {
          Pair<UsagePoint, UsagePoint> newUnsafePair = Pair.of(point1, point2);
          if (unsafePair == null || compare(newUnsafePair, unsafePair) < 0) {
            unsafePair = newUnsafePair;
          }
        }
      }
    }
    // If we can not find an unsafe here, fail
    return unsafePair;
  }

  private int compare(Pair<UsagePoint, UsagePoint> pair1, Pair<UsagePoint, UsagePoint> pair2) {
    int result = 0;
    UsagePoint point1 = pair1.getFirst();
    UsagePoint point2 = pair1.getSecond();
    UsagePoint oPoint1 = pair2.getFirst();
    UsagePoint oPoint2 = pair2.getSecond();

    boolean isEmpty = point1.isEmpty() && point2.isEmpty();
    boolean otherIsEmpty = oPoint1.isEmpty() && oPoint2.isEmpty();
    if (isEmpty && !otherIsEmpty) {
      return 1;
    }
    if (!isEmpty && otherIsEmpty) {
      return -1;
    }
    result += point1.compareTo(oPoint1);
    result += point2.compareTo(oPoint2);
    return result;
  }

  public boolean isUnsafePair(UsagePoint point1, UsagePoint point2) {
    if (point1.isCompatible(point2)) {
      switch (config.getUnsafeMode()) {
        case RACE:
          return isRace(point1, point2);

        case DEADLOCKDISPATCH:
          return isDeadlockDispatch(point1, point2);

        case DEADLOCKCIRCULAR:
          return isDeadlockCircular(point1, point2);

        case DEADLOCKSIGNAL:
          return isDeadlockSignal(point1, point2);

        case SIGNALINVERSION:
          return isSignalInversion(point1, point2);

        default:
          throw new UnsupportedOperationException("Unknown mode: " + config.getUnsafeMode());
      }
    }
    return false;
  }

  private boolean isRace(UsagePoint point1, UsagePoint point2) {
    if ((point1.getAccess().isWrite() || point2.getAccess().isWrite())
        && !(point1.getAccess().isAtomic() && point2.getAccess().isAtomic())) {
      if (config.ignoreEmptyLockset() && point1.isEmpty() && point2.isEmpty()) {
        return false;
      }
      return true;
    }
    return false;
  }

  private boolean isDeadlockDispatch(UsagePoint point1, UsagePoint point2) {
    LockIdentifier intLock = LockIdentifier.of(checkNotNull(config.getIntLockName()));
    DeadLockTreeNode node1 = point1.get(DeadLockTreeNode.class);
    DeadLockTreeNode node2 = point2.get(DeadLockTreeNode.class);

    if (node2.contains(intLock) && !node1.contains(intLock)) {
      for (LockIdentifier lock1 : node1) {
        int index1 = node2.indexOf(lock1);
        int index2 = node2.indexOf(intLock);

        if (index1 > index2) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isDeadlockCircular(UsagePoint point1, UsagePoint point2) {
    //Deadlocks
    DeadLockTreeNode node1 = point1.get(DeadLockTreeNode.class);
    DeadLockTreeNode node2 = point2.get(DeadLockTreeNode.class);

    for (LockIdentifier lock1 : node1) {
      for (LockIdentifier lock2 : node2) {
        int index1 = node1.indexOf(lock1);
        int index2 = node1.indexOf(lock2);
        int otherIndex1 = node2.indexOf(lock1);
        int otherIndex2 = node2.indexOf(lock2);
        if (otherIndex1 >= 0 && index2 >= 0 &&
            ((index1 > index2 && otherIndex1 < otherIndex2) ||
             (index1 < index2 && otherIndex1 > otherIndex2))) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isDeadlockSignal(UsagePoint point1, UsagePoint point2) {
    // The last signals in points should be the same
    // TODO refactor to check only last signals

    SignalState node1 = point1.get(SignalState.class);
    SignalState node2 = point2.get(SignalState.class);

    List<SignalDesc> signals1 = node1.getSignals();
    List<SignalDesc> signals2 = node2.getSignals();

    for (int i = 0; i < signals1.size(); i++) {
      SignalDesc signal1 = signals1.get(i);
      if (signal1.direction == Action.SEND) {
        continue;
      }
      for (int j = i + 1; j < signals1.size(); j++) {
        SignalDesc signal2 = signals1.get(j);
        if (signal2.direction == Action.RECEIVE) {
          continue;
        }
        if (hasDeadlockOnSignals(signals2, signal1.num, signal2.num)) {
          return true;
        }
      }
    }
    return false;

  }

  // Note, receive and send are from the point of first thread view
  // We need to find them as reverted value (send<->receive) in the second thread
  private boolean
      hasDeadlockOnSignals(List<SignalDesc> signals, int receive, int send) {

    for (int i = 0; i < signals.size(); i++) {
      SignalDesc signal1 = signals.get(i);
      // This thread receives the signal, which was _sent_
      if (signal1.direction == Action.SEND
          || !sameSignal(signal1.num, send)) {
        continue;
      }
      for (int j = i + 1; j < signals.size(); j++) {
        SignalDesc signal2 = signals.get(j);
        // This thread sends the signal, which was _received_
        if (signal2.direction == Action.SEND && sameSignal(signal2.num, receive)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean sameSignal(Integer sig1, Integer sig2) {
    // -1 means 'any' signal
    final int ANY = -1;
    final int UNKNOWN = -2;
    return sig1.equals(sig2) || sig1 == ANY || sig2 == ANY || sig1 == UNKNOWN || sig2 == UNKNOWN;
  }

  private boolean isSignalInversion(UsagePoint pPoint1, UsagePoint pPoint2) {
    SignalState state1 = pPoint1.get(InversionSignalState.class);
    SignalState state2 = pPoint2.get(InversionSignalState.class);

    if (state1 == null || state2 == null) {
      throw new UnsupportedOperationException(
          "Signal inversion check need SignalCPA with inversed states (cpa.signal.deadlockState=false)");
    }

    List<SignalDesc> sState1 = state1.getSignals();
    List<SignalDesc> sState2 = state2.getSignals();

    assert sState1.size() == 1;
    assert sState2.size() == 1;

    int sig1 = sState1.get(0).num;
    int sig2 = sState2.get(0).num;

    String target1 = sState1.get(0).target;
    String target2 = sState2.get(0).target;

    if (sameSignal(sig1, sig2)) {
      ThreadState tState1 = pPoint1.get(ThreadState.class);
      ThreadState tState2 = pPoint2.get(ThreadState.class);

      if (tState1 == null) {
        tState1 = pPoint1.get(SimpleThreadState.class);
        tState2 = pPoint2.get(SimpleThreadState.class);
      }

      String func1 = tState1.getCurrentThread();
      String func2 = tState2.getCurrentThread();

      if (!theSameThread(func1, target2) || !theSameThread(func2, target1)) {
        // different src/dst
        return false;
      }

      Access access1 = pPoint1.getAccess();
      Access access2 = pPoint2.getAccess();

      if (access1 == Access.SEND && access2 == Access.RECEIVE) {
        return lowerPriority(
            tState1.getPriority(),
            tState2.getPriority(),
            config.isRevertedPriority());
      } else if (access2 == Access.SEND && access1 == Access.RECEIVE) {
        return lowerPriority(
            tState2.getPriority(),
            tState1.getPriority(),
            config.isRevertedPriority());
      }
    }
    return false;
  }

  private static boolean theSameThread(String pThread, String pTarget) {
    return pTarget.isEmpty() || pThread.isEmpty() || pTarget.equals(pThread);
  }

  private static boolean lowerPriority(int prio1, int prio2, boolean reverted) {
    if (reverted) {
      return (0 <= prio2 && prio2 < prio1) || (prio2 == -1 && prio1 > 0) || (prio1 == -1);
    } else {
      return (0 <= prio1 && prio1 < prio2) || (prio1 == -1 && prio2 > 0) || (prio2 == -1);
    }
  }

 }
