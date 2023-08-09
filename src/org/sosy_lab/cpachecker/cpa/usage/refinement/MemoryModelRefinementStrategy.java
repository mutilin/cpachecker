// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.refinement;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractionManager;
import org.sosy_lab.cpachecker.cpa.predicate.ThreadEffectRefinementStrategy;
import org.sosy_lab.cpachecker.util.identifiers.VariableIdentifier;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;

public class MemoryModelRefinementStrategy extends ThreadEffectRefinementStrategy {

  private List<PathFormula> pathFormulas;
  private String varName;

  public MemoryModelRefinementStrategy(
      Configuration pConfig,
      LogManager pLogger,
      PredicateAbstractionManager pPredAbsMgr,
      Solver pSolver)
      throws InvalidConfigurationException {
    super(pConfig, pLogger, pPredAbsMgr, pSolver);
    pathFormulas = null;
    varName = null;
  }

  public void addVariableID(VariableIdentifier pVariableID) {
    varName = pVariableID.getName();
  }

  public String getVariableName() {
    return varName;
  }

  public void addPathFormulas(PathFormula state1, PathFormula state2) {
    pathFormulas = ImmutableList.of(state1, state2);
  }

  public void swapPathFormulas() {
    pathFormulas = ImmutableList.of(pathFormulas.get(1), pathFormulas.get(0));
  }

  public PathFormula getPathFormula(int index) {
    return pathFormulas.get(index);
  }

  @Override
  public void resetGlobalRefinement() {
    pathFormulas = null;
    super.resetGlobalRefinement();
  }
}
