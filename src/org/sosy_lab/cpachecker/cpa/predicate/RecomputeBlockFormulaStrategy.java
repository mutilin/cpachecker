// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.java_smt.api.BooleanFormula;

public class RecomputeBlockFormulaStrategy extends BlockFormulaStrategy {

  private final PathFormulaManager pfmgr;

  public RecomputeBlockFormulaStrategy(final PathFormulaManager pPathFormulaManager) {
    pfmgr = pPathFormulaManager;
  }

  @Override
  BlockFormulas getFormulasForPath(ARGState argRoot, List<ARGState> abstractionStates)
      throws CPATransferException, InterruptedException {
    List<BooleanFormula> formulas = new ArrayList<>(abstractionStates.size());

    List<CFAEdge> partialPath;
    ARGState current = argRoot;
    PathFormula previousPathFormula = pfmgr.makeEmptyPathFormula();
    for (ARGState next : abstractionStates) {
      final ARGState start = current;
      // Quick hack for TM experiments
      // partialPath = ARGUtils.getOnePathFromTo((x) -> x == start, next).getFullPath();
      partialPath = start.getEdgesToChild(next);
      PathFormula partialFormula = pfmgr.makeEmptyPathFormula(previousPathFormula);
      for (CFAEdge edge : partialPath) {
        partialFormula = pfmgr.makeAnd(partialFormula, edge);
      }
      formulas.add(partialFormula.getFormula());
      previousPathFormula = partialFormula;
      current = next;
    }
    return new BlockFormulas(formulas);
  }
}
