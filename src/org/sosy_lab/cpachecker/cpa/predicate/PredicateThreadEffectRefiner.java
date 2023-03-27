// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.predicate;

import static org.sosy_lab.cpachecker.util.statistics.StatisticsWriter.writingStatisticsTo;

import com.google.common.collect.ImmutableList;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.LoopStructure;
import org.sosy_lab.cpachecker.util.predicates.PathChecker;
import org.sosy_lab.cpachecker.util.predicates.interpolation.InterpolationManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.cpachecker.util.refinement.PrefixProvider;
import org.sosy_lab.cpachecker.util.refinement.PrefixSelector;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

@Options(prefix = "cpa.predicate.threadeffect")
public class PredicateThreadEffectRefiner extends PredicateCPARefiner {

  private final StatTimer totalTime = new StatTimer("Time for refinement");
  private final StatTimer repeatedCEXcheck = new StatTimer("Check if counterexample is repeated");
  private final StatTimer mainCheckTime = new StatTimer("Refinement of main path");
  private final StatTimer pathExtraction = new StatTimer("Extracting paths to effects");
  private final StatTimer effectCheckTime =
      new StatTimer("Refinement of paths with effects by thread effect");
  private final StatTimer effect1CheckTime = new StatTimer("main+effect paths");
  private final StatTimer effect2CheckTime = new StatTimer("effect+main paths");
  private final StatCounter projectionsTotal =
      new StatCounter("Total amount of projections in paths");
  private final StatInt amountOfEffects = new StatInt(StatKind.AVG, "Average amount of effects");
  private final StatCounter spuriousEffectPath =
      new StatCounter("Number of spurious paths found by thread effect");
  private final StatCounter spuriousMainPaths = new StatCounter("Number of spurious main paths");

  @Option(
    secure = true,
    name = "skipParentEffects",
    description = "Projections can be projected from states with 'parent-child' relation."
        + "Only refine paths to child states.")
  private boolean skipParentEffects = true;

  // the previously analyzed counterexample to detect repeated counterexamples
  private ImmutableList<CFANode> lastErrorPath = null;
  private Set<ImmutableList<CFANode>> lastErrorPathsToEffects = new HashSet<>();

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
    pConfig.inject(this);
  }

  @Override
  public CounterexampleInfo
      performRefinementForPath(final ARGReachedSet pReached, final ARGPath allStatesTrace)
          throws CPAException, InterruptedException {
    totalTime.start();
    try {

      ((GlobalRefinementStrategy) strategy).initializeGlobalRefinement();

      repeatedCEXcheck.start();
      final ImmutableList<CFANode> errorPath =
          allStatesTrace.asStatesList()
              .stream()
              .map(AbstractStates::extractLocation)
              .filter(x -> x != null)
              .collect(ImmutableList.toImmutableList());
      if (lastErrorPath != null && lastErrorPath.equals(errorPath)) { // repeatedCEX
        if (repeatedCEX(allStatesTrace)) {
          repeatedCEXcheck.stop();
          throw new RefinementFailedException(
              RefinementFailedException.Reason.RepeatedCounterexample,
              null);
        }
      }
      lastErrorPath = errorPath;
      repeatedCEXcheck.stop();

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

          projectionsTotal.inc();
          boolean allEffectsSpurious = true;
          for (ARGState effect : modifySetOfEffects(state.getAppliedFrom().getSecond().getProjectedFrom())) {

            // Note, not getOnePathTo, to be sure, the sequence is the same
            pathExtraction.start();
            ARGPath pathToEffect = ARGUtils.getOnePathTo(effect);
            pathExtraction.stop();

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

            // cexInfo1 and cexInfo2 are both necessary. They provide different interpolants and
            // a proper refinement root

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
          }

        }
        statesInMainPath.add(state);
      }
      effectCheckTime.stop();

      if (spuriousCexFound) {
        spuriousEffectPath.inc();
        ((GlobalRefinementStrategy) strategy).updatePrecisionAndARG();
        return CounterexampleInfo.spurious();
      }

      return cexInfo;

    } finally {
      totalTime.stop();
    }
  }

  private Collection<ARGState> modifySetOfEffects(Collection<ARGState> effects) {
    Collection<ARGState> result = effects;

    if (skipParentEffects && effects.size() > 1) {
      List<ARGState> allEffects = new ArrayList<>(effects);
      result = new ArrayList<>(effects);
      // We remove all predecessor states for every effect. We check for every effect if a path to
      // it contains any other effects. Then we remove those effects.
      Collections.sort(allEffects, Collections.reverseOrder());
      // It is optimal to build paths to child states first and remove parent states than to build
      // paths to parent states that will be removed later. Child states are built later and have
      // greater IDs.

      for (ARGState state : allEffects) {
        if (result.contains(state)) {
          ARGPath path = ARGUtils.getOnePathTo(state);

          Iterator<ARGState> iterator = result.iterator();
          while (iterator.hasNext()) {
            ARGState state2 = iterator.next();
            if (path.asStatesList().contains(state2) && !state.equals(state2)) {
              iterator.remove();
            }
          }
        }
      }
    }

    amountOfEffects.setNextValue(result.size());
    return result;
  }

  private boolean repeatedCEX(ARGPath path) {
    ImmutableList<ARGState> appliedStates =
        path.asStatesList()
            .stream()
            .filter(x -> x.getAppliedFrom() != null)
            .collect(ImmutableList.toImmutableList());

    Set<ImmutableList<CFANode>> errorPathsToEffects = new HashSet<>();

    for (ARGState state : appliedStates) {
      for (ARGState effect : state.getAppliedFrom().getSecond().getProjectedFrom()) {
        errorPathsToEffects.add(
            ARGUtils.getOnePathTo(effect)
                .asStatesList()
                .stream()
                .map(AbstractStates::extractLocation)
                .filter(x -> x != null)
                .collect(ImmutableList.toImmutableList()));
      }
    }

    if (lastErrorPathsToEffects.equals(errorPathsToEffects)) {
      return true;
    }

    lastErrorPathsToEffects = errorPathsToEffects;
    return false;
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
            .put("Number of thread effect refinements", effectCheckTime.getUpdateCount())
            .put(spuriousEffectPath);
        w0.put(projectionsTotal);
        w0.put(amountOfEffects);
        w0.put(totalTime);
        w0.beginLevel()
            .put(repeatedCEXcheck)
            .put(mainCheckTime)
            .put(effectCheckTime)
            .beginLevel()
            .put(pathExtraction)
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
