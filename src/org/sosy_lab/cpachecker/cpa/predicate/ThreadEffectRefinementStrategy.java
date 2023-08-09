// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.predicate;

import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.common.collect.Collections3.transformedImmutableListCopy;

import java.util.ArrayList;
import java.util.List;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.usage.refinement.PredicateRefinerAdapter.UsageStatisticsRefinementStrategy;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BooleanFormula;

public class ThreadEffectRefinementStrategy extends PredicateAbstractionGlobalRefinementStrategy {

  public ThreadEffectRefinementStrategy(
      Configuration pConfig,
      LogManager pLogger,
      PredicateAbstractionManager pPredAbsMgr,
      Solver pSolver)
      throws InvalidConfigurationException {
    super(pConfig, pLogger, pPredAbsMgr, pSolver);
  }

  @Override
  public boolean performRefinement(
      ARGReachedSet pReached,
      List<ARGState> pAbstractionStatesTrace,
      List<BooleanFormula> pInterpolants,
      boolean pRepeatedCounterexample)
      throws CPAException, InterruptedException {

    if (!(this instanceof UsageStatisticsRefinementStrategy)) {
      // renaming variables back
      pInterpolants =
          new ArrayList<>(
              transformedImmutableListCopy(
                  pInterpolants,
                  f -> fmgr.renameFreeVariablesAndUFs(
                      f,
                      ThreadEffectBlockFormulaStrategy::renameVariablesBack)));
    }

    return super.performRefinement(
        pReached,
        pAbstractionStatesTrace,
        pInterpolants,
        pRepeatedCounterexample);
  }

  @Override
  public List<ARGState> filterAbstractionStates(ARGPath pPath) {
    List<ARGState> result =
        from(pPath.asStatesList()).skip(1)
            .filter(PredicateAbstractState::containsAbstractionState)
            .toList();

    return result;
  }

  public PredicatePrecision getNewPrecision() {
    PredicatePrecision newPrecision = PredicatePrecision.empty();
    return newPrecision.addLocalPredicates(newPredicates.entries());
  }

}
