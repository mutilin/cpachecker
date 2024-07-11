// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.refinement;

import static com.google.common.collect.FluentIterable.from;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.cpa.arg.ARGBasedRefiner;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicatePrecision;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.VariableIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class MemoryModelRefiner
    extends WrappedConfigurableRefinementBlock<
        Pair<ExtendedARGPath, ExtendedARGPath>, Pair<ExtendedARGPath, ExtendedARGPath>> {

  private final StatTimer totalTime = new StatTimer("Time for MemoryModel refiner");
  private final StatTimer refiningPaths = new StatTimer("Refining joined paths");
  private final StatCounter numberOfRefinements = new StatCounter("Number of refinements");
  private final StatCounter spuriousPaths = new StatCounter("Number of spuriuos paths found by MM");

  ARGBasedRefiner refiner;
  MemoryModelRefinementStrategy strategy;

  protected MemoryModelRefiner(
      ConfigurableRefinementBlock<Pair<ExtendedARGPath, ExtendedARGPath>> pWrapper,
      ARGBasedRefiner pRefiner,
      MemoryModelRefinementStrategy pStrategy) {
    super(pWrapper);
    refiner = pRefiner;
    strategy = pStrategy;
  }

  @Override
  public RefinementResult performBlockRefinement(Pair<ExtendedARGPath, ExtendedARGPath> pInput)
      throws CPAException, InterruptedException {

    totalTime.start();
    numberOfRefinements.inc();
    try {

      ExtendedARGPath firstPath = pInput.getFirst();
      ExtendedARGPath secondPath = pInput.getSecond();

      AbstractIdentifier id = firstPath.getUsageInfo().getId();
      if (!id.equals(secondPath.getUsageInfo().getId()) || !id.isDereferenced()) {
        return wrappedRefiner.performBlockRefinement(pInput);
      }
      strategy.addVariableID(extractVarId(id));

      storeInfo(firstPath, secondPath);

      strategy.initializeGlobalRefinement();
      CounterexampleInfo counterexampleInfo =
          refine(firstPath.asStatesList(), secondPath.asStatesList());

      strategy.swapPathFormulas(); // to match the order of paths; TODO: find permanent solution
      CounterexampleInfo counterexampleInfo2 =
          refine(secondPath.asStatesList(), firstPath.asStatesList());

      RefinementResult result = RefinementResult.createFalse();
      if (counterexampleInfo != null && counterexampleInfo.isSpurious()) {
        assert counterexampleInfo2.isSpurious();
        spuriousPaths.inc();
        PredicatePrecision lastPrecision = strategy.getNewPrecision();
        List<ARGState> allStates = new ArrayList<>(secondPath.asStatesList());
        allStates.addAll(firstPath.asStatesList());
        List<ARGState> affectedStates = getAffectedStates(lastPrecision, allStates);

        if (!affectedStates.isEmpty()) {
          result.addInfo(MemoryModelRefiner.class, affectedStates);
          assert lastPrecision != null && !lastPrecision.isEmpty();
          result.addPrecision(lastPrecision);
        }
      } else {
        result = wrappedRefiner.performBlockRefinement(pInput);
      }
      strategy.resetGlobalRefinement();
      return result;

    } finally {
      totalTime.stop();
    }
  }

  private List<ARGState> getAffectedStates(PredicatePrecision prec, List<ARGState> pPath) {
    Set<CFANode> nodes = prec.getLocalPredicates().keySet();
    return from(pPath).filter(s -> nodes.contains(AbstractStates.extractLocation(s))).toList();
  }

  private CounterexampleInfo refine(List<ARGState> states1, List<ARGState> states2)
      throws CPAException, InterruptedException {

    List<ARGState> allStates = new ArrayList<>(states1);
    allStates.addAll(states2);
    ARGPath newPath = new ARGPath(allStates);

    refiningPaths.start();
    CounterexampleInfo counterexampleInfo = refiner.performRefinementForPath(null, newPath);
    refiningPaths.stop();
    return counterexampleInfo;
  }

  private void storeInfo(ExtendedARGPath path1, ExtendedARGPath path2) {
    PredicateAbstractState state1 =
        AbstractStates.extractStateByType(
            path1.getUsageInfo().getKeyState(), PredicateAbstractState.class);
    PredicateAbstractState state2 =
        AbstractStates.extractStateByType(
            path2.getUsageInfo().getKeyState(), PredicateAbstractState.class);

    strategy.addPathFormulas(state1.getPathFormula(), state2.getPathFormula());
  }

  private VariableIdentifier extractVarId(AbstractIdentifier id) {
    assert (id instanceof VariableIdentifier || id instanceof StructureIdentifier);
    if (id instanceof VariableIdentifier) {
      return (VariableIdentifier) id;
    } else { // id instanceof StructureIdentifier
      return extractVarId(((StructureIdentifier) id).getOwner());
    }
  }

  @Override
  protected void handleFinishSignal(Class<? extends RefinementInterface> pCallerClass) {
    if (pCallerClass.equals(IdentifierIterator.class)) {
      strategy.resetGlobalRefinement();
    }
  }

  @Override
  public void printStatistics(StatisticsWriter pOut) {
    pOut.spacer().put(totalTime).put(numberOfRefinements);
    if (numberOfRefinements.getUpdateCount() > 0) {
      pOut.put(refiningPaths).put(spuriousPaths);
    }
    wrappedRefiner.printStatistics(pOut);
  }
}
