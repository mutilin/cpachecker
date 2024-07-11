// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.refinement;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usage.storage.UnrefinedUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usage.storage.UnsafeDetector;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageContainer;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsagePoint;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class PointIterator
    extends GenericIterator<AbstractIdentifier, Pair<Set<UsageInfo>, Set<UsageInfo>>> {

  private UsageContainer container;
  private UnsafeDetector detector;

  // Internal state
  private UsagePoint firstPoint;
  private UsagePoint secondPoint;

  private Iterator<UsagePoint> firstPointIterator;
  private Iterator<UsagePoint> secondPointIterator;

  private UnrefinedUsagePointSet currentUsagePointSet;

  private final StatCounter removedPoints = new StatCounter("Removed empty usage points");

  public PointIterator(
      ConfigurableRefinementBlock<Pair<Set<UsageInfo>, Set<UsageInfo>>> pWrapper,
      ShutdownNotifier pNotifier) {
    super(pWrapper, pNotifier);
  }

  @Override
  protected void init(AbstractIdentifier id) {
    currentUsagePointSet = container.getUnrefinedUsages(id);

    firstPointIterator = currentUsagePointSet.getPointIterator();
    secondPointIterator = currentUsagePointSet.getPointIterator();
    firstPoint = firstPointIterator.next();
    assert firstPoint != null;
  }

  @Override
  protected boolean isRelevant(Pair<Set<UsageInfo>, Set<UsageInfo>> iteration) {
    return !(iteration.getFirst().isEmpty() || iteration.getSecond().isEmpty());
  }

  @Override
  protected Pair<Set<UsageInfo>, Set<UsageInfo>> getNext(AbstractIdentifier pInput) {
    // Sanity check
    UnrefinedUsagePointSet pointSet = container.getUnrefinedUsages(pInput);
    assert currentUsagePointSet == pointSet;

    do {
      if (!secondPointIterator.hasNext() || currentUsagePointSet.getUsageInfo(firstPoint) == null) {
        if (!firstPointIterator.hasNext()) {
          return null;
        }
        firstPoint = firstPointIterator.next();
        // Start from first point to save the time
        secondPointIterator = currentUsagePointSet.getPointIteratorFrom(firstPoint);
        if (!secondPointIterator.hasNext()) {
          // All points are removed
          return null;
        }
      }
      secondPoint = secondPointIterator.next();

      assert firstPoint != null && secondPoint != null;

    } while (!detector.isUnsafePair(firstPoint, secondPoint)
        // The point was removed
        || currentUsagePointSet.getUsageInfo(firstPoint) == null
        || currentUsagePointSet.getUsageInfo(secondPoint) == null);

    Pair<Set<UsageInfo>, Set<UsageInfo>> resultingPair =
        prepareIterationPair(firstPoint, secondPoint);
    if (firstPoint == secondPoint) {
      postpone(resultingPair);
      return getNext(pInput);
    }
    return resultingPair;
  }

  private Pair<Set<UsageInfo>, Set<UsageInfo>> prepareIterationPair(
      UsagePoint first, UsagePoint second) {
    Set<UsageInfo> firstUsageInfoSet = currentUsagePointSet.getUsageInfo(first);
    Set<UsageInfo> secondUsageInfoSet = currentUsagePointSet.getUsageInfo(second);

    if (firstUsageInfoSet == secondUsageInfoSet) {
      // To avoid concurrent modification
      secondUsageInfoSet = new TreeSet<>(secondUsageInfoSet);
    }

    assert firstUsageInfoSet != null;
    assert secondUsageInfoSet != null;
    return Pair.of(firstUsageInfoSet, secondUsageInfoSet);
  }

  @Override
  protected void finishIteration(Pair<Set<UsageInfo>, Set<UsageInfo>> pPair, RefinementResult r) {
    Set<UsageInfo> firstUsageInfoSet = pPair.getFirst();
    Set<UsageInfo> secondUsageInfoSet = pPair.getSecond();

    if (secondUsageInfoSet.isEmpty()) {
      // No reachable usages - remove point
      currentUsagePointSet.remove(secondPoint);
      removedPoints.inc();
    }
    if (firstUsageInfoSet.isEmpty() && firstPoint != secondPoint) {
      // No reachable usages - remove point
      currentUsagePointSet.remove(firstPoint);
      removedPoints.inc();
    }
  }

  @Override
  protected void handleUpdateSignal(
      Class<? extends RefinementInterface> pCallerClass, Object pData) {
    if (pCallerClass.equals(IdentifierIterator.class)) {
      assert pData instanceof UsageContainer;
      container = (UsageContainer) pData;
      detector = container.getUnsafeDetector();
    }
  }

  @Override
  protected void handleFinishSignal(Class<? extends RefinementInterface> pCallerClass) {
    if (pCallerClass.equals(IdentifierIterator.class)) {
      firstPointIterator = null;
      secondPointIterator = null;
      firstPoint = null;
      secondPoint = null;
      currentUsagePointSet = null;
    }
  }

  @Override
  protected void printDetailedStatistics(StatisticsWriter pOut) {
    pOut.put(removedPoints);
  }

  @Override
  protected String getName() {
    return "PointPairIterator";
  }
}
