// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.storage;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Preconditions;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usage.UsageState;
import org.sosy_lab.cpachecker.cpa.usage.refinement.RefinementResult;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.AnyIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class UsageContainer {
  private final NavigableMap<AbstractIdentifier, UnrefinedUsagePointSet> unrefinedIds;
  private final NavigableMap<AbstractIdentifier, Pair<UsageInfo, UsageInfo>> refinedIds;
  private final UnrefinedUsagePointSet anyIds;

  private Map<AbstractIdentifier, Pair<UsageInfo, UsageInfo>> stableUnsafes = new TreeMap<>();

  private final UnsafeDetector detector;

  private Set<AbstractIdentifier> falseUnsafes;
  private Set<AbstractIdentifier> initialUnsafes;

  // Only for statistics
  private int initialUsages = 0;
  private boolean usagesCalculated = false;

  private final LogManager logger;

  private final StatTimer resetTimer = new StatTimer("Time for reseting unsafes");
  private final StatTimer unsafeDetectionTimer = new StatTimer("Time for unsafe detection");
  private final StatTimer searchingInCachesTimer = new StatTimer("Time for searching in caches");
  private final StatTimer addingToSetTimer = new StatTimer("Time for adding to usage point set");
  private final StatCounter sharedVariables =
      new StatCounter("Number of detected shared variables");

  private boolean oneTotalIteration = false;

  public UsageContainer(UsageConfiguration pConfig, LogManager l) {
    unrefinedIds = new ConcurrentSkipListMap<>();
    refinedIds = new TreeMap<>();
    falseUnsafes = new TreeSet<>();
    anyIds = new UnrefinedUsagePointSet();
    logger = l;
    detector = new UnsafeDetector(pConfig, anyIds);
  }

  public void add(UsageInfo pUsage) {
    AbstractIdentifier id = pUsage.getId();
    if (id instanceof StructureIdentifier) {
      id = ((StructureIdentifier) id).toStructureFieldIdentifier();
    }

    UnrefinedUsagePointSet uset;

    assert (!oneTotalIteration || unrefinedIds.containsKey(id));

    if (id == AnyIdentifier.getInstance()) {
      uset = anyIds;
    } else if (!unrefinedIds.containsKey(id)) {
      uset = new UnrefinedUsagePointSet();
      // It is possible, that someone placed the set after check
      UnrefinedUsagePointSet present = unrefinedIds.putIfAbsent(id, uset);
      if (present != null) {
        uset = present;
      }
    } else {
      uset = unrefinedIds.get(id);
    }
    // searchingInCachesTimer.stop();

    // addingToSetTimer.start();
    uset.add(pUsage);
    // addingToSetTimer.stop();
  }

  private void calculateUnsafesIfNecessary() {
    if (!usagesCalculated) {
      unsafeDetectionTimer.start();

      Iterator<Entry<AbstractIdentifier, UnrefinedUsagePointSet>> iterator =
          unrefinedIds.entrySet().iterator();
      while (iterator.hasNext()) {
        Entry<AbstractIdentifier, UnrefinedUsagePointSet> entry = iterator.next();
        UnrefinedUsagePointSet tmpList = entry.getValue();
        if (detector.isUnsafe(tmpList)) {
          if (!oneTotalIteration) {
            initialUsages += tmpList.size();
          }
        } else {
          if (!oneTotalIteration) {
            sharedVariables.inc();
          }
          iterator.remove();
        }
      }

      if (!oneTotalIteration) {
        initialUnsafes = new TreeSet<>(unrefinedIds.keySet());
      } else {
        falseUnsafes = new TreeSet<>(initialUnsafes);
        falseUnsafes.removeAll(unrefinedIds.keySet());
        falseUnsafes.removeAll(refinedIds.keySet());
      }

      usagesCalculated = true;
      saveStableUnsafes();
      unsafeDetectionTimer.stop();
    }
  }

  public Set<AbstractIdentifier> getFalseUnsafes() {
    return falseUnsafes;
  }

  public Iterator<AbstractIdentifier> getUnrefinedUnsafeIterator() {
    // New set to avoid concurrent modification exception
    Set<AbstractIdentifier> result = new TreeSet<>(unrefinedIds.keySet());
    return result.iterator();
  }

  public int getProcessedUnsafeSize() {
    return falseUnsafes.size() + refinedIds.size();
  }

  public UnsafeDetector getUnsafeDetector() {
    return detector;
  }

  public void resetUnrefinedUnsafes() {
    resetTimer.start();
    oneTotalIteration = true;
    unrefinedIds.forEach((k, v) -> v.reset());
    anyIds.reset();
    usagesCalculated = false;
    logger.log(Level.FINE, "Unsafes are reseted");
    resetTimer.stop();
  }

  public void removeState(final UsageState pUstate) {
    unrefinedIds.forEach((id, uset) -> uset.remove(pUstate));
    anyIds.remove(pUstate);
    logger.log(
        Level.ALL,
        "All unsafes related to key state " + pUstate + " were removed from reached set");
  }

  public UnrefinedUsagePointSet getUnrefinedUsages(AbstractIdentifier id) {
    // Not sure, it should be used
    // Likely, here should be only normal identifiers
    assert (id != AnyIdentifier.getInstance());
    assert unrefinedIds.containsKey(id);
    return unrefinedIds.get(id);
  }

  public void setAsFalseUnsafe(AbstractIdentifier id) {
    assert (id != AnyIdentifier.getInstance());
    unrefinedIds.remove(id);
  }

  public void setAsRefined(AbstractIdentifier id, RefinementResult result) {
    Preconditions.checkArgument(
        result.isTrue(), "Result is not true, can not set the set as refined");
    checkArgument(
        detector.isUnsafe(getUnrefinedUsages(id)),
        "Refinement is successful, but the unsafe is absent for identifier %s",
        id);

    UsageInfo firstUsage = result.getTrueRace().getFirst();
    UsageInfo secondUsage = result.getTrueRace().getSecond();

    refinedIds.put(id, Pair.of(firstUsage, secondUsage));
    unrefinedIds.remove(id);
  }

  public void printUsagesStatistics(StatisticsWriter out) {
    int unsafeSize = unrefinedIds.size() + refinedIds.size();
    StatInt topUsagePoints = new StatInt(StatKind.SUM, "Total amount of unrefined usage points");
    StatInt unrefinedUsages = new StatInt(StatKind.SUM, "Total amount of unrefined usages");
    StatInt refinedUsages = new StatInt(StatKind.SUM, "Total amount of refined usages");
    StatCounter failedUsages = new StatCounter("Total amount of failed usages");

    final int generalUnrefinedSize = unrefinedIds.size();
    for (UnrefinedUsagePointSet uset : unrefinedIds.values()) {
      unrefinedUsages.setNextValue(uset.size());
      topUsagePoints.setNextValue(uset.getNumberOfTopUsagePoints());
    }

    int generalRefinedSize = 0;
    int generalFailedSize = 0;

    for (Pair<UsageInfo, UsageInfo> pair : refinedIds.values()) {
      UsageInfo firstUsage = pair.getFirst();
      UsageInfo secondUsage = pair.getSecond();

      if (firstUsage.isLooped()) {
        failedUsages.inc();
        generalFailedSize++;
      }
      if (secondUsage.isLooped() && !firstUsage.equals(secondUsage)) {
        failedUsages.inc();
      }
      if (!firstUsage.isLooped() && !secondUsage.isLooped()) {
        generalRefinedSize++;
        refinedUsages.setNextValue(2);
      }
    }

    out.spacer()
        .put(sharedVariables)
        .put("Total amount of unsafes", unsafeSize)
        .put("Initial amount of unsafes (before refinement)", unsafeSize + falseUnsafes.size())
        .put("Initial amount of usages (before refinement)", initialUsages)
        .put("Initial amount of refined false unsafes", falseUnsafes.size())
        .put("Initial amount of unknown usages", anyIds.size())
        .put("Total amount of unrefined unsafes", generalUnrefinedSize)
        .put(topUsagePoints)
        .put(unrefinedUsages)
        .put("Total amount of refined unsafes", generalRefinedSize)
        .put(refinedUsages)
        .put("Total amount of failed unsafes", generalFailedSize)
        .put(failedUsages)
        .put(resetTimer)
        .put(unsafeDetectionTimer)
        .put(searchingInCachesTimer)
        .put(addingToSetTimer);
  }

  public String getUnsafeStatus() {
    return unrefinedIds.size()
        + " unrefined, "
        + refinedIds.size()
        + " refined; "
        + falseUnsafes.size()
        + " false unsafes";
  }

  public Set<AbstractIdentifier> getInterestingIds() {
    if (oneTotalIteration) {
      return new TreeSet<>(unrefinedIds.keySet());
    }
    return null;
  }

  private void saveStableUnsafes() {
    stableUnsafes.clear();
    for (Entry<AbstractIdentifier, UnrefinedUsagePointSet> entry : unrefinedIds.entrySet()) {
      Pair<UsageInfo, UsageInfo> tmpPair = detector.getUnsafePair(entry.getValue());
      stableUnsafes.put(entry.getKey(), tmpPair);
    }
  }

  public boolean hasUnsafes() {
    calculateUnsafesIfNecessary();
    return !stableUnsafes.isEmpty() || !refinedIds.isEmpty();
  }

  public boolean hasRefinedIds() {
    return !refinedIds.isEmpty();
  }

  public Map<AbstractIdentifier, Pair<UsageInfo, UsageInfo>> getStableUnsafes() {
    stableUnsafes.putAll(refinedIds);
    return stableUnsafes;
  }
}
