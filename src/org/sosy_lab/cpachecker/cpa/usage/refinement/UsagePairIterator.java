// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.refinement;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageInfoSet;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;


public class UsagePairIterator
    extends GenericPairIterator<Pair<UsageInfoSet, UsageInfoSet>, UsageInfo> {
  private final LogManager logger;

  //internal state
  private List<Iterator<UsageInfo>> iterators = new ArrayList<>();
  private UsageInfoSet secondUsageInfoSet;

  private final StatCounter removedUsages = new StatCounter("Removed empty usages");

  public UsagePairIterator(
      ConfigurableRefinementBlock<Pair<UsageInfo, UsageInfo>> pWrapper,
      LogManager l,
      ShutdownNotifier pNotifier,
      int pIterationLimit) {
    super(pWrapper, pNotifier, pIterationLimit);
    logger = l;
  }

  @Override
  protected void init(Pair<UsageInfoSet, UsageInfoSet> pInput) {
    super.init(pInput);
    UsageInfoSet firstUsageInfoSet;
    firstUsageInfoSet = pInput.getFirst();
    secondUsageInfoSet = pInput.getSecond();
    assert !firstUsageInfoSet.isEmpty();
    assert !secondUsageInfoSet.isEmpty();

    iterators.clear();
    iterators.add(firstUsageInfoSet.iterator());
    iterators.add(secondUsageInfoSet.iterator());
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void finishIteration(Pair<UsageInfo, UsageInfo> usagePair, RefinementResult r) {
    UsageInfo first = usagePair.getFirst();
    UsageInfo second = usagePair.getSecond();
    Iterator<UsageInfo> firstUsageIterator = iterators.get(0);
    Iterator<UsageInfo> secondUsageIterator = iterators.get(1);

    List<UsageInfo> unreachableUsages = (List<UsageInfo>) r.getInfo(PathPairIterator.class);

    if (unreachableUsages != null && unreachableUsages.contains(second)) {
      logger.log(Level.FINE, "Usage " + secondUsageIterator + " is not reachable, remove it from container");
      secondUsageIterator.remove();
      removedUsages.inc();
    }
    if (unreachableUsages != null && unreachableUsages.contains(first)) {
      logger.log(Level.FINE, "Usage " + firstUsageIterator + " is not reachable, remove it from container");
      firstUsageIterator.remove();
      removedUsages.inc();
      firstItem = null;
      resetSecondIterationFor();
    }
    if ((first.isLooped() || second.isLooped()) && first.equals(second)) {
      first.setAsLooped();
      second.setAsLooped();
    }
  }

  @Override
  protected void handleFinishSignal(Class<? extends RefinementInterface> pCallerClass) {
    super.handleFinishSignal(pCallerClass);
    if (pCallerClass.equals(IdentifierIterator.class)) {
      secondUsageInfoSet = null;
      iterators.clear();
    }
  }

  @Override
  protected UsageInfo getNextIteration0(int pI) {
    Iterator<UsageInfo> iterator = iterators.get(pI);
    if (iterator.hasNext()) {
      UsageInfo item = iterator.next();
      if (pI == 1 && firstItem == item) {
        item = item.copy();
      }
      return item;
    }
    return null;
  }

  @Override
  protected void resetSecondIterationFor() {
    iterators.set(1, secondUsageInfoSet.iterator());
  }

  @Override
  protected void printDetailedStatistics(StatisticsWriter pOut) {
    super.printDetailedStatistics(pOut);
    pOut.put(removedUsages);
  }

  @Override
  protected String getName() {
    return "UsagePairIterator";
  }
}
