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
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usage.UsageState;
import org.sosy_lab.cpachecker.cpa.usage.refinement.RefinementResult;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class UsageContainer {
  private final SortedMap<SingleIdentifier, UnrefinedUsagePointSet> unrefinedIds;
  private final SortedMap<SingleIdentifier, RefinedUsagePointSet> refinedIds;

  private final UnsafeDetector detector;

  private Set<SingleIdentifier> falseUnsafes;
  private Set<SingleIdentifier> initialUnsafes;

  // Only for statistics
  private int initialUsages = 0;

  private final LogManager logger;
  private final UsageConfiguration config;

  private final StatTimer resetTimer = new StatTimer("Time for reseting unsafes");
  private final StatTimer unsafeDetectionTimer = new StatTimer("Time for unsafe detection");
  private final StatTimer searchingInCachesTimer = new StatTimer("Time for searching in caches");
  private final StatTimer addingToSetTimer = new StatTimer("Time for adding ti usage point set");

  private boolean usagesCalculated = false;
  private boolean oneTotalIteration = false;

  public UsageContainer(UsageConfiguration pConfig, LogManager l, UnsafeDetector unsafeDetector) throws InvalidConfigurationException {
    unrefinedIds = new TreeMap<>();
    refinedIds = new TreeMap<>();
    falseUnsafes = new TreeSet<>();
    logger = l;
    detector = unsafeDetector;
    config = pConfig;
  }

  public void add(SingleIdentifier pId, UsageInfo pUsage) {
    SingleIdentifier id = pId;
    searchingInCachesTimer.start();
    if (id instanceof StructureIdentifier) {
      id = ((StructureIdentifier) id).toStructureFieldIdentifier();
    }
    if (oneTotalIteration && !unrefinedIds.containsKey(id)) {
      searchingInCachesTimer.stop();
      return;
    }

    assert (!falseUnsafes.contains(id) || !refinedIds.containsKey(id));
    UnrefinedUsagePointSet uset;

    if (!unrefinedIds.containsKey(id)) {
      uset = new UnrefinedUsagePointSet();
      unrefinedIds.put(id, uset);
    } else {
      uset = unrefinedIds.get(id);
    }
    searchingInCachesTimer.stop();

    addingToSetTimer.start();
    uset.add(pUsage);
    addingToSetTimer.stop();
  }

  private void calculateUnsafesIfNecessary() {
    if (!usagesCalculated) {
      unsafeDetectionTimer.start();
      usagesCalculated = true;

      Iterator<Entry<SingleIdentifier, UnrefinedUsagePointSet>> iterator =
          unrefinedIds.entrySet().iterator();
      while (iterator.hasNext()) {
        Entry<SingleIdentifier, UnrefinedUsagePointSet> entry = iterator.next();
        UnrefinedUsagePointSet tmpList = entry.getValue();
        if (detector.isUnsafe(tmpList)) {
          if (!oneTotalIteration) {
            initialUsages += tmpList.size();
          }
        } else {
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

      unsafeDetectionTimer.stop();
    }
  }

  public Set<SingleIdentifier> getFalseUnsafes() {
    return falseUnsafes;
  }

  public Iterator<SingleIdentifier> getUnsafeIterator() {
    calculateUnsafesIfNecessary();
    Set<SingleIdentifier> result = new TreeSet<>(refinedIds.keySet());
    result.addAll(unrefinedIds.keySet());
    return result.iterator();
  }

  public Iterator<SingleIdentifier> getUnrefinedUnsafeIterator() {
    // New set to avoid concurrent modification exception
    Set<SingleIdentifier> result = new TreeSet<>(unrefinedIds.keySet());
    return result.iterator();
  }

  public int getTotalUnsafeSize() {
    calculateUnsafesIfNecessary();
    return unrefinedIds.size() + refinedIds.size();
  }

  public int getProcessedUnsafeSize() {
    return refinedIds.size();
  }

  public UnsafeDetector getUnsafeDetector() {
    return detector;
  }

  public void resetUnrefinedUnsafes() {
    resetTimer.start();
    usagesCalculated = false;
    oneTotalIteration = true;
    unrefinedIds.forEach((k, v) -> v.reset());
    logger.log(Level.FINE, "Unsafes are reseted");
    resetTimer.stop();
  }

  public void removeState(final UsageState pUstate) {
    unrefinedIds.forEach((id, uset) -> uset.remove(pUstate));
    logger.log(Level.ALL, "All unsafes related to key state " + pUstate + " were removed from reached set");
  }

  public AbstractUsagePointSet getUsages(SingleIdentifier id) {
    if (unrefinedIds.containsKey(id)) {
      return unrefinedIds.get(id);
    } else {
      return refinedIds.get(id);
    }
  }

  public void setAsFalseUnsafe(SingleIdentifier id) {
    falseUnsafes.add(id);
    unrefinedIds.remove(id);
  }

  public void setAsRefined(SingleIdentifier id, RefinementResult result) {
    Preconditions.checkArgument(result.isTrue(), "Result is not true, can not set the set as refined");
    checkArgument(
        detector.isUnsafe(getUsages(id)),
        "Refinement is successful, but the unsafe is absent for identifier %s",
        id);

    UsageInfo firstUsage = result.getTrueRace().getFirst();
    UsageInfo secondUsage = result.getTrueRace().getSecond();

    RefinedUsagePointSet rSet = RefinedUsagePointSet.create(firstUsage, secondUsage);
    refinedIds.put(id, rSet);
    unrefinedIds.remove(id);
  }

  public void printUsagesStatistics(StatisticsWriter out) {
    int unsafeSize = getTotalUnsafeSize();
    StatInt topUsagePoints = new StatInt(StatKind.SUM, "Total amount of unrefined usage points");
    StatInt unrefinedUsages = new StatInt(StatKind.SUM, "Total amount of unrefined usages");
    StatInt refinedUsages = new StatInt(StatKind.SUM, "Total amount of refined usages");
    StatCounter failedUsages = new StatCounter("Total amount of failed usages");

    final int generalUnrefinedSize = unrefinedIds.keySet().size();
    for (UnrefinedUsagePointSet uset : unrefinedIds.values()) {
      unrefinedUsages.setNextValue(uset.size());
      topUsagePoints.setNextValue(uset.getNumberOfTopUsagePoints());
    }

    int generalRefinedSize = 0;
    int generalFailedSize = 0;

    for (RefinedUsagePointSet uset : refinedIds.values()) {
      Pair<UsageInfo, UsageInfo> pair = uset.getUnsafePair();
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
        refinedUsages.setNextValue(uset.size());
      }
    }

    out.spacer()
        .put("Total amount of unsafes", unsafeSize)
        .put("Initial amount of unsafes (before refinement)", unsafeSize + falseUnsafes.size())
        .put("Initial amount of usages (before refinement)", initialUsages)
        .put("Initial amount of refined false unsafes", falseUnsafes.size())
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

  public Set<SingleIdentifier> getNotInterestingUnsafes() {
    return new TreeSet<>(Sets.union(falseUnsafes, refinedIds.keySet()));
  }

  public List<Pair<UsageInfo, UsageInfo>> calculateStableUnsafes() {
    calculateUnsafesIfNecessary();
    List<Pair<UsageInfo, UsageInfo>> result = new ArrayList<>();

    addUnsafesFrom(refinedIds, result);
    addUnsafesFrom(unrefinedIds, result);
    return result;
  }

  private void addUnsafesFrom(
      SortedMap<SingleIdentifier, ? extends AbstractUsagePointSet> storage,
      List<Pair<UsageInfo, UsageInfo>> pResult) {

    for (Entry<SingleIdentifier, ? extends AbstractUsagePointSet> entry : storage.entrySet()) {
      Pair<UsageInfo, UsageInfo> tmpPair = detector.getUnsafePair(entry.getValue());
      assert tmpPair != null;
      pResult.add(tmpPair);
    }
  }
}
