// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.storage;

import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usage.UsageState;

public class UnrefinedUsagePointSet implements AbstractUsagePointSet {
  private final NavigableSet<UsagePoint> topUsages;
  private final Map<UsagePoint, Set<UsageInfo>> usageInfoSets;

  public UnrefinedUsagePointSet() {
    topUsages = new ConcurrentSkipListSet<>();
    usageInfoSets = new ConcurrentSkipListMap<>();
  }

  public void add(UsageInfo newInfo) {
    Set<UsageInfo> targetSet;
    UsagePoint newPoint = newInfo.getUsagePoint();
    if (usageInfoSets.containsKey(newPoint)) {
      targetSet = usageInfoSets.get(newPoint);
    } else {
      targetSet = new ConcurrentSkipListSet<>();
      // It is possible, that someone place the set after check
      Set<UsageInfo> present = usageInfoSets.putIfAbsent(newPoint, targetSet);
      if (present != null) {
        targetSet = present;
      }
    }
    add(newPoint);
    targetSet.add(newInfo);
  }

  private void add(UsagePoint newPoint) {
    // Put newPoint in the right place in tree
    for (UsagePoint point : topUsages) {
      if (point.equals(newPoint)) {
        // Unknown problem with contains:
        // for skipList it somehow returns false for an element in the set
        return;
      }
    }
    Iterator<UsagePoint> iterator = topUsages.iterator();
    while (iterator.hasNext()) {
      UsagePoint point = iterator.next();
      if (newPoint.covers(point)) {
        iterator.remove();
        newPoint.addCoveredUsage(point);
      } else if (point.covers(newPoint)) {
        point.addCoveredUsage(newPoint);
        return;
      }
    }
    topUsages.add(newPoint);
  }

  public Set<UsageInfo> getUsageInfo(UsagePoint point) {
    return usageInfoSets.get(point);
  }

  @Override
  public int size() {
    int result = 0;

    for (Set<UsageInfo> value : usageInfoSets.values()) {
      result += value.size();
    }

    return result;
  }

  public void reset() {
    topUsages.clear();
    usageInfoSets.clear();
  }

  public void remove(UsageState pUstate) {
    //Attention! Use carefully. May not work
    for (UsagePoint point : new TreeSet<>(usageInfoSets.keySet())) {
      Set<UsageInfo> uset = usageInfoSets.get(point);

      Iterator<UsageInfo> iterator = uset.iterator();
      while (iterator.hasNext()) {
        UsageInfo uinfo = iterator.next();
        AbstractState keyState = uinfo.getKeyState();
        assert (keyState != null);
        if (UsageState.get(keyState).equals(pUstate)) {
          iterator.remove();
        }
      }

      if (uset.isEmpty()) {
        usageInfoSets.remove(point);
      }
      // May be two usages related to the same state. This is abstractState !
    }
  }

  public Iterator<UsagePoint> getPointIterator() {
    return new TreeSet<>(topUsages).iterator();
  }

  public Iterator<UsagePoint> getPointIteratorFrom(UsagePoint p) {
    return new TreeSet<>(topUsages.tailSet(p)).iterator();
  }

  public int getNumberOfTopUsagePoints() {
    return topUsages.size();
  }

  public void remove(UsagePoint currentUsagePoint) {
    usageInfoSets.remove(currentUsagePoint);
    topUsages.remove(currentUsagePoint);
    currentUsagePoint.getCoveredUsages().forEach(this::add);
  }

  NavigableSet<UsagePoint> getTopUsages() {
    return topUsages;
  }
}
