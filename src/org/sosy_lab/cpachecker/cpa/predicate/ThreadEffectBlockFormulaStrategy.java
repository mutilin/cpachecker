// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.predicate;

import static org.sosy_lab.common.collect.Collections3.transformedImmutableListCopy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaType;

public class ThreadEffectBlockFormulaStrategy extends BlockFormulaStrategy {

  public static enum EqualitiesLocation {
    MIDDLE,
    END;
  }

  protected final FormulaManagerView fmgr;
  protected final PathFormulaManager pathFmgr;
  protected final EqualitiesLocation variableEqualitiesLocation;

  protected final static String THREAD_PREFIX = "otherpath#";

  public ThreadEffectBlockFormulaStrategy(FormulaManagerView pFmgr, PathFormulaManager pPfgmr) {
    this(pFmgr, pPfgmr, EqualitiesLocation.MIDDLE);
  }

  protected ThreadEffectBlockFormulaStrategy(
      FormulaManagerView pFmgr,
      PathFormulaManager pPfgmr,
      EqualitiesLocation pEqualities) {
    fmgr = pFmgr;
    pathFmgr = pPfgmr;
    variableEqualitiesLocation = pEqualities;
  }

  private String renameVariables(String s) {
    return s.endsWith("@") ? s : THREAD_PREFIX + s;
  }

  public static String renameVariablesBack(String s) {
    return s.startsWith(THREAD_PREFIX) ? s.substring(THREAD_PREFIX.length()) : s;
  }

  @Override
  public BlockFormulas getFormulasForPath(ARGState argRoot, List<ARGState> abstractionStates)
      throws CPATransferException, InterruptedException {

    Pair<List<ARGState>, List<ARGState>> paths = divideIntoTwoPaths(abstractionStates);
    if (paths.getFirst() == null) { // No division
      return super.getFormulasForPath(argRoot, paths.getSecond());
    }
    List<BooleanFormula> formulas1 =
        modifyFormulas(super.getFormulasForPath(argRoot, paths.getFirst()).getFormulas(), 0);
    List<BooleanFormula> formulas2 =
        modifyFormulas(super.getFormulasForPath(argRoot, paths.getSecond()).getFormulas(), 1);
    SSAMap firstSsa = getSSAMap(paths.getFirst(), 0), secondSsa = getSSAMap(paths.getSecond(), 1);
    BooleanFormula variableEqualities =
        makeVariableEqualities(formulas1, formulas2, firstSsa, secondSsa);
    // TODO Branching formulas
    // currently branchingFormulas are not used as checkPath does not support interleavings and it
    // is disabled
    return new BlockFormulas(
        combineFormulas(formulas1, formulas2, variableEqualities),
        pathFmgr.buildBranchingFormula(new HashSet<>(abstractionStates)));

  }

  private Pair<List<ARGState>, List<ARGState>>
      divideIntoTwoPaths(List<ARGState> abstractionStates) {

    List<ARGState> path = new ArrayList<>(), firstPath = null;

    // InitialState is filtered from the first part, but not from the second part
    for (ARGState next : abstractionStates) {
      if (next.getParents().isEmpty()) {
        assert firstPath == null; // No more than two paths
        firstPath = path;
        path = new ArrayList<>();
      }
      path.add(next);
    }

    return Pair.of(firstPath, path);
  }

  protected List<BooleanFormula> modifyFormulas(List<BooleanFormula> formulas, int i) { // TODO
    if (i == 0) {
      return formulas;
    } else {
      return renameFormulas(formulas);
    }
  }

  private List<BooleanFormula> renameFormulas(List<BooleanFormula> formulas) {
    return transformedImmutableListCopy(
        formulas,
        f -> fmgr.renameFreeVariablesAndUFs(f, this::renameVariables));
  }

  protected SSAMap getSSAMap(List<ARGState> path, int i) {
    return AbstractStates
        .extractStateByType(path.get(path.size() - 1), PredicateAbstractState.class)
        .getPathFormula()
        .getSsa();
  }

  private BooleanFormula makeVariableEqualities(
      List<BooleanFormula> formulas1,
      List<BooleanFormula> formulas2,
      SSAMap lastSsa1,
      SSAMap lastSsa2) {

    BooleanFormula variableEqualities = fmgr.getBooleanFormulaManager().makeTrue();

    for (String varName : lastSsa1.allVariables()) {
      if (lastSsa2.containsVariable(varName) && additionalCheck(varName)) {
        FormulaType<Formula> formulaType1, formulaType2;
        formulaType1 = getVariableType(varName, formulas1);
        formulaType2 = getVariableType(renameVariables(varName), formulas2);

        if (formulaType1 != null && formulaType2 != null && formulaType1 == formulaType2) {
          Formula var1 = fmgr.makeVariable(formulaType1, varName, lastSsa1.getIndex(varName));
          Formula var2 =
              fmgr.makeVariable(formulaType2, renameVariables(varName), lastSsa2.getIndex(varName));
          variableEqualities = fmgr.makeAnd(variableEqualities, fmgr.makeEqual(var1, var2));
        }
      }
    }

    return variableEqualities;
  }

  protected boolean additionalCheck(@SuppressWarnings("unused") String varName) {
    return true;
  }

  protected List<BooleanFormula> combineFormulas(
      List<BooleanFormula> formulas1,
      List<BooleanFormula> formulas2,
      BooleanFormula variableEqualities) {
    List<BooleanFormula> allFormulas = new ArrayList<>(formulas1);
    allFormulas.addAll(formulas2);
    int index = 0;
    if (variableEqualitiesLocation == EqualitiesLocation.MIDDLE) {
      index = formulas1.size();
    } else if (variableEqualitiesLocation == EqualitiesLocation.END) {
      index = allFormulas.size() - 1;
    }
    BooleanFormula conjunction = fmgr.makeAnd(allFormulas.get(index), variableEqualities);
    allFormulas.set(index, conjunction);
    return allFormulas;
  }

  private FormulaType<Formula> getVariableType(String name, List<BooleanFormula> listOfFormulas) {
    for (BooleanFormula f : listOfFormulas) {
      for (String s : fmgr.extractVariableNames(f)) {
        if (s.startsWith(name + "@")) {
          return fmgr.getFormulaType(fmgr.extractVariables(f).get(s));
        }
      }
    }
    return null;
  }
}
