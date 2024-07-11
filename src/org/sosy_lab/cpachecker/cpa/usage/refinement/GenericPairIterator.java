// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.refinement;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public abstract class GenericPairIterator<I, O> extends GenericIterator<I, Pair<O, O>> {

  protected O firstItem;
  protected final int iterationLimit;

  private int[] iterations = new int[2];

  // Statistics
  private StatTimer iterationTime = new StatTimer("Time for iteration");
  private StatCounter numberOfIterations = new StatCounter("Number of internal iterations");
  private StatCounter numberOflimits = new StatCounter("Number of iteration breaks due to limit");

  public GenericPairIterator(
      ConfigurableRefinementBlock<Pair<O, O>> pWrapper,
      ShutdownNotifier pNotifier,
      int pInterationLimit) {
    super(pWrapper, pNotifier);
    iterationLimit = pInterationLimit;
  }

  @Override
  protected void init(I pInput) {
    firstItem = null;
    iterations[0] = 0;
    iterations[1] = 0;
  }

  @Override
  protected Pair<O, O> getNext(I pInput) {
    if (firstItem == null) {
      // First time or it was unreachable last time
      firstItem = getNextIteration(0);
      if (firstItem == null) {
        return null;
      }
    }

    O secondItem = getNextIteration(1);
    if (secondItem == null) {
      // And move shift the first one
      firstItem = getNextIteration(0);
      if (firstItem == null) {
        return null;
      }
      resetSecondIterationFor();
      secondItem = getNextIteration(1);
      if (secondItem == null) {
        return null;
      }
    }

    return Pair.of(firstItem, secondItem);
  }

  private O getNextIteration(int pI) {
    if (iterations[pI] >= iterationLimit) {
      numberOflimits.inc();
      return null;
    }
    iterations[pI]++;

    numberOfIterations.inc();
    iterationTime.start();
    O next = getNextIteration0(pI);
    iterationTime.stop();
    return next;
  }

  @Override
  protected void printDetailedStatistics(StatisticsWriter pOut) {
    pOut.put(iterationTime).put(numberOfIterations).put(numberOflimits);
  }

  protected abstract O getNextIteration0(int pI);

  protected abstract void resetSecondIterationFor();

  @Override
  protected void handleFinishSignal(Class<? extends RefinementInterface> callerClass) {
    if (callerClass.equals(IdentifierIterator.class)) {
      // Refinement iteration finishes
      firstItem = null;
    }
  }
}
