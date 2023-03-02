// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.storage;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
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
  private final Map<AbstractState, Collection<UsageDelta>> processedSets;
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

  @SuppressWarnings("deprecation")
  private final ThreadSafeTimerContainer reachedSetCheckTimer =
      new ThreadSafeTimerContainer("Time for checking a reached set");

  @SuppressWarnings("deprecation")
  private final ThreadSafeTimerContainer processedSetWaitingTimer =
      new ThreadSafeTimerContainer("Time for waiting for processed deltas");

  @SuppressWarnings("deprecation")
  private final ThreadSafeTimerContainer processedSyncTimer =
      new ThreadSafeTimerContainer("Time for filter processed deltas");

  @SuppressWarnings("deprecation")
  private final ThreadSafeTimerContainer processedWaitingTimer =
      new ThreadSafeTimerContainer("Time for waiting processed deltas");


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
    processedSets = new HashMap<>();
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
    Collection<UsageDelta> initialSet = new HashSet<>();
    initialSet.add(emptyDelta);
    processedSets.put(firstState, initialSet);
    usageProcessor.updateRedundantUnsafes(container.getNotInterestingUnsafes());

    numberOfActiveTasks.incrementAndGet();
    traversalTimer.start();
    service.submit(
        new ReachedSetTraversal(reached, emptyDelta, ImmutableList.of()));

    waitUntill(s -> s != processingSteps.get());
    logger.log(Level.FINE, "ReachedSet traversion is finished");

    traversalTimer.stop();
    reachedSetCounter.setNextValue(processingSteps.get());
    finalSetCounter.setNextValue(0);
    numberOfActiveTasks.set(0);
    usageTimer.start();

    for (Entry<AbstractState, Collection<UsageDelta>> entry : processedSets.entrySet()) {
      numberOfActiveTasks.addAndGet(entry.getValue().size());
      finalSetCounter.setNextValue(entry.getValue().size());
      for (UsageDelta rSet : entry.getValue()) {
        service.submit(
            new UsageTraversal(entry.getKey(), rSet, stacks.get(Pair.of(entry.getKey(), rSet))));
      }
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
    int threads = useConcurrentExtraction ? Runtime.getRuntime().availableProcessors() : 1;
    StatisticsWriter writer =
        pWriter.spacer()
            .put("Number of concurrent extractors", threads)
            .put(totalTimer)
            .beginLevel()
            .put(traversalTimer)
            .beginLevel()
            .put(reachedSetCheckTimer)
            .beginLevel()
            .put(processedSetWaitingTimer)
            .put(processedWaitingTimer)
            .beginLevel()
            .put(processedSyncTimer)
            .endLevel()
            .endLevel()
            .endLevel()
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

    private final TimerWrapper checkTimer;
    private final TimerWrapper procesedTimer;
    private final TimerWrapper procesedSetWaitTimer;
    private final TimerWrapper procesedWaitTimer;

    ReachedSetTraversal(
        ReachedSet pSet,
        UsageDelta pDelta,
        List<AbstractState> pExpandedStack) {

      currentDelta = pDelta;
      reached = pSet;
      expandedStack = pExpandedStack;
      checkTimer = reachedSetCheckTimer.getNewTimer();
      procesedTimer = processedSyncTimer.getNewTimer();
      procesedSetWaitTimer = processedSetWaitingTimer.getNewTimer();
      procesedWaitTimer = processedWaitingTimer.getNewTimer();
    }

    @Override
    public void run() {
      for (AbstractState state : reached.asCollection()) {

        // Search state in the BAM cache
        if (manager != null && manager.hasInitialState(state)) {
          ARGState argState = (ARGState) state;
          for (ARGState child : argState.getChildren()) {
            AbstractState reducedChild = manager.getReducedStateForExpandedState(child);
            ReachedSet innerReached = manager.getReachedSetForInitialState(state, reducedChild);

            processReachedSet(state, innerReached);
          }
        } else if (manager != null && manager.hasInitialStateWithoutExit(state)) {
          // Likely thread functions
          ReachedSet innerReached = manager.getReachedSetForInitialState(state);

          processReachedSet(state, innerReached);
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

      checkTimer.start();
      boolean res = shouldContinue(reducedState, difference);
      checkTimer.stop();

      if (res) {
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
      Collection<UsageDelta> processed;
      procesedSetWaitTimer.start();
      synchronized (processedSets) {
        if (processedSets.containsKey(pFirstState)) {
          processed = processedSets.get(pFirstState);
        } else {
          processed = new HashSet<>();
          processedSets.put(pFirstState, processed);
        }
      }
      procesedSetWaitTimer.stop();

      if (processCoveredUsages) {

        procesedWaitTimer.start();
        synchronized (processed) {
          procesedTimer.start();
          boolean b = !processed.contains(currentDifference);
          if (b) {
            processed.add(currentDifference);
          }
          procesedTimer.stop();
          procesedWaitTimer.stop();
          return b;
        }

      } else {
        Collection<UsageDelta> toRemove = new HashSet<>();

        procesedWaitTimer.start();
        synchronized (processed) {
          procesedTimer.start();
          for (UsageDelta delta : processed) {
            if (delta.covers(currentDifference)) {
              procesedTimer.stop();
              procesedWaitTimer.stop();
              return false;
            }
            if (currentDifference.covers(delta)) {
              toRemove.add(delta);
            }
          }

          processed.removeAll(toRemove);
          processed.add(currentDifference);
          procesedTimer.stop();
        }
        procesedWaitTimer.stop();

        synchronized (processedSets) {
          for (UsageDelta delta : toRemove) {
            stacks.remove(Pair.of(pFirstState, delta));
          }
        }

        return true;
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
