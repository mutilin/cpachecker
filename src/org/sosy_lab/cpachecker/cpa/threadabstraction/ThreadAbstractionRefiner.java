// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.threadabstraction;

import static org.sosy_lab.cpachecker.util.statistics.StatisticsWriter.writingStatisticsTo;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGBasedRefiner;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.threadabstraction.ThreadAbstractionOptions.AddProjections;
import org.sosy_lab.cpachecker.cpa.threadabstraction.ThreadAbstractionOptions.PrecisionUpdate;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CounterexampleAnalysisFailed;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Precisions;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class ThreadAbstractionRefiner implements ARGBasedRefiner, StatisticsProvider {

  private final StatTimer totalRefinement = new StatTimer("Time for refinement");
  private final StatInt pathLength = new StatInt(StatKind.AVG, "Average length of a path");
  private final StatCounter numSpuriousPaths =
      new StatCounter("Number of spurious counterexamples");
  private final StatCounter numFeasiblePaths =
      new StatCounter("Number of feasible counterexamples");
  private final StatInt projInPath =
      new StatInt(StatKind.AVG, "Average amount of projections in feasible path");
  private final StatCounter numProjToPrec =
      new StatCounter("Number of projections that were added to the precision");
  private final StatCounter numProjOrder =
      new StatCounter("Number of paths with projections in the wrong order");
  private final StatInt edgesAdded =
      new StatInt(StatKind.SUM, "Total amount of edges added to precision");
  private final StatTimer totalOrderAnalysis =
      new StatTimer("Time for analysing order in which projections are applied");
  private final StatTimer timeForPrecUpdate = new StatTimer("Time for precision update");

  final ThreadAbstractionOptions options;

  public ThreadAbstractionRefiner(final Configuration pConfig)
      throws InvalidConfigurationException {
    options = new ThreadAbstractionOptions();
    pConfig.inject(options);
  }

  @Override
  public CounterexampleInfo performRefinementForPath(ARGReachedSet pReached, ARGPath pPath)
      throws CPAException, InterruptedException {
    totalRefinement.start();
    try {
      pathLength.setNextValue(pPath.size());
      if (options.addProjectionsToPrecision() == AddProjections.EVERYTHING_IN_PRECISION) {
        return CounterexampleInfo.feasibleImprecise(pPath);
      }

      Set<ARGState> apppliedProjections = new TreeSet<>();
      for (ARGState state : pPath.asStatesList()) {
        if (state.getAppliedFrom() != null) {

          ARGState newProjection = state.getAppliedFrom().getSecond();

          if (apppliedProjections.contains(newProjection)) {
            if (options.addProjectionsToPrecision() == AddProjections.REPEATED) {
              if (updatePrecisionWithProjection(newProjection, pReached)) {
                numSpuriousPaths.inc();
                numProjToPrec.inc();
                return CounterexampleInfo.spurious();
              }
            }
            continue;
          }

          if (options.addProjectionsToPrecision() == AddProjections.ALL) {
            if (updatePrecisionWithProjection(newProjection, pReached)) {
              numSpuriousPaths.inc();
              numProjToPrec.inc();
              return CounterexampleInfo.spurious();
            }
          }

          totalOrderAnalysis.start();
          for (ARGState oldProjection : apppliedProjections) {
            String thread = getThreadForProjection(newProjection);
            if (thread.equals(getThreadForProjection(oldProjection))) {
              for (ARGState presumablyEarlierState : oldProjection.getProjectedFrom()) {
                for (ARGState presumablyLaterState : newProjection.getProjectedFrom()) {
                  if (ARGUtils.getAllStatesOnPathsTo(presumablyEarlierState)
                      .contains(presumablyLaterState)) {
                    throw new CounterexampleAnalysisFailed(
                        "Could not refine path. Projections are applied in the wrong order");
                  }
                }
              }
            }
          }
          totalOrderAnalysis.stop();
          apppliedProjections.add(newProjection);
        }
      }

      numFeasiblePaths.inc();
      projInPath.setNextValue(apppliedProjections.size());
      return CounterexampleInfo.feasibleImprecise(pPath);

    } finally {
      totalRefinement.stop();
    }
  }

  /**
   * finds states to update precision for. If the precision is different, updates the precision
   * @return true if precision has changed
   */
  private boolean updatePrecisionWithProjection(ARGState projection, ARGReachedSet pReached)
      throws InterruptedException {
    timeForPrecUpdate.start();
    try {
      // if the edge is not EmptyEdge, no refinement is needed since this edge is already
      // in precision.
      if (!AbstractStates.extractStateByType(projection, ThreadAbstractionStateWithEdge.class)
          .getAbstractEdge()
          .isEmptyEdge()) {
        return false;
      }

      ImmutableSet<CFAEdge> edges = getEdgesFromProjection(projection);

      if (options.optimizePrecisionUpdate() == PrecisionUpdate.ALL) {
        return addEdgesToPrecisionForState(
            pReached,
            Iterables.get(((ARGState) pReached.asReachedSet().getFirstState()).getChildren(), 0),
            edges);
      } else {
        Set<ARGState> states = new TreeSet<>(projection.getProjectedFrom());

        if (options.optimizePrecisionUpdate() == PrecisionUpdate.COMMON_PREDECESSOR) {
          return addEdgesToPrecisionForState(pReached, findCommonPredecessor(states), edges);
        }

        if (options.optimizePrecisionUpdate() == PrecisionUpdate.PROJECTIONS) {
          boolean precisionChanged = false;
          for (ARGState state : states) {
            if (!state.isDestroyed()) {
              if (addEdgesToPrecisionForState(pReached, state, edges)) {
                precisionChanged = true;
              }
            }
          }
          return precisionChanged;
        }
      }
      return false;
    } finally {
      timeForPrecUpdate.stop();
    }
  }

  private ARGState findCommonPredecessor(Collection<ARGState> states) {
    Iterator<ARGState> iterator = states.iterator();
    ARGState predecessor = iterator.next();
    // In case of single element, we just return itself
    while (iterator.hasNext()) {
      predecessor = findCommonPredecessor(iterator.next(), predecessor);
    }
    return predecessor;
  }

  private ARGState findCommonPredecessor(ARGState state1, ARGState state2) {
    if (ARGUtils.getAllStatesOnPathsTo(state2).contains(state1)) {
      return state1;
    }
    Set<ARGState> allStatesOnPath = ARGUtils.getAllStatesOnPathsTo(state1);
    while (!allStatesOnPath.contains(state2)) {
      state2 = findCommonPredecessor(state2.getParents());
    }
    return state2;
  }

  private ImmutableSet<CFAEdge> getEdgesFromProjection(ARGState projection) {
    ImmutableSet.Builder<CFAEdge> edges = ImmutableSet.builder();
    for (ARGState state : projection.getProjectedFrom()) {
      for (ARGState child : state.getChildren()) {
        if (child.getAppliedFrom() == null) {
          edges.add(state.getEdgeToChild(child));
          break;
        }
      }
    }
    return edges.build();
  }

  /**
   * If precision is different, updates precision and removes subtree
   * @return true if precision was changed
   */
  private boolean addEdgesToPrecisionForState(
      ARGReachedSet pReached,
      ARGState pState,
      ImmutableSet<CFAEdge> edges)
      throws InterruptedException {

    ThreadAbstractionPrecision precision =
        Precisions.extractPrecisionByType(
            pReached.asReachedSet().getPrecision(pState),
            ThreadAbstractionPrecision.class);
    ThreadAbstractionPrecision newPrecision = precision.copyWith(edges);
    edgesAdded.setNextValue(newPrecision.size() - precision.size());
    pReached.removeSubtree(
        pState,
        newPrecision,
        Predicates.instanceOf(ThreadAbstractionPrecision.class));
    return true;
  }

  private static String getThreadForProjection(ARGState projection) {
    return AbstractStates.extractStateByType(projection, ThreadAbstractionStateWithEdge.class)
        .getCurrentThread();
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(new Stats());
  }

  private class Stats implements Statistics {

    @Override
    public void printStatistics(PrintStream out, Result result, UnmodifiableReachedSet reached) {
      StatisticsWriter w0 = writingStatisticsTo(out);

      int numberOfRefinements = totalRefinement.getUpdateCount();
      if (numberOfRefinements > 0) {
        w0.put("Number of thread abstraction refinements", numberOfRefinements);
        w0.put(pathLength);
        w0.put(numFeasiblePaths);
        if (numFeasiblePaths.getValue() > 0) {
          w0.beginLevel().put(projInPath);
        }
        w0.put(numSpuriousPaths);
        if (numSpuriousPaths.getValue() > 0) {
          w0.beginLevel().put(numProjToPrec).put(numProjOrder).put(edgesAdded);
        }
        w0.put(totalRefinement);
        w0.beginLevel().put(totalOrderAnalysis).put(timeForPrecUpdate);
      }
    }

    @Override
    public String getName() {
      return "ThreadAbstractionRefiner";
    }
  }
}
