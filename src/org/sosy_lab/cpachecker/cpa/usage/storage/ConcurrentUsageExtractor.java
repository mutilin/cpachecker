// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.storage;

import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bam.BAMCPA;
import org.sosy_lab.cpachecker.cpa.bam.cache.BAMDataManager;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState;
import org.sosy_lab.cpachecker.cpa.usage.UsageCPA;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usage.UsageProcessor;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;
import org.sosy_lab.cpachecker.util.statistics.ThreadSafeTimerContainer;
import org.sosy_lab.cpachecker.util.statistics.ThreadSafeTimerContainer.TimerWrapper;

public class ConcurrentUsageExtractor {

  private final LogManager logger;
  private final UsageContainer container;
  private BAMDataManager manager;
  private UsageProcessor usageProcessor;
  private final boolean processCoveredUsages;
  private final boolean useConcurrentExtraction;

  private final StatTimer totalTimer = new StatTimer("Time for extracting usages");
  private final StatTimer traversalTimer = new StatTimer("Time for reached sets traversal");
  private final StatTimer usageTimer = new StatTimer("Time for usage set processing");
  private final StatInt reachedSetCounter =
      new StatInt(StatKind.SUM, "Number of traversed reached sets");
  private final StatInt finalSetCounter =
      new StatInt(StatKind.SUM, "Number of different reached sets");
  private final AtomicInteger processingSteps;
  private final AtomicInteger numberOfActiveTasks;
  private final ShutdownNotifier notifier;
  private ExecutorService service;
  private final Multimap<AbstractState, UsageDelta> processedSets;
  private final Map<Pair<AbstractState, UsageDelta>, List<AbstractState>> stacks;

  @SuppressWarnings("deprecation")
  private final ThreadSafeTimerContainer usageExpandingTimer =
      new ThreadSafeTimerContainer("Time for usage expanding");

  @SuppressWarnings("deprecation")
  private final ThreadSafeTimerContainer usageProcessingTimer =
      new ThreadSafeTimerContainer("Time for usage calculation");

  @SuppressWarnings("deprecation")
  private final ThreadSafeTimerContainer addingToContainerTimer =
      new ThreadSafeTimerContainer("Time for adding to container");


  public ConcurrentUsageExtractor(
      ConfigurableProgramAnalysis pCpa,
      LogManager pLogger,
      UsageContainer pContainer,
      UsageConfiguration pConfig) {
    logger = pLogger;
    container = pContainer;
    processCoveredUsages = pConfig.getProcessCoveredUsages();
    useConcurrentExtraction = pConfig.useConcurrentExtraction();

    BAMCPA bamCpa = CPAs.retrieveCPA(pCpa, BAMCPA.class);
    if (bamCpa != null) {
      manager = bamCpa.getData();
    }
    UsageCPA usageCpa = CPAs.retrieveCPA(pCpa, UsageCPA.class);
    usageProcessor = usageCpa.getUsageProcessor();
    processingSteps = new AtomicInteger();
    numberOfActiveTasks = new AtomicInteger();
    notifier = usageCpa.getNotifier();
    processedSets = ArrayListMultimap.create();
    // Normal map + synchronized shows better performance than ConcurrentHashMap
    stacks = new HashMap<>();
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public void extractUsages(ReachedSet reached) {
    totalTimer.start();
    int threads = useConcurrentExtraction ? Runtime.getRuntime().availableProcessors() : 1;
    // Reset from the previous iteration
    service = Executors.newFixedThreadPool(threads);
    processingSteps.set(0);
    numberOfActiveTasks.set(0);
    logger.log(Level.INFO, "Analysis is finished, start usage extraction");
    AbstractState firstState = reached.getFirstState();

    UsageDelta emptyDelta = UsageDelta.constructDeltaBetween(firstState, firstState);
    processedSets.put(firstState, emptyDelta);
    usageProcessor.updateRedundantUnsafes(container.getNotInterestingUnsafes());

    numberOfActiveTasks.incrementAndGet();
    traversalTimer.start();
    service.submit(
        new ReachedSetTraversal(reached, emptyDelta, ImmutableList.of()));

    waitUntill(s -> s != processingSteps.get());
    logger.log(Level.FINE, "ReachedSet traversion is finished");

    traversalTimer.stop();
    reachedSetCounter.setNextValue(processingSteps.get());
    finalSetCounter.setNextValue(processedSets.entries().size());
    numberOfActiveTasks.set(processedSets.entries().size());
    usageTimer.start();

    for (Entry<AbstractState, UsageDelta> rSet : processedSets.entries()) {
      service.submit(
          new UsageTraversal(
              rSet.getKey(),
              rSet.getValue(),
              stacks.get(Pair.of(rSet.getKey(), rSet.getValue()))));
    }

    waitUntill(s -> s > 0);
    logger.log(Level.FINE, "Usage processing is finished");
    usageTimer.stop();

    // Save memory
    processedSets.clear();
    stacks.clear();
    service.shutdownNow();
    totalTimer.stop();
  }

  public BAMDataManager getManager() {
    return manager;
  }

  private void waitUntill(Predicate<Integer> pPredicate) {
    try {
      while (pPredicate.apply(numberOfActiveTasks.get())) {
        synchronized (this) {
          this.wait(1000);
        }
        if (notifier.shouldShutdown()) {
          service.shutdown();
          notifier.shutdownIfNecessary();
        }
      }
    } catch (InterruptedException e) {
      logger.log(Level.WARNING, "wait is interrupted");
    }
  }

  public void printStatistics(StatisticsWriter pWriter) {
    StatisticsWriter writer =
        pWriter.spacer()
            .put(totalTimer)
            .beginLevel()
            .put(traversalTimer)
            .put(usageTimer)
            .beginLevel()
            .put(usageProcessingTimer)
            .beginLevel();

    // Timers are disabled due to problems with synchronization
    // usageProcessor.printStatistics(writer);
    writer.endLevel()
        .put(addingToContainerTimer)
        .put(usageExpandingTimer)
        .endLevel()
        .put(reachedSetCounter)
        .put(finalSetCounter);
  }

  private class ReachedSetTraversal implements Runnable {

    private final UsageDelta currentDelta;
    private final ReachedSet reached;
    private final List<AbstractState> expandedStack;

    ReachedSetTraversal(
        ReachedSet pSet,
        UsageDelta pDelta,
        List<AbstractState> pExpandedStack) {

      currentDelta = pDelta;
      reached = pSet;
      expandedStack = pExpandedStack;
    }

    @Override
    public void run() {
      for (AbstractState state : reached.asCollection()) {
        ARGState argState = (ARGState) state;

        // Search state in the BAM cache
        if (manager != null && manager.hasInitialState(argState)) {
          for (ARGState child : argState.getChildren()) {
            AbstractState reducedChild = manager.getReducedStateForExpandedState(child);
            ReachedSet innerReached = manager.getReachedSetForInitialState(argState, reducedChild);

            processReachedSet(argState, innerReached);
          }
        } else if (manager != null && manager.hasInitialStateWithoutExit(argState)) {
          // Likely thread functions
          ReachedSet innerReached = manager.getReachedSetForInitialState(argState);

          processReachedSet(argState, innerReached);
        }
      }

      if (processingSteps.incrementAndGet() == numberOfActiveTasks.get()) {
        service.notify();
      }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void processReachedSet(
        AbstractState rootState,
        ReachedSet innerReached) {

      AbstractState reducedState = innerReached.getFirstState();

      UsageDelta newDiff = UsageDelta.constructDeltaBetween(reducedState, rootState);
      UsageDelta difference = currentDelta.add(newDiff);

      if (shouldContinue(reducedState, difference)) {
        numberOfActiveTasks.incrementAndGet();
        List<AbstractState> newStack = new ArrayList<>(expandedStack);
        newStack.add(rootState);
        synchronized (processedSets) {
          stacks.put(Pair.of(reducedState, difference), newStack);
        }
        service.submit(
            new ReachedSetTraversal(
                innerReached,
                difference,
                newStack));
      }
    }

    private boolean shouldContinue(AbstractState pFirstState, UsageDelta currentDifference) {
      synchronized (processedSets) {
        Collection<UsageDelta> processed = processedSets.get(pFirstState);

        if (processCoveredUsages) {
          boolean b = !processed.contains(currentDifference);
          if (b) {
            processedSets.put(pFirstState, currentDifference);
          }
          return b;
        } else {
          for (UsageDelta delta : processed) {
            if (delta.covers(currentDifference)) {
              return false;
            }
          }
          // Remove old deltas, covered by new one
          Iterator<UsageDelta> iterator = processed.iterator();
          while (iterator.hasNext()) {
            UsageDelta delta = iterator.next();
            if (currentDifference.covers(delta)) {
              iterator.remove();
              stacks.remove(Pair.of(pFirstState, delta));
            }
          }
          processedSets.put(pFirstState, currentDifference);
          return true;
        }
      }
    }
  }

  private class UsageTraversal implements Runnable {

    private final UsageDelta currentDelta;
    // Cannot use reached set as we need to traverse the set in order
    private final AbstractState firstState;
    private final List<AbstractState> expandedStack;
    private final Map<AbstractState, List<UsageInfo>> stateToUsage;

    private final TimerWrapper expandingTimer;
    private final TimerWrapper processingTimer;
    private final TimerWrapper addingTimer;

    UsageTraversal(
        AbstractState pState,
        UsageDelta pDelta,
        List<AbstractState> pExpandedStack) {

      currentDelta = pDelta;
      firstState = pState;
      expandingTimer = usageExpandingTimer.getNewTimer();
      processingTimer = usageProcessingTimer.getNewTimer();
      addingTimer = addingToContainerTimer.getNewTimer();
      expandedStack = pExpandedStack;
      stateToUsage = new HashMap<>();
    }

    @Override
    public void run() {
      Deque<AbstractState> stateWaitlist = new ArrayDeque<>();
      stateWaitlist.add(firstState);
      Set<AbstractState> visitedStates = new HashSet<>();

      // Waitlist to be sure in order (not start from the middle point)
      while (!stateWaitlist.isEmpty()) {
        ARGState argState = (ARGState) stateWaitlist.poll();
        if (visitedStates.contains(argState)) {
          continue;
        }

        visitedStates.add(argState);
        List<UsageInfo> expandedUsages = expandUsagesAndAdd(argState);

        if (needToDumpUsages(argState)) {
          addingTimer.start();
          for (UsageInfo usage : expandedUsages) {
            container.add(usage);
          }
          addingTimer.stop();
        } else {
          stateToUsage.put(argState, expandedUsages);
        }
        stateWaitlist.addAll(argState.getSuccessors());
      }
      if (numberOfActiveTasks.decrementAndGet() <= 0) {
        service.notify();
      }
    }

    private boolean needToDumpUsages(AbstractState pState) {
      PredicateAbstractState predicateState =
          AbstractStates.extractStateByType(pState, PredicateAbstractState.class);

      return predicateState == null
          || (predicateState.isAbstractionState()
              && !predicateState.getAbstractionFormula().isFalse());
    }

    private List<UsageInfo> expandUsagesAndAdd(ARGState state) {

      List<UsageInfo> expandedUsages = new ArrayList<>();

      for (ARGState covered : state.getCoveredByThis()) {
        expandedUsages.addAll(stateToUsage.getOrDefault(covered, ImmutableList.of()));
      }
      for (ARGState parent : state.getParents()) {
        expandedUsages.addAll(stateToUsage.getOrDefault(parent, ImmutableList.of()));
      }

      processingTimer.start();
      List<UsageInfo> usages = usageProcessor.getUsagesForState(state);
      processingTimer.stop();

      expandingTimer.start();
      for (UsageInfo usage : usages) {
        UsageInfo expanded = usage.expand(currentDelta, expandedStack);
        if (expanded.isRelevant()) {
          expandedUsages.add(expanded);
        }
      }
      expandingTimer.stop();

      return expandedUsages;
    }
  }
}
