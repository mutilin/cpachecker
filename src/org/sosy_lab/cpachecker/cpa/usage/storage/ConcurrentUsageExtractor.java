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
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
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
  private final StatTimer usageInitTimer = new StatTimer("Time for usage tasks initialization");
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

  private volatile boolean active;
  // TODO Sorted
  private final Queue<AbstractTask> tasks;

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
    tasks = new PriorityQueue<>();
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

    active = true;
    assert(tasks.isEmpty());
    List<Thread> workers = new ArrayList<>();

    synchronized (tasks) {
      // start workers inside synchronized to avoid missed task
      for (int i = 0; i < threads; i++) {
        ReachedSetWorker worker = new ReachedSetWorker();
        workers.add(worker);
        worker.start();
      }
      stacks.put(Pair.of(reached.getFirstState(), emptyDelta), ImmutableList.of());
      tasks.add(new ReachedSetTask(reached, emptyDelta, ImmutableList.of()));
    }

    waitUntill(s -> s != processingSteps.get(), workers);
    logger.log(Level.FINE, "ReachedSet traversion is finished");

    traversalTimer.stop();
    reachedSetCounter.setNextValue(processingSteps.get());
    finalSetCounter.setNextValue(0);
    numberOfActiveTasks.set(0);
    usageTimer.start();
    usageInitTimer.start();

    synchronized (tasks) {
      // start workers inside synchronized to avoid missed task
      for (int i = 0; i < threads; i++) {
        UsageWorker worker = new UsageWorker();
        workers.add(worker);
        worker.start();
      }
      active = true;

      numberOfActiveTasks.set(processedSets.size());
      for (Entry<AbstractState, Collection<UsageDelta>> entry : processedSets.entrySet()) {
        Collection<UsageDelta> deltas = entry.getValue();
        finalSetCounter.setNextValue(deltas.size());

        List<Pair<UsageDelta, List<AbstractState>>> uStacks = new ArrayList<>();
        for (UsageDelta delta : deltas) {
          List<AbstractState> stack = stacks.get(Pair.of(entry.getKey(), delta));
          assert stack != null;
          uStacks.add(Pair.of(delta, stack));
        }
        tasks.add(new UsageSetTask(entry.getKey(), uStacks));
      }
    }
    usageInitTimer.stop();

    waitUntill(s -> s > 0, workers);
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

  private void waitUntill(Predicate<Integer> pPredicate, List<Thread> workers) {
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

    synchronized (tasks) {
      active = false;
      tasks.notifyAll();
    }
    try {
      for (Thread worker : workers) {
        worker.join();
      }
    } catch (InterruptedException e) {
      logger.log(Level.WARNING, "Join is interrupted");
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
            .put(usageInitTimer)
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

  private static abstract class AbstractTask implements Comparable<AbstractTask> {
    protected final ARGState firstState;

    AbstractTask(ARGState pState) {
      firstState = pState;
    }

    @Override
    public int compareTo(AbstractTask pArg0) {
      return firstState.getStateId() - pArg0.firstState.getStateId();
    }
  }

  private static class ReachedSetTask extends AbstractTask {
    private final UsageDelta currentDelta;
    private final ReachedSet reached;
    private final List<AbstractState> expandedStack;

    ReachedSetTask(
        ReachedSet pSet,
        UsageDelta pDelta,
        List<AbstractState> pExpandedStack) {

      super((ARGState) pSet.getFirstState());
      currentDelta = pDelta;
      reached = pSet;
      expandedStack = pExpandedStack;
    }

    @Override
    public int compareTo(AbstractTask pArg0) {
      int res = super.compareTo(pArg0);
      if (res != 0) {
        return res;
      }

      ReachedSetTask rTask = (ReachedSetTask) pArg0;
      int size = Math.min(expandedStack.size(), rTask.expandedStack.size());
      for (int i = 0; i < size; i++) {
        ARGState state1 = (ARGState) expandedStack.get(expandedStack.size() - i - 1);
        ARGState state2 = (ARGState) rTask.expandedStack.get(rTask.expandedStack.size() - i - 1);
        res = state1.getStateId() - state2.getStateId();
        if (res != 0) {
          return res;
        }
      }

      throw new AssertionError("Should not be two equal tasks");
    }

    @Override
    public int hashCode() {
      return Objects.hash(currentDelta, expandedStack, reached);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (!(obj instanceof ReachedSetTask)) {
        return false;
      }
      ReachedSetTask other = (ReachedSetTask) obj;
      return Objects.equals(currentDelta, other.currentDelta)
          && Objects.equals(expandedStack, other.expandedStack)
          && Objects.equals(reached, other.reached);
    }
  }

  private abstract class AbstractWorker extends Thread {

    @Override
    public void run() {
      while (active) {
        AbstractTask toProcess = null;
        synchronized (tasks) {
          if (!tasks.isEmpty()) {
            toProcess = tasks.poll();
          } else {
            try {
              if (!active) {
                return;
              }
              tasks.wait(1000);
            } catch (InterruptedException e) {
              logger.log(Level.WARNING, "Wait is interrupted");
            }
          }
        }
        if (toProcess != null) {
          process(toProcess);
        }
      }
    }

    protected abstract void process(AbstractTask task);
  }

  private class ReachedSetWorker extends AbstractWorker {

    private final TimerWrapper checkTimer;
    private final TimerWrapper procesedTimer;
    private final TimerWrapper procesedSetWaitTimer;
    private final TimerWrapper procesedWaitTimer;

    public ReachedSetWorker() {
      checkTimer = reachedSetCheckTimer.getNewTimer();
      procesedTimer = processedSyncTimer.getNewTimer();
      procesedSetWaitTimer = processedSetWaitingTimer.getNewTimer();
      procesedWaitTimer = processedWaitingTimer.getNewTimer();
    }

    @Override
    protected void process(AbstractTask task) {
      try {
        ReachedSetTask rTask = (ReachedSetTask) task;
        ReachedSet reached = rTask.reached;
        UsageDelta delta = rTask.currentDelta;
        List<AbstractState> stack = rTask.expandedStack;

        for (AbstractState state : reached.asCollection()) {

          // Search state in the BAM cache
          if (manager != null && manager.hasInitialState(state)) {
            ARGState argState = (ARGState) state;
            for (ARGState child : argState.getChildren()) {
              if (manager.hasExpandedState(child)) {
                // A destroyed state may be in BAM cache because
                // It is destroyed after stop after expanding in caller block
                // So, ignore it
                AbstractState reducedChild = manager.getReducedStateForExpandedState(child);
                ReachedSet innerReached = manager.getReachedSetForInitialState(state, reducedChild);

                processReachedSet(state, innerReached, delta, stack);
              }
            }
          } else if (manager != null && manager.hasInitialStateWithoutExit(state)) {
            // Likely thread functions
            ReachedSet innerReached = manager.getReachedSetForInitialState(state);

            processReachedSet(state, innerReached, delta, stack);
          }
        }

        if (processingSteps.incrementAndGet() == numberOfActiveTasks.get()) {
          synchronized (ConcurrentUsageExtractor.this) {
            ConcurrentUsageExtractor.this.notify();
          }
        }

      } catch (Throwable e) {
        // Important to increment processingSteps to avoid hanging
        // e.printStackTrace() is a forbidden api
        logger.log(Level.WARNING, e);
        processingSteps.incrementAndGet();
        throw e;
      }
    }

    private void processReachedSet(
        AbstractState rootState,
        ReachedSet innerReached,
        UsageDelta currentDelta,
        List<AbstractState> expandedStack) {

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
        ReachedSetTask task = new ReachedSetTask(innerReached, difference, newStack);
        synchronized (tasks) {
          tasks.add(task);
          tasks.notify();
        }
      }
    }

    private boolean shouldContinue(AbstractState pFirstState, UsageDelta currentDifference) {
      Collection<UsageDelta> processed;
      procesedSetWaitTimer.start();
      synchronized (processedSets) {
        if (processedSets.containsKey(pFirstState)) {
          processed = processedSets.get(pFirstState);
        } else {
          processed = new ArrayList<>();
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

  private static class UsageSetTask extends AbstractTask {
    private final List<Pair<UsageDelta, List<AbstractState>>> stacks;

    UsageSetTask(
        AbstractState pFirst,
        List<Pair<UsageDelta, List<AbstractState>>> pExpandedStacks) {
      super((ARGState) pFirst);
      stacks = pExpandedStacks;
    }

    @Override
    public int compareTo(AbstractTask pArg0) {
      int res = super.compareTo(pArg0);
      if (res != 0) {
        return res;
      }
      throw new AssertionError("Should not be two equal tasks");
    }

    @Override
    public int hashCode() {
      return Objects.hash(firstState, stacks);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (!(obj instanceof UsageSetTask)) {
        return false;
      }
      UsageSetTask other = (UsageSetTask) obj;
      return Objects.equals(firstState, other.firstState) && Objects.equals(stacks, other.stacks);
    }
  }

  private class UsageWorker extends AbstractWorker {

    private final TimerWrapper expandingTimer;
    private final TimerWrapper processingTimer;
    private final TimerWrapper addingTimer;

    private Map<AbstractState, List<UsageInfo>> stateToUsage;

    UsageWorker() {
      expandingTimer = usageExpandingTimer.getNewTimer();
      processingTimer = usageProcessingTimer.getNewTimer();
      addingTimer = addingToContainerTimer.getNewTimer();
    }

    @Override
    protected void process(AbstractTask task) {
      UsageSetTask uTask = (UsageSetTask) task;
      AbstractState firstState = task.firstState;

      Deque<AbstractState> stateWaitlist = new ArrayDeque<>();
      stateWaitlist.add(firstState);
      Set<AbstractState> visitedStates = new HashSet<>();
      stateToUsage = new HashMap<>();

      // Waitlist to be sure in order (not start from the middle point)
      while (!stateWaitlist.isEmpty()) {
        ARGState argState = (ARGState) stateWaitlist.poll();
        if (visitedStates.contains(argState)) {
          continue;
        }

        addingTimer.start();
        visitedStates.add(argState);
        List<UsageInfo> usages = expandUsagesAndAdd(argState);
        addingTimer.stop();

        if (needToDumpUsages(argState)) {
          expandingTimer.start();
          for (UsageInfo usage : usages) {
            for (Pair<UsageDelta, List<AbstractState>> stack : uTask.stacks) {
              UsageInfo expanded = usage.expand(stack.getFirst(), stack.getSecond());
              if (expanded.isRelevant()) {
                container.add(expanded);
              }
            }
          }
          expandingTimer.stop();
        } else {
          stateToUsage.put(argState, usages);
        }
        stateWaitlist.addAll(argState.getSuccessors());
      }
      if (numberOfActiveTasks.decrementAndGet() <= 0) {
        synchronized (ConcurrentUsageExtractor.this) {
          ConcurrentUsageExtractor.this.notify();
        }
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
      List<UsageInfo> newUsages = usageProcessor.getUsagesForState(state);
      processingTimer.stop();

      if (expandedUsages.isEmpty()) {
        return newUsages;
      }
      expandedUsages.addAll(newUsages);

      return expandedUsages;
    }
  }
}
