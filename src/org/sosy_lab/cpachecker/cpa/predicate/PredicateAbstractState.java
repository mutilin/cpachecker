/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.predicate;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sosy_lab.cpachecker.util.AbstractStates.extractStateByType;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.matheclipse.core.reflection.system.Solve;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.CParser;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.parser.Scope;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.FormulaReportingState;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;
import org.sosy_lab.cpachecker.core.interfaces.NonMergeableAbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Partitionable;
import org.sosy_lab.cpachecker.core.interfaces.Property;
import org.sosy_lab.cpachecker.core.interfaces.Targetable;
import org.sosy_lab.cpachecker.cpa.arg.Splitable;
import org.sosy_lab.cpachecker.cpa.automaton.CParserUtils;
import org.sosy_lab.cpachecker.cpa.automaton.InvalidAutomatonException;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.predicates.AbstractionFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap.SSAMapBuilder;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.CtoFormulaTypeHandler;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSet;
import org.sosy_lab.cpachecker.util.predicates.smt.ArrayFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.BooleanFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.ArrayFormula;
import org.sosy_lab.java_smt.api.BitvectorFormula;
import org.sosy_lab.java_smt.api.BooleanFormula;

import java.io.Serializable;
import java.util.Collection;
import org.sosy_lab.java_smt.api.FormulaType;
import org.sosy_lab.java_smt.api.SolverException;

/**
 * AbstractState for Symbolic Predicate Abstraction CPA
 */
public abstract class PredicateAbstractState
    implements AbstractQueryableState, Partitionable, Serializable, Splitable, Targetable {

  private static final long serialVersionUID = -265763837277453447L;

  public final static Predicate<AbstractState> CONTAINS_ABSTRACTION_STATE =
      Predicates.compose(
          PredicateAbstractState::isAbstractionState,
          AbstractStates.toState(PredicateAbstractState.class));



  public static PredicateAbstractState getPredicateState(AbstractState pState) {
    return checkNotNull(extractStateByType(pState, PredicateAbstractState.class));
  }

  /**
   * Marker type for abstract states that were generated by computing an
   * abstraction.
   */
  private static class AbstractionState extends PredicateAbstractState implements NonMergeableAbstractState, Graphable, FormulaReportingState {

    private static final long serialVersionUID = 8341054099315063986L;

    private AbstractionState(
        PathFormula pf,
        AbstractionFormula pA,
        PersistentMap<CFANode, Integer> pAbstractionLocations,
        boolean pIsTarget,
        PathFormula pErrorPathFormula) {
      super(pf, pA, pAbstractionLocations, pIsTarget, pErrorPathFormula);
      // Check whether the pathFormula of an abstraction element is just "true".
      // partialOrder relies on this for optimization.
      //Preconditions.checkArgument(bfmgr.isTrue(pf.getFormula()));
      // Check uncommented because we may pre-initialize the path formula
      // with an invariant.
      // This is no problem for the partial order because the invariant
      // is always the same when the location is the same.
    }

    @Override
    public Object getPartitionKey() {
      if (super.abstractionFormula.isFalse()) {
        // put unreachable states in a separate partition to avoid merging
        // them with any reachable states
        return Boolean.FALSE;
      } else {
        return null;
      }
    }

    @Override
    public boolean isAbstractionState() {
      return true;
    }

    @Override
    public String toString() {
      return "Abstraction location: true, Abstraction: " + super.abstractionFormula;
    }

    @Override
    public String toDOTLabel() {
      return super.abstractionFormula.toString();
    }

    @Override
    public boolean shouldBeHighlighted() {
      return true;
    }

    @Override
    public BooleanFormula getFormulaApproximation(FormulaManagerView pManager) {
      return super.abstractionFormula.asFormulaFromOtherSolver(pManager);
    }
  }

  private static class NonAbstractionState extends PredicateAbstractState {
    private static final long serialVersionUID = -6912172362012773999L;
    /**
     * The abstract state this element was merged into.
     * Used for fast coverage checks.
     */
    private transient PredicateAbstractState mergedInto = null;

    private NonAbstractionState(
        PathFormula pF,
        AbstractionFormula pA,
        PersistentMap<CFANode, Integer> pAbstractionLocations,
        boolean pIsTarget,
        PathFormula pErrorPathFormula) {
      super(pF, pA, pAbstractionLocations, pIsTarget, pErrorPathFormula);
    }

    @Override
    public boolean isAbstractionState() {
      return false;
    }

    @Override
    PredicateAbstractState getMergedInto() {
      return mergedInto;
    }

    @Override
    void setMergedInto(PredicateAbstractState pMergedInto) {
      Preconditions.checkNotNull(pMergedInto);
      mergedInto = pMergedInto;
    }

    @Override
    public Object getPartitionKey() {
      return getAbstractionFormula();
    }

    @Override
    public String toString() {
      return "Abstraction location: false";
    }
  }

  public static PredicateAbstractState mkAbstractionState(
      PathFormula pF,
      AbstractionFormula pA,
      PersistentMap<CFANode, Integer> pAbstractionLocations,
      boolean pIsTarget,
      PathFormula pErrorPathFormula) {
    return new AbstractionState(pF, pA, pAbstractionLocations, pIsTarget, pErrorPathFormula);
  }

  public static PredicateAbstractState mkAbstractionState(
      PathFormula pF,
      AbstractionFormula pA,
      PersistentMap<CFANode, Integer> pAbstractionLocations) {
    return new AbstractionState(pF, pA, pAbstractionLocations, false, null);
  }

  public static PredicateAbstractState mkNonAbstractionStateWithNewPathFormula(PathFormula pF,
      PredicateAbstractState oldState) {
    return new NonAbstractionState(
        pF,
        oldState.getAbstractionFormula(),
        oldState.getAbstractionLocationsOnPath(),
        oldState.isTarget(),
        oldState.getErrorPathFormula());
  }

  public static PredicateAbstractState copyNonAbsState(PredicateAbstractState oldState) {
    return new NonAbstractionState(
        oldState.getPathFormula(),
        oldState.getAbstractionFormula(),
        oldState.getAbstractionLocationsOnPath(),
        oldState.isTarget(),
        oldState.getErrorPathFormula());
  }

  public static PredicateAbstractState copyAbsState(PredicateAbstractState oldState) {
    return new AbstractionState(
        oldState.getPathFormula(),
        oldState.getAbstractionFormula(),
        oldState.getAbstractionLocationsOnPath(),
        oldState.isTarget(),
        oldState.getErrorPathFormula());
  }

  public static PredicateAbstractState mkNonAbstractionState(
      PathFormula pF,
      AbstractionFormula pA,
      PersistentMap<CFANode, Integer> pAbstractionLocations,
      boolean pIsTarget,
      PathFormula pErrorPathFormula) {
    return new NonAbstractionState(pF, pA, pAbstractionLocations, pIsTarget, pErrorPathFormula);
  }

  public static PredicateAbstractState mkNonAbstractionState(
      PathFormula pF,
      AbstractionFormula pA,
      PersistentMap<CFANode, Integer> pAbstractionLocations) {
    return new NonAbstractionState(pF, pA, pAbstractionLocations, false, null);
  }

  /** The path formula for the path from the last abstraction node to this node.
   * it is set to true on a new abstraction location and updated with a new
   * non-abstraction location */
  private PathFormula pathFormula;

  /** The abstraction which is updated only on abstraction locations */
  private AbstractionFormula abstractionFormula;

  /** How often each abstraction location was visited on the path to the current state. */
  private final transient PersistentMap<CFANode, Integer> abstractionLocations;

  private CParser parser;
  private Scope scope;
  private final boolean isTarget;
  private PathFormula errorPathFormula;

  private PredicateAbstractState(
      PathFormula pf,
      AbstractionFormula a,
      PersistentMap<CFANode, Integer> pAbstractionLocations,
      boolean pIsTarget,
      PathFormula pErrorPathFormula) {
    this.pathFormula = pf;
    this.abstractionFormula = a;
    this.abstractionLocations = pAbstractionLocations;
    this.isTarget = pIsTarget;
    this.errorPathFormula = pErrorPathFormula;
  }

  public abstract boolean isAbstractionState();

  PredicateAbstractState getMergedInto() {
    throw new UnsupportedOperationException("Assuming wrong PredicateAbstractStates were merged!");
  }

  /**
   * @param pMergedInto the state that should be set as merged
   */
  void setMergedInto(PredicateAbstractState pMergedInto) {
    throw new UnsupportedOperationException("Merging wrong PredicateAbstractStates!");
  }

  public PersistentMap<CFANode, Integer> getAbstractionLocationsOnPath() {
    return abstractionLocations;
  }

  public AbstractionFormula getAbstractionFormula() {
    return abstractionFormula;
  }

  public PathFormula getErrorPathFormula(){
    return errorPathFormula;
  }

  /**
   * Replace the abstraction formula part of this element.
   * THIS IS POTENTIALLY UNSOUND!
   *
   * Call this function only during refinement if you also change all successor
   * elements and consider the coverage relation.
   */
  void setAbstraction(AbstractionFormula pAbstractionFormula) {
    if (isAbstractionState()) {
      abstractionFormula = checkNotNull(pAbstractionFormula);
    } else {
      throw new UnsupportedOperationException("Changing abstraction formula is only supported for abstraction elements");
    }
  }

  public PathFormula getPathFormula() {
    return pathFormula;
  }

  protected Object readResolve() {
    if (this instanceof AbstractionState) {
      // consistency check
      /*Pair<String,Integer> splitName;
      FormulaManagerView mgr = GlobalInfo.getInstance().getFormulaManager();
      SSAMap ssa = pathFormula.getSsa();

      for (String var : mgr.extractFreeVariableMap(abstractionFormula.asInstantiatedFormula()).keySet()) {
        splitName = FormulaManagerView.parseName(var);

        if (splitName.getSecond() == null) {
          if (ssa.containsVariable(splitName.getFirst())) {
            throw new StreamCorruptedException("Proof is corrupted, abort reading");
          }
          continue;
        }

        if(splitName.getSecond()!=ssa.getIndex(splitName.getFirst())) {
          throw new StreamCorruptedException("Proof is corrupted, abort reading");
        }
      }*/

      return new AbstractionState(
          pathFormula,
          abstractionFormula,
          PathCopyingPersistentTreeMap.<CFANode, Integer> of(),
          isTarget,
          errorPathFormula);
    }
    return new NonAbstractionState(
        pathFormula,
        abstractionFormula,
        PathCopyingPersistentTreeMap.<CFANode, Integer>of(),
        isTarget,
        errorPathFormula);
  }

  @Override
  public AbstractState forkWithReplacements(Collection<AbstractState> pReplacementStates) {
    for (AbstractState state : pReplacementStates) {
      if (state instanceof PredicateAbstractState) {
        return state;
      }
    }
    return this;
  }

  private static boolean startsWithIgnoreCase(String s, String prefix) {
    s = s.substring(0, prefix.length());
    return s.equalsIgnoreCase(prefix);
  }

  @Override
  public boolean isTarget() {
    return isTarget;
  }

  @Override
  public @NonNull Set<Property> getViolatedProperties() throws IllegalStateException {
    return Sets.newHashSet(
        new Property() {
          @Override
          public String toString() {
            return pathFormula.toString();
          }
        });
  }

  @Override
  public boolean checkProperty(String property) throws InvalidQueryException {
    checkNotNull(property);
    PredicateCPA predicateCPA = getPredicateCPA();

    PathFormulaManager pfMgr = predicateCPA.getPathFormulaManager();
    FormulaManagerView fMgr = predicateCPA.getSolver().getFormulaManager();
    ArrayFormulaManagerView afMgr = fMgr.getArrayFormulaManager();
    BooleanFormulaManagerView bfMgr = fMgr.getBooleanFormulaManager();
    CtoFormulaTypeHandler typeHandler = new CtoFormulaTypeHandler(
        predicateCPA.logger,
        predicateCPA.getCfa().getMachineModel());
    Solver solver = predicateCPA.getSolver();
    //PredicateAbstractionManager abstractionManager = predicateCPA.getPredicateManager();

    if (startsWithIgnoreCase(property, "setcontains(") ||
        startsWithIgnoreCase(property, "!setcontains(") ||
        startsWithIgnoreCase(property, "setempty(") ||
        startsWithIgnoreCase(property, "!setempty(")) {

      if (!property.endsWith(")")) {
        throw new InvalidQueryException(property + " should end with \")\"");
      }

      String paramsStr =
          property.substring(property.indexOf('(') + 1, property.lastIndexOf(')'));

      List<String> params = Splitter.on(',').trimResults().splitToList(paramsStr);
      String setName = params.get(0);

      BitvectorFormula arrayIndex = null;
      if (!startsWithIgnoreCase(property, "setempty(") &&
          !startsWithIgnoreCase(property, "!setempty(")) {
        String mutexExp = params.get(1);

        arrayIndex = parseMutexExpression(
            mutexExp,
            pathFormula,
            pfMgr,
            parser,
            scope);
      }

      ArrayFormula<BitvectorFormula, BooleanFormula> arrayFormula =
          makeArray(setName, fMgr, typeHandler);

      boolean unsat = false;

      if (startsWithIgnoreCase(property, "setcontains(")){
        BooleanFormula set_select =
            afMgr.select(arrayFormula, arrayIndex);

        BooleanFormula set_contains = bfMgr.equivalence(set_select, bfMgr.makeTrue());

        // bitwise axioms??
        BooleanFormula resultFormula0 = bfMgr.and(pathFormula.getFormula(), set_contains);
        BooleanFormula resultFormula = bfMgr.and(abstractionFormula.asInstantiatedFormula(), resultFormula0);
        try {
          unsat = solver.isUnsat(resultFormula);
        } catch (SolverException | InterruptedException pE) {
          pE.printStackTrace();
        }
      }
      else if (startsWithIgnoreCase(property, "!setcontains(")){
        BooleanFormula set_select =
            afMgr.select(arrayFormula, arrayIndex);

        BooleanFormula set_not_contains = bfMgr.equivalence(set_select, bfMgr.makeFalse());

        BooleanFormula resultFormula0 = bfMgr.and(pathFormula.getFormula(), set_not_contains);
        BooleanFormula resultFormula = bfMgr.and(abstractionFormula.asInstantiatedFormula(), resultFormula0);
        try {
          unsat = solver.isUnsat(resultFormula);
        } catch (SolverException | InterruptedException pE) {
          pE.printStackTrace();
        }
      }
      else if (startsWithIgnoreCase(property, "setempty(")){
        ArrayFormula<BitvectorFormula, BooleanFormula> initialArrayFormula =
            makeArray(toInitialSetName(setName), fMgr, typeHandler, 1);

        BooleanFormula set_empty = afMgr.equivalence(arrayFormula, initialArrayFormula);

        BooleanFormula resultFormula0 = bfMgr.and(pathFormula.getFormula(), set_empty);
        BooleanFormula resultFormula = bfMgr.and(abstractionFormula.asInstantiatedFormula(), resultFormula0);
        try {
          unsat = solver.isUnsat(resultFormula);
        } catch (SolverException | InterruptedException pE) {
          pE.printStackTrace();
        }
      }
      else if (startsWithIgnoreCase(property, "!setempty(")){
        ArrayFormula<BitvectorFormula, BooleanFormula> initialArrayFormula =
            makeArray(toInitialSetName(setName), fMgr, typeHandler, 1);

        BooleanFormula set_empty = afMgr.equivalence(arrayFormula, initialArrayFormula);
        BooleanFormula set_not_empty = bfMgr.not(set_empty);

        BooleanFormula resultFormula0 = bfMgr.and(pathFormula.getFormula(), set_not_empty);
        BooleanFormula resultFormula = bfMgr.and(abstractionFormula.asInstantiatedFormula(), resultFormula0);
        try {
          unsat = solver.isUnsat(resultFormula);
        } catch (SolverException | InterruptedException pE) {
          pE.printStackTrace();
        }
      }

      return !unsat;
    } else {
      return false;
    }
  }

  @Override
  public void modifyProperty(String pModification) throws InvalidQueryException {
    if (this.isTarget){
      // we shouldn't modify target state
      return;
    }

    String s = checkNotNull(pModification);
    //System.out.println(s);

    PredicateCPA predicateCPA = getPredicateCPA();

    PathFormulaManager pfMgr = predicateCPA.getPathFormulaManager();
    FormulaManagerView fMgr = predicateCPA.getSolver().getFormulaManager();
    ArrayFormulaManagerView afMgr = fMgr.getArrayFormulaManager();
    BooleanFormulaManagerView bfMgr = fMgr.getBooleanFormulaManager();
    CtoFormulaTypeHandler typeHandler = new CtoFormulaTypeHandler(
        predicateCPA.logger,
        predicateCPA.getCfa().getMachineModel());

    // todo split by ;
    if (startsWithIgnoreCase(pModification, "setinit(") ||
        startsWithIgnoreCase(pModification, "setadd(") ||
        startsWithIgnoreCase(pModification, "setremove(") ||
        startsWithIgnoreCase(pModification, "setcontains(") ||
        startsWithIgnoreCase(pModification, "!setcontains(") ||
        startsWithIgnoreCase(pModification, "setempty(") ||
        startsWithIgnoreCase(pModification, "!setempty(")) {
      //if (!pModification.endsWith(")")) {
      //  throw new InvalidQueryException(pModification + " should end with \")\"");
      //}

      String secondArg = pModification.contains(";")
          ? pModification.substring(pModification.indexOf(';'))
          : "";

      //todo: error text
      boolean elseError = secondArg.contains("elseerror");

      String paramsStr =
          pModification.substring(pModification.indexOf('(') + 1, pModification.lastIndexOf(')'));

      List<String> params = Splitter.on(',').trimResults().splitToList(paramsStr);
      String setName = params.get(0);

      BitvectorFormula arrayIndex = null;
      if (!startsWithIgnoreCase(pModification, "setempty(") &&
          !startsWithIgnoreCase(pModification, "!setempty(")) {
        String mutexExp = params.get(1);

        arrayIndex = parseMutexExpression(
            mutexExp,
            pathFormula,
            pfMgr,
            parser,
            scope);
      }

      ArrayFormula<BitvectorFormula, BooleanFormula> arrayFormula =
          makeArray(setName, fMgr, typeHandler);

      if (startsWithIgnoreCase(pModification, "setadd(")) {
        ArrayFormula<BitvectorFormula, BooleanFormula> set_store =
            afMgr.store(arrayFormula, arrayIndex, bfMgr.makeTrue());

        setSsa(
            setName,
            pathFormula.getSsa().getType(setName),
            pathFormula.getSsa().builder().getFreshIndex(setName));

        ArrayFormula<BitvectorFormula, BooleanFormula> arrayFormula1 =
            makeArray(setName, fMgr, typeHandler);

        BooleanFormula set_add =
            afMgr.equivalence(arrayFormula1, set_store);

        makeAndWithoutInst(set_add, bfMgr, false);
      }
      else if (startsWithIgnoreCase(pModification, "setremove(")){
        ArrayFormula<BitvectorFormula, BooleanFormula> set_store =
            afMgr.store(arrayFormula, arrayIndex, bfMgr.makeFalse());

        setSsa(
            setName,
            pathFormula.getSsa().getType(setName),
            pathFormula.getSsa().builder().getFreshIndex(setName));

        ArrayFormula<BitvectorFormula, BooleanFormula> arrayFormula1 =
            makeArray(setName, fMgr, typeHandler);

        BooleanFormula set_remove =
            afMgr.equivalence(arrayFormula1, set_store);

        makeAndWithoutInst(set_remove, bfMgr, false);
      }
      else if (startsWithIgnoreCase(pModification, "setcontains(")){
        BooleanFormula set_select =
            afMgr.select(arrayFormula, arrayIndex);

        BooleanFormula set_contains = bfMgr.equivalence(set_select, bfMgr.makeTrue());

        makeAndWithoutInst(set_contains, bfMgr, elseError);
      }
      else if (startsWithIgnoreCase(pModification, "!setcontains(")){
        BooleanFormula set_select =
            afMgr.select(arrayFormula, arrayIndex);

        BooleanFormula set_not_contains = bfMgr.equivalence(set_select, bfMgr.makeFalse());

        makeAndWithoutInst(set_not_contains, bfMgr, elseError);
      }
      else if (startsWithIgnoreCase(pModification, "setempty(")){
        ArrayFormula<BitvectorFormula, BooleanFormula> initialArrayFormula =
            makeArray(toInitialSetName(setName), fMgr, typeHandler, 1);

        BooleanFormula set_empty = afMgr.equivalence(arrayFormula, initialArrayFormula);

        makeAndWithoutInst(set_empty, bfMgr, elseError);
      }
      else if (startsWithIgnoreCase(pModification, "!setempty(")){
        ArrayFormula<BitvectorFormula, BooleanFormula> initialArrayFormula =
            makeArray(toInitialSetName(setName), fMgr, typeHandler, 1);

        BooleanFormula set_empty = afMgr.equivalence(arrayFormula, initialArrayFormula);
        BooleanFormula set_not_empty = bfMgr.not(set_empty);

        makeAndWithoutInst(set_not_empty, bfMgr, elseError);
      }
      else if (startsWithIgnoreCase(pModification, "setinit(")){
        // todo: only one init
        ArrayFormula<BitvectorFormula, BooleanFormula> initialArrayFormula =
            makeArray(toInitialSetName(setName), fMgr, typeHandler, 1);

        BooleanFormula set_select =
            afMgr.select(arrayFormula, arrayIndex);

        BooleanFormula set_init = afMgr.equivalence(initialArrayFormula, arrayFormula);
        BooleanFormula set_not_contains = bfMgr.equivalence(set_select, bfMgr.makeFalse());
        BooleanFormula and = bfMgr.and(set_not_contains, set_init);

        makeAndWithoutInst(and, bfMgr, elseError);
      }

    }
  }

  @Nullable
  private PredicateCPA getPredicateCPA() {
    PredicateCPA predicateCPA = null;
    try {
      predicateCPA = CPAs.retrieveCPAOrFail(
          GlobalInfo.getInstance().getCPA().get(),
          PredicateCPA.class,
          this.getClass());
    } catch (InvalidConfigurationException pE) {
      pE.printStackTrace();
    }
    return predicateCPA;
  }

  private static String toInitialSetName(String name){
    return name + "_init";
  }

  private ArrayFormula<BitvectorFormula, BooleanFormula> makeArray(
      String name,
      FormulaManagerView fMgr,
      CtoFormulaTypeHandler typeHandler) {
    if (!pathFormula.getSsa().containsVariable(name)) {
      setSsa(name, CNumericTypes.BOOL, 1);
    }

    return fMgr.makeVariable(
        FormulaType.getArrayType(
            (FormulaType<BitvectorFormula>) typeHandler.getPointerType(),
            FormulaType.BooleanType),
        name,
        pathFormula.getSsa().getIndex(name));
  }

  private ArrayFormula<BitvectorFormula, BooleanFormula> makeArray(
      String name,
      FormulaManagerView fMgr,
      CtoFormulaTypeHandler typeHandler,
      int ssaIndex) {
    if (!pathFormula.getSsa().containsVariable(name)) {
      setSsa(name, CNumericTypes.BOOL, ssaIndex);
    }

    return fMgr.makeVariable(
        FormulaType.getArrayType(
            (FormulaType<BitvectorFormula>) typeHandler.getPointerType(),
            FormulaType.BooleanType),
        name,
        ssaIndex);
  }

  private static BitvectorFormula parseMutexExpression(
      String mutexExp,
      PathFormula pathFormula,
      PathFormulaManager pfMgr,
      CParser parser,
      Scope scope) {
    List<CStatement> statements = new ArrayList<>();
    try {
      statements = CParserUtils.parseListOfStatements(mutexExp, parser, scope);
    } catch (InvalidAutomatonException pE) {
      pE.printStackTrace();
    }

    CFAEdge dummyEdge = new BlankEdge("",
        FileLocation.DUMMY,
        new CFANode("dummy-1"), new CFANode("dummy-2"), "Dummy Edge");

    CExpression expression = ((CExpressionStatement) statements.get(0)).getExpression();
    BitvectorFormula formula = null;
    try {
      formula =
          (BitvectorFormula) pfMgr.expressionToFormula(pathFormula, expression, dummyEdge);
    } catch (UnrecognizedCodeException pE) {
      pE.printStackTrace();
    }

    return formula;
  }

  private void makeAndWithoutInst(
      BooleanFormula pOtherFormula,
      BooleanFormulaManagerView bfmgr,
      boolean elseError) {
    SSAMap ssa = pathFormula.getSsa();
    BooleanFormula resultFormula = bfmgr.and(pathFormula.getFormula(), pOtherFormula);
    final PointerTargetSet pts = pathFormula.getPointerTargetSet();

    if (elseError) {
      BooleanFormula not = bfmgr.not(pOtherFormula);
      BooleanFormula errorFormula = bfmgr.and(pathFormula.getFormula(), not);
      if (errorPathFormula != null) {
        errorFormula = bfmgr.or(errorPathFormula.getFormula(), errorFormula);
      }
      errorPathFormula = new PathFormula(errorFormula, ssa, pts, this.pathFormula.getLength() + 1);
    }

    pathFormula = new PathFormula(resultFormula, ssa, pts, pathFormula.getLength() + 1);
  }

  private static SSAMap updateSsa(SSAMap ssa, String variable) {
    SSAMapBuilder ssaMapBuilder = ssa.builder();
    return ssaMapBuilder
        .setIndex(
            variable,
            ssa.getType(variable),
            ssaMapBuilder.getFreshIndex(variable))
        .build();
  }

  private void setSsa(
      String variable,
      CType type,
      int index) {
    SSAMapBuilder ssaMapBuilder = pathFormula.getSsa().builder();
    SSAMap newSsa = ssaMapBuilder
        .setIndex(
            variable,
            type,
            index)
        .build();

    pathFormula = new PathFormula(
        pathFormula.getFormula(),
        newSsa,
        pathFormula.getPointerTargetSet(),
        pathFormula.getLength());
  }

  public void setCParserAndScope(CParser pParser, Scope pScope){
    this.parser = pParser;
    this.scope = pScope;
  }

  @Override
  public String getCPAName() {
    return "Predicate";
  }
}