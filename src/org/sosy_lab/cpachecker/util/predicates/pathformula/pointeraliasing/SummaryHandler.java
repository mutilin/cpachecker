/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ErrorConditions;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap.SSAMapBuilder;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.Constraints;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.CtoFormulaConverter;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.ExpressionToFormulaVisitor;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.Expression.Location.AliasedLocation;
import org.sosy_lab.cpachecker.util.predicates.smt.ArrayFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.BooleanFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.java_smt.api.ArrayFormula;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaType;
import org.sosy_lab.java_smt.api.visitors.DefaultFormulaVisitor;

public final class SummaryHandler {

  private final CToFormulaConverterWithPointerAliasing conv;
  private final TypeHandlerWithPointerAliasing typeHandler;
  private final CFAEdge edge;
  private final String function;
  private final SSAMapBuilder ssa;
  private final PointerTargetSetBuilder pts;
  private final Constraints constraints;
  private final ErrorConditions err;
  private final MemoryRegionManager regionMgr;
  private final FormulaEncodingWithPointerAliasingOptions options;

  private final FormulaManagerView fmgr;
  private final BooleanFormulaManagerView bfmgr;
  private final ArrayFormulaManagerView afmgr;

  private static int freshIdx = 0;


  private static final List<String> auxStarts =
      Arrays.asList(new String[]{"grd", "ass", "var", "mem", "tmp", "__CPAchecker_TMP", "result"});

  public SummaryHandler(
      final CToFormulaConverterWithPointerAliasing pConv,
      final CFAEdge pEdge,
      final String pFunction,
      final SSAMapBuilder pSsa,
      final PointerTargetSetBuilder pPts,
      final Constraints pConstraints,
      final ErrorConditions pErrorConditions,
      final MemoryRegionManager pRegionMgr) {
    conv = pConv;

    typeHandler = pConv.typeHandler;
    options = conv.options;
    fmgr = conv.fmgr;
    bfmgr = conv.bfmgr;
    afmgr = fmgr.getArrayFormulaManager();

    edge = pEdge;
    function = pFunction;
    ssa = pSsa;
    err = pErrorConditions;
    pts = pPts;
    constraints = pConstraints;
    regionMgr = pRegionMgr;
  }

  public static void startSummary(final SSAMapBuilder ssa) {
     ssa.clearCaches();
  }

  private Formula getTerm(final String name, final CType type) {
    Formula term = ssa.getTerm(name);
    if (term == null) {
      final BooleanFormula pred = ssa.getPredicate(name);
      final FormulaType<?> ftype = typeHandler.getFormulaTypeFromCType(type);
      term = conv.ifTrueThenOneElseZero(ftype, pred);
    }
    return term;
  }

  private BooleanFormula getPred(final String name) {
    BooleanFormula pred = ssa.getPredicate(name);
    if (pred == null) {
      final Formula term = ssa.getTerm(name);
      final FormulaType<?> ftype = fmgr.getFormulaType(term);
      pred = bfmgr.not(fmgr.makeEqual(term, fmgr.makeNumber(ftype, BigInteger.ZERO)));
    }
    return pred;
  }

  private static class ConstFormulaVisitor extends DefaultFormulaVisitor<Boolean> {
    private long c;

    ConstFormulaVisitor(final long c) {
      this.c = c;
    }

    @Override
    public Boolean visitDefault(final Formula f) {
      return false;
    }

    @Override
    public Boolean visitConstant(final Formula f, final Object v) {
      if (v instanceof Number) {
        final Number n = (Number) v;
        return n.longValue() == c;
      }
      return false;
    }
  }

  private boolean isConst(final Formula f, final long c) {
    return fmgr.visit(f, new ConstFormulaVisitor(c));
  }

  private void putTerm(final String name, final Formula term) {
    ssa.setTerm(name, term);
    fmgr.splitIfThenElse(term).ifPresent( ite -> {
      if (isConst(ite.getSecond(), 1) && isConst(ite.getThird(), 0)) {
        ssa.setPredicate(name, ite.getFirst());
      }
    });
  }

  private void putPred(final String name, final BooleanFormula pred) {
    ssa.setPredicate(name, pred);
  }

  private static boolean isAux(final String name) {
    return auxStarts.stream().anyMatch(name::startsWith);
  }

  private class CSummaryExpressionVisitor extends ExpressionToFormulaVisitor {
    CSummaryExpressionVisitor(final CToFormulaConverterWithPointerAliasing cToFormulaConverter,
                                final FormulaManagerView fmgr,
                                final CFAEdge cfaEdge,
                                final String function,
                                final SSAMapBuilder ssa,
                                final Constraints constraints) {
      super(cToFormulaConverter, fmgr, cfaEdge, function, ssa, constraints);
    }

    @Override
    public Formula visit(final CIdExpression e) throws UnrecognizedCCodeException {
      final String name = e.getName();
      if (isAux(name)) {
        return getTerm(name, e.getExpressionType());
      } else {
        if (!(conv instanceof CToFormulaConverterWithPointerAliasing)) {
          throw new UnrecognizedCCodeException("Need pointer aliasing converter", edge);
        }
        final CToFormulaConverterWithPointerAliasing convtr = (CToFormulaConverterWithPointerAliasing) conv;
        CType type = e.getExpressionType();
        if (type instanceof CArrayType) {
          type = new CPointerType(false, false, ((CArrayType) type).getType());
        }
        return convtr.makeVariable(
                        e.getDeclaration().getQualifiedName(),
                        type,
                        ssa);
      }
    }
  }

  private Formula evaluate(final CExpression e) throws UnrecognizedCCodeException {
    final ExpressionToFormulaVisitor vis = new CSummaryExpressionVisitor(conv,
                                                                         fmgr,
                                                                         edge,
                                                                         function,
                                                                         ssa,
                                                                         constraints);
    return e.accept(vis);
  }

  private BooleanFormula evaluate(final String name, final CExpression e) throws UnrecognizedCCodeException {
    if (e instanceof CCastExpression &&
        ((CCastExpression)e).getOperand() instanceof CIdExpression) {
      final String rname = ((CIdExpression)((CCastExpression)e).getOperand()).getName();
      final Formula result = ssa.getTerm(rname);
      if (result != null && result instanceof ArrayFormula) {
        putTerm(name, result);
        return bfmgr.makeTrue();
      }
      if (result != null &&
          fmgr.getFormulaType(result).equals(
              typeHandler.getFormulaTypeFromCType(((CCastExpression)e).getCastType()))) {
        putTerm(name, result);
        return bfmgr.makeTrue();
      }
    }

    putTerm(name, evaluate(e));
    return bfmgr.makeTrue();
  }

  private BooleanFormula choose(final String name, final List<CExpression> args) {
    final CType returnType = args.get(1).getExpressionType();
    final Formula fresh = fmgr.makeVariable(typeHandler.getFormulaTypeFromCType(returnType), "fresh", freshIdx++);
    if (conv.options.addRangeConstraintsForNondet()) {
      conv.addRangeConstraint(fresh, returnType, constraints);
    }
    ssa.setTerm(name, fresh);
    return bfmgr.makeTrue();
  }

  private BooleanFormula alloc(final String name, final String fname, final CFunctionCallExpression call)
      throws UnrecognizedCCodeException, InterruptedException {
    final DynamicMemoryHandler memoryHandler =
        new DynamicMemoryHandler(conv, edge, ssa, pts, constraints, err, regionMgr);
    putTerm(name, memoryHandler.handleMemoryAllocation(call, fname));
    return bfmgr.makeTrue();
  }

  private BooleanFormula ite(final String name, final List<CExpression> args)
      throws UnrecognizedCCodeException {
    final BooleanFormula c = getPred(((CIdExpression)args.get(0)).getName());
    final Formula t = evaluate(args.get(1));
    final Formula e = evaluate(args.get(2));
    final Formula result = bfmgr.ifThenElse(c, t, e);
    ssa.setTerm(name, result);
    return bfmgr.makeTrue();
  }

  private BooleanFormula not(final String name, final List<CExpression> args) {
    final BooleanFormula a = getPred(((CIdExpression)args.get(0)).getName());
    putPred(name, bfmgr.not(a));
    return bfmgr.makeTrue();
  }

  private BooleanFormula and(final String name, final List<CExpression> args) {
    final BooleanFormula a = getPred(((CIdExpression)args.get(0)).getName());
    final BooleanFormula b = getPred(((CIdExpression)args.get(1)).getName());
    putPred(name, bfmgr.and(a, b));
    return bfmgr.makeTrue();
  }

  private BooleanFormula or(final String name, final List<CExpression> args) {
    final BooleanFormula a = getPred(((CIdExpression)args.get(0)).getName());
    final BooleanFormula b = getPred(((CIdExpression)args.get(1)).getName());
    putPred(name, bfmgr.or(a, b));
    return bfmgr.makeTrue();
  }

  private BooleanFormula assignVar(final CIdExpression left, final CIdExpression right) {
    final String rname = right.getName();
    return fmgr.makeEqual(conv.makeVariable(left.getDeclaration().getQualifiedName(), left.getExpressionType(), ssa),
                          getTerm(rname, right.getExpressionType()));
  }

  private ArrayFormula<?, ?> array(final String name, final CType elementCType) {
    final int index = conv.getIndex(name, typeHandler.simplifyType(elementCType), ssa);
    final FormulaType<?> elementType = typeHandler.getFormulaTypeFromCType(elementCType);
    return afmgr.makeArray(name, index, typeHandler.getPointerType(), elementType);
  }

  private MemoryRegion region(final CExpression loc) throws UnrecognizedCCodeException {
    final CExpressionVisitorWithPointerAliasing vis =
        new CExpressionVisitorWithPointerAliasing(conv, edge, function, ssa, constraints, err, pts, regionMgr);
    final Expression regionLoc = loc.accept(vis);
    if (!(regionLoc instanceof AliasedLocation)) {
      throw new UnrecognizedCCodeException("Argument should be a heap location", edge);
    }
    MemoryRegion region = ((AliasedLocation) regionLoc).getMemoryRegion();
    if (region == null) {
      CType type = typeHandler.simplifyType(loc.getExpressionType());
      region = regionMgr.makeMemoryRegion(type);
    }
    return region;
  }

  private ArrayFormula<?, ?> array(final CExpression loc) throws UnrecognizedCCodeException {
    final MemoryRegion region = region(loc);
    return array(regionMgr.getPointerAccessName(region), region.getType());
  }

  private BooleanFormula memory(final String name, final List<CExpression> args)
      throws UnrecognizedCCodeException {
    final Formula result = array(args.get(0));
    ssa.setTerm(name, result);
    return bfmgr.makeTrue();
  }

  private BooleanFormula select(final String name, final List<CExpression> args) {
    final Formula arr = ssa.getTerm(((CIdExpression) args.get(0)).getName());
    final Formula idx = getTerm(((CIdExpression) args.get(1)).getName(), args.get(1).getExpressionType());
    @SuppressWarnings("unchecked")
    final Formula result = afmgr.select((ArrayFormula<Formula,?>)arr, idx);
    ssa.setTerm(name, result);
    return bfmgr.makeTrue();
  }

  private BooleanFormula update(final String name, final List<CExpression> args) {
    final Formula arr = ssa.getTerm(((CIdExpression) args.get(0)).getName());
    final Formula idx = getTerm(((CIdExpression) args.get(1)).getName(), args.get(1).getExpressionType());
    final Formula val = getTerm(((CIdExpression) args.get(2)).getName(), args.get(2).getExpressionType());
    @SuppressWarnings("unchecked")
    final Formula result = afmgr.store((ArrayFormula<Formula, Formula>)arr, idx, val);
    ssa.setTerm(name, result);
    return bfmgr.makeTrue();
  }

  private BooleanFormula nondetMem(final String name, final List<CExpression> args)
         throws UnrecognizedCCodeException {
    final CExpression arg0 = args.get(0);
    if (!(arg0 instanceof CIdExpression)) {
      throw new UnrecognizedCCodeException("Need variable witness to create memory", edge);
    }
    final CIdExpression arg = (CIdExpression)arg0;
    if (!(arg.getExpressionType() instanceof CPointerType)) {
      throw new UnrecognizedCCodeException("Need pointer type to create memory", edge);
    }
    final FormulaType<?> elementType =
        typeHandler.getFormulaTypeFromCType(((CPointerType)arg.getExpressionType()).getType());
    final Formula result = afmgr.makeArray("fresh", freshIdx++, typeHandler.getPointerType(), elementType);
    ssa.setTerm(name, result);
    return bfmgr.makeTrue();
  }

  private BooleanFormula makeAssignment(final CLeftHandSide left, final CRightHandSide right)
          throws UnrecognizedCCodeException, InterruptedException {
    if (left instanceof CIdExpression) {
      final CIdExpression id = (CIdExpression) left;
      final String name = id.getName();
      if (isAux(name)) {
        if (right instanceof CExpression) {
          return evaluate(name, (CExpression) right);
        } else {
          if (!(right instanceof CFunctionCallExpression)) {
            throw new UnrecognizedCCodeException("Unknown RHS", edge);
          }
          final CFunctionCallExpression call = (CFunctionCallExpression) right;
          final CExpression fexp = call.getFunctionNameExpression();
          if (!(fexp instanceof CIdExpression)) {
            throw new UnrecognizedCCodeException("Only special notational functions are supported", edge);
          }
          final String fname = ((CIdExpression) fexp).getName();
          final List<CExpression> args = call.getParameterExpressions();
          if (conv.options.isChooseFunctionName(fname)) {
            return choose(name, args);
          } else if (options.isAllocFunctionName(fname)) {
            return alloc(name, fname, call);
          } if (conv.options.isIteFunctionName(fname)) {
            return ite(name, args);
          } else if (conv.options.isIteMemFunctionName(fname)) {
            return ite(name, args);
          } else if (conv.options.isNotFunctionName(fname)) {
            return not(name, args);
          } else if (conv.options.isAndFunctionName(fname)) {
            return and(name, args);
          } else if (conv.options.isOrFunctionName(fname)) {
            return or(name, args);
          } else if (conv.options.isMemoryFunctionName(fname)) {
            return memory(name, args);
          } else if (conv.options.isSelectFunctionName(fname)) {
            return select(name, args);
          } else if (conv.options.isUpdateFunctionName(fname)) {
            return update(name, args);
          } else if (conv.options.isConstFunctionName(fname)) {
            return nondetMem(name, args);
          } else if (conv.options.isNondetMemFunctionName(fname)) {
            return nondetMem(name, args);
          } else {
            throw new UnrecognizedCCodeException("Only special notational functions are supported", edge);
          }
        }
      } else {
        if (!(right instanceof CIdExpression)) {
          throw new UnrecognizedCCodeException("Uncached resulting value", edge);
        }
        return assignVar(id, (CIdExpression) right);
      }
    } else {
      throw new UnrecognizedCCodeException("Only variable assignments are allowed in summaries", edge);
    }
  }

  private BooleanFormula havoc(final CFunctionCallExpression call) throws UnrecognizedCCodeException {
    final MemoryRegion region = region(call.getParameterExpressions().get(0));
    conv.makeFreshIndex(regionMgr.getPointerAccessName(region), region.getType(), ssa);
     if (conv.options.addRangeConstraintsForNondet()) {
       conv.addHavocRegionRangeConstraints(region, ssa, constraints, pts);
     }
     return bfmgr.makeTrue();
  }

  private BooleanFormula makeCall(final CFunctionCallExpression call)
      throws UnrecognizedCCodeException {
    final CExpression fexp = call.getFunctionNameExpression();
    if (!(fexp instanceof CIdExpression)) {
      throw new UnrecognizedCCodeException("Only special notational functions are supported", edge);
    }
    final String fname = ((CIdExpression) fexp).getName();
    if (conv.options.isHavocFunctionName(fname)) {
      return havoc(call);
    } else if (conv.options.isAssignFunctionName(fname)) {
      return havoc(call);
    } else if (CtoFormulaConverter.PURE_EXTERNAL_FUNCTIONS.contains(fname)) {
      return bfmgr.makeTrue();
    } else {
      throw new UnrecognizedCCodeException("Only special notational functions are supported", edge);
    }
  }

  private BooleanFormula assume(final CExpression e, boolean isTrue) {
    final CIdExpression cond;
    if (e instanceof CIdExpression) {
      cond = (CIdExpression) e;
    } else {
      final CBinaryExpression bin = (CBinaryExpression) e;
      cond = (CIdExpression) bin.getOperand1();
      final CExpression z = bin.getOperand2();
      assert
        z instanceof CIntegerLiteralExpression &&
        ((CIntegerLiteralExpression) z).getValue().equals(BigInteger.ZERO) :
          "Unrecognized assumption";
      if (bin.getOperator() == BinaryOperator.EQUALS) {
        isTrue = !isTrue;
      }
    }
    BooleanFormula result = getPred(cond.getName());
    if (!isTrue) {
      result = bfmgr.not(result);
    }
    return result;
  }

  private BooleanFormula makeStatement(final CStatementEdge statement)
          throws UnrecognizedCCodeException, InterruptedException {

    CStatement stmt = statement.getStatement();
    if (stmt instanceof CAssignment) {
      final CAssignment assignment = (CAssignment) stmt;
      return makeAssignment(assignment.getLeftHandSide(), assignment.getRightHandSide());
    } else {
      if (stmt instanceof CFunctionCallStatement) {
        final CFunctionCallStatement call = (CFunctionCallStatement) stmt;
         return makeCall(call.getFunctionCallExpression());
      } else if (!(stmt instanceof CExpressionStatement)) {
        throw new UnrecognizedCCodeException("Unknown statement", statement, stmt);
      }
      return bfmgr.makeTrue();
    }
  }

  public BooleanFormula getFormula()
          throws UnrecognizedCCodeException, UnrecognizedCFAEdgeException, InterruptedException {
    switch (edge.getEdgeType()) {
    case StatementEdge: {
      return makeStatement((CStatementEdge) edge);
    }

    case ReturnStatementEdge: {
      CReturnStatementEdge returnEdge = (CReturnStatementEdge)edge;
      final com.google.common.base.Optional<CAssignment> ass = returnEdge.asAssignment();
      if (ass.isPresent()) {
      return makeAssignment(ass.get().getLeftHandSide(), ass.get().getRightHandSide());
      } else {
        return bfmgr.makeTrue();
      }
    }

    case DeclarationEdge: {
      return bfmgr.makeTrue();
    }

    case AssumeEdge: {
      CAssumeEdge assumeEdge = (CAssumeEdge)edge;
      return assume(assumeEdge.getExpression(), assumeEdge.getTruthAssumption());
    }

    case BlankEdge: {
      return bfmgr.makeTrue();
    }

    case FunctionCallEdge: {
      throw new UnrecognizedCFAEdgeException(edge);
    }

    case FunctionReturnEdge: {
      throw new UnrecognizedCFAEdgeException(edge);
    }

    default:
      throw new UnrecognizedCFAEdgeException(edge);
    }
  }
}
