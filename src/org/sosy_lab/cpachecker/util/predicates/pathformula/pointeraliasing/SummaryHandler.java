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
import java.util.List;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ErrorConditions;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap.SSAMapBuilder;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.Constraints;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.Expression.Location.AliasedLocation;
import org.sosy_lab.cpachecker.util.predicates.smt.ArrayFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.BooleanFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.java_smt.api.ArrayFormula;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaType;

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

  private ArrayFormula<?, ?> getMem(final int idx, final CType elemType) {
    ArrayFormula<?, ?> result = ssa.getMem(idx);
    if (result == null) {
      final FormulaType<?> elementType = typeHandler.getFormulaTypeFromCType(elemType);
      result = afmgr.makeArray("fresh", freshIdx++, typeHandler.getPointerType(), elementType);
    }
    return result;
  }

  private void putVal(final int idx, final Formula term) {
    ssa.setVal(idx, term);
  }

  private void putMem(final int idx, final ArrayFormula<?, ?> mem) {
    ssa.setMem(idx, mem);
  }

  private BooleanFormula choose(final List<CExpression> args) {
    final CType type = CNumericTypes.UNSIGNED_LONG_LONG_INT;
    final FormulaType<?> formulaType = typeHandler.getFormulaTypeFromCType(type);
    final Formula fresh = fmgr.makeVariable(formulaType, "fresh", freshIdx++);
    if (options.addRangeConstraintsForNondet()) {
      conv.addRangeConstraint(fresh, type, constraints);
    }
    final int idx = (int) ((CIntegerLiteralExpression)args.get(0)).asLong();
    if (((CIntegerLiteralExpression)args.get(1)).asLong() != 0L) {
      constraints.addConstraint(fmgr.makeNot(fmgr.makeEqual(fresh, fmgr.makeNumber(formulaType, BigInteger.ZERO))));
    }
    putVal(idx, fresh);
    return bfmgr.makeTrue();
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

  private BooleanFormula memory(final List<CExpression> args)
      throws UnrecognizedCCodeException {
    final int idx = (int) ((CIntegerLiteralExpression)args.get(0)).asLong();
    final ArrayFormula<?, ?> result = array(args.get(1));
    putMem(idx, result);
    return bfmgr.makeTrue();
  }

  private BooleanFormula select(final List<CExpression> args) throws UnrecognizedCCodeException {
    final int idx = (int) ((CIntegerLiteralExpression)args.get(0)).asLong();
    final int midx = (int) ((CIntegerLiteralExpression)args.get(1)).asLong();
    final CType ptrType = args.get(2).getExpressionType();
    CType elemType = ((CPointerType) ptrType).getType();
    if (elemType instanceof CArrayType) {
      elemType = ((CArrayType) elemType).getType();
    }
    final ArrayFormula<?, ?> arr = getMem(midx, elemType);
    final CExpressionVisitorWithPointerAliasing vis =
        new CExpressionVisitorWithPointerAliasing(conv, edge, function, ssa, constraints, err, pts, regionMgr);
    final Formula adr = vis.asValueFormula(args.get(2).accept(vis), ptrType);
    @SuppressWarnings("unchecked")
    final Formula result = afmgr.select((ArrayFormula<Formula,?>)arr, adr);
    putVal(idx, result);
    return bfmgr.makeTrue();
  }

  private BooleanFormula update(final List<CExpression> args) throws UnrecognizedCCodeException {
    final int idx = (int) ((CIntegerLiteralExpression)args.get(0)).asLong();
    final int midx = (int) ((CIntegerLiteralExpression)args.get(1)).asLong();
    final CType ptrType = args.get(2).getExpressionType();
    CType elemType = ((CPointerType) ptrType).getType();
    if (elemType instanceof CArrayType) {
      elemType = ((CArrayType) elemType).getType();
    }
    final ArrayFormula<?, ?> arr = getMem(midx, elemType);
    final CExpressionVisitorWithPointerAliasing vis =
        new CExpressionVisitorWithPointerAliasing(conv, edge, function, ssa, constraints, err, pts, regionMgr);
    final Formula adr = vis.asValueFormula(args.get(2).accept(vis), ptrType);
    final Formula val = vis.asValueFormula(args.get(3).accept(vis), elemType);
    @SuppressWarnings("unchecked")
    final ArrayFormula<?, ?> result = afmgr.store((ArrayFormula<Formula, Formula>)arr, adr, val);
    putMem(idx, result);
    return bfmgr.makeTrue();
  }

  private BooleanFormula nondetMem() {
    return bfmgr.makeTrue();
  }

  private BooleanFormula havoc(final CFunctionCallExpression call) throws UnrecognizedCCodeException {
    final MemoryRegion region = region(call.getParameterExpressions().get(0));
    conv.makeFreshIndex(regionMgr.getPointerAccessName(region), region.getType(), ssa);
     if (options.addRangeConstraintsForNondet()) {
       conv.addHavocRegionRangeConstraints(region, ssa, constraints, pts);
     }
     return bfmgr.makeTrue();
  }

  public BooleanFormula makeCall(final CFunctionCallExpression call)
      throws UnrecognizedCCodeException {
    final CExpression fexp = call.getFunctionNameExpression();
    if (!(fexp instanceof CIdExpression)) {
      throw new UnrecognizedCCodeException("Only special notational functions are supported", edge);
    }
    final String fname = ((CIdExpression) fexp).getName();
    final List<CExpression> args = call.getParameterExpressions();
    if (options.isHavocFunctionName(fname)) {
      return havoc(call);
    } else if (options.isAssignFunctionName(fname)) {
      return havoc(call);
    } else  if (options.isChooseFunctionName(fname)) {
      return choose(args);
    } else if (options.isMemoryFunctionName(fname)) {
      return memory(args);
    } else if (options.isSelectFunctionName(fname)) {
      return select(args);
    } else if (options.isUpdateFunctionName(fname)) {
      return update(args);
    } else if (options.isConstFunctionName(fname)) {
      return nondetMem();
    } else if (options.isNondetMemFunctionName(fname)) {
      return nondetMem();
    } else {
      throw new UnrecognizedCCodeException("Only special notational functions are supported", edge);
    }
  }

  public boolean isSpecial(final CStatementEdge statement) {
    CStatement stmt = statement.getStatement();
    if (stmt instanceof CFunctionCallStatement) {
      final CFunctionCallStatement call = (CFunctionCallStatement) stmt;
      final CFunctionCallExpression cexp = call.getFunctionCallExpression();
      final CExpression fexp = cexp.getFunctionNameExpression();
      if (fexp instanceof CIdExpression) {
        final String fname = ((CIdExpression) fexp).getName();
        return
            options.isHavocFunctionName(fname) ||
            options.isAssignFunctionName(fname) ||
            options.isMemoryFunctionName(fname) ||
            options.isChooseFunctionName(fname) ||
            options.isSelectFunctionName(fname) ||
            options.isUpdateFunctionName(fname) ||
            options.isConstFunctionName(fname) ||
            options.isNondetMemFunctionName(fname);
      }
    }
    return false;
  }
}
