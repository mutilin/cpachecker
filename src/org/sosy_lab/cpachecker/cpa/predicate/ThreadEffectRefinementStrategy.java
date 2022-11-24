// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.predicate;

import static org.sosy_lab.common.collect.Collections3.transformedImmutableListCopy;

import java.util.List;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BooleanFormula;

public class ThreadEffectRefinementStrategy extends PredicateAbstractionGlobalRefinementStrategy {

  protected ThreadEffectRefinementStrategy(
      Configuration pConfig,
      LogManager pLogger,
      PredicateAbstractionManager pPredAbsMgr,
      Solver pSolver)
      throws InvalidConfigurationException {
    super(pConfig, pLogger, pPredAbsMgr, pSolver);
    // TODO Auto-generated constructor stub
  }

  @Override
  public boolean performRefinement(
      ARGReachedSet pReached,
      List<ARGState> pAbstractionStatesTrace,
      List<BooleanFormula> pInterpolants,
      boolean pRepeatedCounterexample)
      throws CPAException, InterruptedException {

    if (pRepeatedCounterexample) {
      throw new RefinementFailedException(
          RefinementFailedException.Reason.RepeatedCounterexample,
          null);
    }

    // renaming variables back
    List<BooleanFormula> interpolants =
        transformedImmutableListCopy(pInterpolants, f->fmgr.renameFreeVariablesAndUFs(f, ThreadEffectBlockFormulaStrategy::RenameVariablesBack));

    return super.performRefinement(
        pReached,
        pAbstractionStatesTrace,
        interpolants,
        pRepeatedCounterexample);
  }

}
