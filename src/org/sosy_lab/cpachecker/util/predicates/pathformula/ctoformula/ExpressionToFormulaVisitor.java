/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula;

import static org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.types.CtoFormulaTypeUtils.*;

import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression.TypeIdOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CEnumType.CEnumerator;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BitvectorFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FunctionFormulaType;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap.SSAMapBuilder;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.types.CtoFormulaTypeUtils;

import com.google.common.collect.ImmutableList;

class ExpressionToFormulaVisitor extends DefaultCExpressionVisitor<Formula, UnrecognizedCCodeException> {

  protected final CtoFormulaConverter conv;
  protected final CFAEdge       edge;
  protected final String        function;
  protected final SSAMapBuilder ssa;
  protected final Constraints   constraints;

  public ExpressionToFormulaVisitor(CtoFormulaConverter pCtoFormulaConverter, CFAEdge pEdge, String pFunction, SSAMapBuilder pSsa, Constraints pCo) {
    conv = pCtoFormulaConverter;
    edge = pEdge;
    function = pFunction;
    ssa = pSsa;
    constraints = pCo;
  }

  @Override
  protected Formula visitDefault(CExpression exp)
      throws UnrecognizedCCodeException {
    return conv.makeVariableUnsafe(exp, function, ssa, false);
  }

  @Override
  public Formula visit(CBinaryExpression exp) throws UnrecognizedCCodeException {
    BinaryOperator op = exp.getOperator();
    CExpression e1 = exp.getOperand1();
    CExpression e2 = exp.getOperand2();

    // these operators expect numeric arguments
    CType t1 = e1.getExpressionType();
    CType t2 = e2.getExpressionType();
    CType returnType = exp.getExpressionType();
    FormulaType<?> returnFormulaType = conv.getFormulaTypeFromCType(returnType);

    Formula f1 = e1.accept(this);
    Formula f2 = e2.accept(this);
    CType promT1 = conv.getPromotedCType(t1);
    f1 = conv.makeCast(t1, promT1, f1);
    CType promT2 = conv.getPromotedCType(t2);
    f2 = conv.makeCast(t2, promT2, f2);

    CType implicitType;
    // FOR SHIFTS: The type of the result is that of the promoted left operand. (6.5.7 3)
    if (op == BinaryOperator.SHIFT_LEFT || op == BinaryOperator.SHIFT_RIGHT) {
      implicitType = promT1;

      // TODO: This is probably not correct as we only need the right formula-type but not a cast
      f2 = conv.makeCast(promT2, promT1, f2);

      // UNDEFINED: When the right side is negative the result is not defined
    } else {
      implicitType = conv.getImplicitCType(promT1, promT2);
      f1 = conv.makeCast(promT1, implicitType, f1);
      f2 = conv.makeCast(promT2, implicitType, f2);
    }

    boolean signed = CtoFormulaTypeUtils.isSignedType(implicitType);

    Formula ret;
    switch (op) {
    case PLUS:
      ret = conv.fmgr.makePlus(f1, f2);
      break;
    case MINUS:
      ret =  conv.fmgr.makeMinus(f1, f2);
      break;
    case MULTIPLY:
      ret =  conv.fmgr.makeMultiply(f1, f2);
      break;
    case DIVIDE:
      ret =  conv.fmgr.makeDivide(f1, f2, signed);
      break;
    case MODULO:
      ret =  conv.fmgr.makeModulo(f1, f2, signed);
      break;
    case BINARY_AND:
      ret =  conv.fmgr.makeAnd(f1, f2);
      break;
    case BINARY_OR:
      ret =  conv.fmgr.makeOr(f1, f2);
      break;
    case BINARY_XOR:
      ret =  conv.fmgr.makeXor(f1, f2);
      break;
    case SHIFT_LEFT:

      // NOTE: The type of the result is that of the promoted left operand. (6.5.7 3)
      ret =  conv.fmgr.makeShiftLeft(f1, f2);
      break;
    case SHIFT_RIGHT:
      // NOTE: The type of the result is that of the promoted left operand. (6.5.7 3)
      ret =  conv.fmgr.makeShiftRight(f1, f2, signed);
      break;

    case GREATER_THAN:
    case GREATER_EQUAL:
    case LESS_THAN:
    case LESS_EQUAL:
    case EQUALS:
    case NOT_EQUALS: {
      BooleanFormula result;
      switch (op) {
        case GREATER_THAN:
          result= conv.fmgr.makeGreaterThan(f1, f2, signed);
          break;
        case GREATER_EQUAL:
          result= conv.fmgr.makeGreaterOrEqual(f1, f2, signed);
          break;
        case LESS_THAN:
          result= conv.fmgr.makeLessThan(f1, f2, signed);
          break;
        case LESS_EQUAL:
          result= conv.fmgr.makeLessOrEqual(f1, f2, signed);
          break;
        case EQUALS:
          result= conv.fmgr.makeEqual(f1, f2);
          break;
        case NOT_EQUALS:
          result= conv.bfmgr.not(conv.fmgr.makeEqual(f1, f2));
          break;
        default:
          throw new AssertionError();
      }
      ret = conv.bfmgr.ifTrueThenOneElseZero(returnFormulaType, result);
      break;
    }
    default:
      throw new UnrecognizedCCodeException("Unknown binary operator", edge, exp);

    }

    if (returnFormulaType != conv.fmgr.getFormulaType(ret)) {
      // Could be because both types got promoted
      if (!areEqual(promT1, t1) && !areEqual(promT2, t2)) {
        // We have to cast back to the return type
        ret = conv.makeCast(implicitType, returnType, ret);
      }
    }

    assert returnFormulaType == conv.fmgr.getFormulaType(ret)
         : "Returntype and Formulatype do not match in visit(CBinaryExpression)";
    return ret;
  }




  @Override
  public Formula visit(CCastExpression cexp) throws UnrecognizedCCodeException {
    Formula operand = cexp.getOperand().accept(this);
    return conv.makeCast(cexp, operand);
  }

  @Override
  public Formula visit(CIdExpression idExp) {

    if (idExp.getDeclaration() instanceof CEnumerator) {
      CEnumerator enumerator = (CEnumerator)idExp.getDeclaration();
      CType t = idExp.getExpressionType();
      if (enumerator.hasValue()) {
        return conv.fmgr.makeNumber(conv.getFormulaTypeFromCType(t), enumerator.getValue());
      } else {
        // We don't know the value here, but we know it is constant.
        return conv.makeConstant(enumerator.getName(), t, ssa);
      }
    }

    return conv.makeVariable(conv.scopedIfNecessary(idExp, ssa, function), ssa);
  }

  @Override
  public Formula visit(CFieldReference fExp) throws UnrecognizedCCodeException {
    if (conv.handleFieldAccess) {
      CExpression fieldOwner = getRealFieldOwner(fExp);
      Formula f = fieldOwner.accept(this);
      return conv.accessField(fExp, f);
    }

    CExpression fieldRef = fExp.getFieldOwner();
    if (fieldRef instanceof CIdExpression) {
      CSimpleDeclaration decl = ((CIdExpression) fieldRef).getDeclaration();
      if (decl instanceof CDeclaration && ((CDeclaration)decl).isGlobal()) {
        // this is the reference to a global field variable

        // we can omit the warning (no pointers involved),
        // and we don't need to scope the variable reference
        return conv.makeVariable(CtoFormulaConverter.exprToVarName(fExp), fExp.getExpressionType(), ssa);
      }
    }

    return super.visit(fExp);
  }


  @Override
  public Formula visit(CCharLiteralExpression cExp) throws UnrecognizedCCodeException {
    // we just take the byte value
    FormulaType<?> t = conv.getFormulaTypeFromCType(cExp.getExpressionType());
    return conv.fmgr.makeNumber(t, cExp.getCharacter());
  }

  @Override
  public Formula visit(CIntegerLiteralExpression iExp) throws UnrecognizedCCodeException {
    FormulaType<?> t = conv.getFormulaTypeFromCType(iExp.getExpressionType());
    return conv.fmgr.makeNumber(t, iExp.getValue().longValue());
  }

  @Override
  public Formula visit(CFloatLiteralExpression fExp) throws UnrecognizedCCodeException {
    FormulaType<?> t = conv.getFormulaTypeFromCType(fExp.getExpressionType());
    // TODO: Check if this is actually correct
    return conv.fmgr.makeNumber(t, fExp.getValue().longValue());
  }

  private FunctionFormulaType<BitvectorFormula> stringUfDecl;
  @Override
  public Formula visit(CStringLiteralExpression lexp) throws UnrecognizedCCodeException {
    // we create a string constant representing the given
    // string literal
    String literal = lexp.getValue();
    BitvectorFormula result = conv.stringLitToFormula.get(literal);

    if (result == null) {
      if (stringUfDecl == null) {
        FormulaType<BitvectorFormula> pointerType =
            conv.efmgr.getFormulaType(conv.machineModel.getSizeofPtr() * conv.machineModel.getSizeofCharInBits());
        stringUfDecl =
            conv.ffmgr.createFunction(
                "__string__", pointerType, FormulaType.RationalType);
      }

      // generate a new string literal. We generate a new UIf
      int n = conv.nextStringLitIndex++;
      result = conv.ffmgr.createUninterpretedFunctionCall(
          stringUfDecl, ImmutableList.of(conv.nfmgr.makeNumber(n)));
      conv.stringLitToFormula.put(literal, result);
    }

    return result;
  }

  @Override
  public Formula visit(CUnaryExpression exp) throws UnrecognizedCCodeException {
    CExpression operand = exp.getOperand();
    UnaryOperator op = exp.getOperator();
    switch (op) {
    case PLUS:
    case MINUS:
    case TILDE: {
      // Handle Integer Promotion
      CType t = operand.getExpressionType();
      CType promoted = conv.getPromotedCType(t);
      Formula operandFormula = operand.accept(this);
      operandFormula = conv.makeCast(t, promoted, operandFormula);
      Formula ret;
      if (op == UnaryOperator.PLUS) {
        ret = operandFormula;
      } else if (op == UnaryOperator.MINUS) {
        ret = conv.fmgr.makeNegate(operandFormula);
      } else {
        assert op == UnaryOperator.TILDE
              : "This case should be impossible because of switch";
        ret = conv.fmgr.makeNot(operandFormula);
      }

      CType returnType = exp.getExpressionType();
      FormulaType<?> returnFormulaType = conv.getFormulaTypeFromCType(returnType);
      assert returnFormulaType == conv.fmgr.getFormulaType(ret)
            : "Returntype and Formulatype do not match in visit(CUnaryExpression)";
      return ret;
    }

    case NOT: {
      Formula f = operand.accept(this);
      BooleanFormula term = conv.fmgr.toBooleanFormula(f);
      return conv.bfmgr.ifTrueThenOneElseZero(conv.getFormulaTypeFromCType(exp.getExpressionType()), conv.bfmgr.not(term));
    }

    case AMPER:
    case STAR:
      return visitDefault(exp);

    case SIZEOF:
      if (exp.getOperand() instanceof CIdExpression) {
        CType lCType =
            ((CIdExpression) exp.getOperand()).getExpressionType();
        return handleSizeof(exp, lCType);
      } else {
        return visitDefault(exp);
      }

    default:
      throw new UnrecognizedCCodeException("Unknown unary operator", edge, exp);
    }
  }

  @Override
  public Formula visit(CTypeIdExpression tIdExp)
      throws UnrecognizedCCodeException {

    if (tIdExp.getOperator() == TypeIdOperator.SIZEOF) {
      CType lCType = tIdExp.getType();
      return handleSizeof(tIdExp, lCType);
    } else {
      return visitDefault(tIdExp);
    }
  }

  private Formula handleSizeof(CExpression pExp, CType pCType)
      throws UnrecognizedCCodeException {
    return conv.fmgr.makeNumber(
        conv
          .getFormulaTypeFromCType(pExp.getExpressionType()),
        conv.getSizeof(pCType));
  }
}