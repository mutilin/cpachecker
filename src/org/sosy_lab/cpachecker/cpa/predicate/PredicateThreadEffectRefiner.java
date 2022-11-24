// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.predicate;

import static org.sosy_lab.cpachecker.util.statistics.StatisticsWriter.writingStatisticsTo;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.LoopStructure;
import org.sosy_lab.cpachecker.util.predicates.PathChecker;
import org.sosy_lab.cpachecker.util.predicates.interpolation.InterpolationManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.cpachecker.util.refinement.PrefixProvider;
import org.sosy_lab.cpachecker.util.refinement.PrefixSelector;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class PredicateThreadEffectRefiner extends PredicateCPARefiner {

  private final StatTimer totalTime = new StatTimer("Time for refinement");
  private final StatTimer mainCheckTime = new StatTimer("Checking the main path");
  private final StatTimer effectCheckTime =
      new StatTimer("Checking the paths with effects");
  private final StatTimer effect1CheckTime =
      new StatTimer("Checking the main+effect paths");
  private final StatTimer effect2CheckTime =
      new StatTimer("Checking the effect+main paths");
  private final StatCounter pathsWithEffects =
      new StatCounter("Total amount of paths with effects that were checked");
  private final StatCounter spuriousEffectPath = new StatCounter("Spurious paths with effects");
  private final StatCounter spuriousMainPaths = new StatCounter("Spurious main paths");

  public PredicateThreadEffectRefiner(
      final Configuration pConfig,
      final LogManager pLogger,
      final Optional<LoopStructure> pLoopStructure,
      final BlockFormulaStrategy pBlockFormulaStrategy,
      final Solver pSolver,
      final PathFormulaManager pPfgmr,
      final InterpolationManager pInterpolationManager,
      final PathChecker pPathChecker,
      final PrefixProvider pPrefixProvider,
      final PrefixSelector pPrefixSelector,
      final PredicateCPAInvariantsManager pInvariantsManager,
      final RefinementStrategy pStrategy)
      throws InvalidConfigurationException {
    super(
        pConfig,
        pLogger,
        pLoopStructure,
        pBlockFormulaStrategy,
        pSolver,
        pPfgmr,
        pInterpolationManager,
        pPathChecker,
        pPrefixProvider,
        pPrefixSelector,
        pInvariantsManager,
        pStrategy);
  }

  @Override
  public CounterexampleInfo
      performRefinementForPath(final ARGReachedSet pReached, final ARGPath allStatesTrace)
          throws CPAException, InterruptedException {
    totalTime.start();
    try {

      ((GlobalRefinementStrategy) strategy).initializeGlobalRefinement();

      // First, we check if given path (main path) is spurious.
      mainCheckTime.start();
      CounterexampleInfo cexInfo = super.performRefinementForPath(pReached, allStatesTrace);
      mainCheckTime.stop();
      if (cexInfo.isSpurious()) {
        spuriousMainPaths.inc();
        ((GlobalRefinementStrategy) strategy).updatePrecisionAndARG();
        return cexInfo;
      }

      // For each applied state in the main path we combine a path to this state and a path to the
      // state from which a corresponding projection was projected. We then check if the new path is
      // spurious.

      List<ARGState> statesInMainPath = new ArrayList<>(); // list of all states in main path from
                                                           // the first to the current
      boolean spuriousCexFound = false;
      effectCheckTime.start();
      for (ARGState state : allStatesTrace.asStatesList()) {
        if (state.getAppliedFrom() != null) {

          boolean allEffectsSpurious = true;
          for (ARGState effect : state.getAppliedFrom().getSecond().getProjectedFrom()) {
            pathsWithEffects.inc();

            // Note, not getOnePathTo, to be sure, the sequence is the same
            ARGPath pathToEffect = ARGUtils.getOnePathTo(effect);

            List<ARGState> mainAndEffect = new ArrayList<>(statesInMainPath);
            mainAndEffect.addAll(pathToEffect.asStatesList());
            ARGPath newPath = new ARGPath(mainAndEffect);
            effect1CheckTime.start();
            CounterexampleInfo cexInfo1 = super.performRefinementForPath(pReached, newPath);
            effect1CheckTime.stop();

            mainAndEffect = new ArrayList<>(pathToEffect.asStatesList());
            mainAndEffect.addAll(statesInMainPath);
            newPath = new ARGPath(mainAndEffect);
            effect2CheckTime.start();
            CounterexampleInfo cexInfo2 = super.performRefinementForPath(pReached, newPath);
            effect2CheckTime.stop();

            // CexInfo1 and cexInfo2 provide different interpolants => both of them are necessary

            assert (cexInfo1.isSpurious() == cexInfo2.isSpurious());
            // If at least one of the effects generates feasible CEX, the state is considered
            // reachable. Otherwise, corresponding projection won't be ruled out and will be found
            // again. All effects must generate spurious CEX in order for the state to be considered
            // unreachable and precision to be updated.
            if (!cexInfo1.isSpurious()) {
              allEffectsSpurious = false;
            }
          }
          if (allEffectsSpurious) {
            spuriousCexFound = true;
            spuriousEffectPath.inc();
          }

        }
        statesInMainPath.add(state);
      }
      effectCheckTime.stop();

      if (spuriousCexFound) {
        ((GlobalRefinementStrategy) strategy).updatePrecisionAndARG();
        return CounterexampleInfo.spurious();
      }

      return cexInfo;

    } finally {
      totalTime.stop();
    }
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(new Stats());
    super.collectStatistics(pStatsCollection);
  }

  private class Stats implements Statistics {

    @Override
    public void printStatistics(PrintStream out, Result result, UnmodifiableReachedSet reached) {
      StatisticsWriter w0 = writingStatisticsTo(out);
      int numberOfRefinements = totalTime.getUpdateCount();
      if (numberOfRefinements > 0) {
        w0.put("Number of refinements", numberOfRefinements);
        w0.beginLevel()
            .put(spuriousMainPaths)
            .put(spuriousEffectPath)
            .put(
                "Feasible paths",
                numberOfRefinements - spuriousMainPaths.getValue() - spuriousEffectPath.getValue());
        w0.put(pathsWithEffects);
        w0.put(totalTime);
        w0.beginLevel()
            .put(mainCheckTime)
            .put(effectCheckTime)
            .beginLevel()
            .put(effect1CheckTime)
            .put(effect2CheckTime);
      }
    }

    @Override
    public String getName() {
      return "PredicateThreadEffect Refiner";
    }
  }
}
