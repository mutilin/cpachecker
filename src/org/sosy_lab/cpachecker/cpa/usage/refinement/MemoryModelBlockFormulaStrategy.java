// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.refinement;

import java.util.ArrayList;
import java.util.List;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.predicate.ThreadEffectBlockFormulaStrategy;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.java_smt.api.BooleanFormula;

public class MemoryModelBlockFormulaStrategy extends ThreadEffectBlockFormulaStrategy {

  private final MemoryModelRefinementStrategy strategy;

  public MemoryModelBlockFormulaStrategy(
      FormulaManagerView pFmgr,
      PathFormulaManager pPfgmr,
      MemoryModelRefinementStrategy pStrategy) {
    super(pFmgr, pPfgmr, EqualitiesLocation.END);
    strategy = pStrategy;
  }

  // Method is necessary
  @Override
  protected List<BooleanFormula> modifyFormulas(List<BooleanFormula> formulas, int i) {
    BooleanFormula conjunction =
        fmgr.makeAnd(formulas.get(formulas.size() - 1), strategy.getPathFormula(i).getFormula());
    List<BooleanFormula> newFormulas = new ArrayList<>(formulas);
    newFormulas.set(formulas.size() - 1, conjunction);
    return super.modifyFormulas(newFormulas, i);
  }

  // Returns different map. Doesn't seem to affect the results
  @Override
  protected SSAMap getSSAMap(List<ARGState> path, int i) {
    return strategy.getPathFormula(i).getSsa();
  }

  @Override
  protected boolean additionalCheck(String varName) {
    // strategy.getVariableName() can be "a", corresponding varName is "f::a"
    if (varName.contains("::")) {
      varName = varName.substring(varName.indexOf(":") + 2);
    }
    return varName.equals(strategy.getVariableName());
  }
}
