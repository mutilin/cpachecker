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
package org.sosy_lab.cpachecker.cpa.value;

import java.util.ArrayList;
import java.util.List;

import org.sosy_lab.common.log.LogManagerWithoutDuplicates;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.ast.java.JIdExpression;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CBasicType;
import org.sosy_lab.cpachecker.cfa.types.c.CComplexType.ComplexTypeKind;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType.CCompositeTypeMemberDeclaration;
import org.sosy_lab.cpachecker.cfa.types.c.CElaboratedType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.cpa.value.type.NumericValue;
import org.sosy_lab.cpachecker.cpa.value.type.Value;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.util.BuiltinFunctions;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;


/**
 * This Visitor returns the value from an expression.
 * The result may be null, i.e., the value is unknown.
 */
public class ExpressionValueVisitor extends AbstractExpressionValueVisitor {

  private boolean missingPointer = false;

  private final ValueAnalysisState state;

  /** This Visitor returns the numeral value for an expression.
   *
   * @param pState where to get the values for variables (identifiers)
   * @param pFunctionName current scope, used only for variable-names
   * @param pMachineModel where to get info about types, for casting and overflows
   * @param pLogger logging
   */
  public ExpressionValueVisitor(ValueAnalysisState pState, String pFunctionName,
      MachineModel pMachineModel, LogManagerWithoutDuplicates pLogger) {
    super(pFunctionName, pMachineModel, pLogger);
    state = pState;
  }

  /* additional methods */

  @Override
  public void reset() {
    super.reset();
    missingPointer = false;
  }

  public boolean hasMissingPointer() {
    return missingPointer;
  }

  @Override
  public Value visit(CFunctionCallExpression pIastFunctionCallExpression) throws UnrecognizedCCodeException {
    CExpression functionNameExp = pIastFunctionCallExpression.getFunctionNameExpression();

    // We only handle builtin functions
    if (functionNameExp instanceof CIdExpression) {
      String functionName = ((CIdExpression) functionNameExp).getName();

      if (BuiltinFunctions.isBuiltinFunction(functionName)) {
        CType functionReturnType = BuiltinFunctions.getFunctionType(functionName);

        if (isUnspecifiedType(functionReturnType)) {
          // unsupported formula
          return Value.UnknownValue.getInstance();
        }

        assert functionReturnType.equals(pIastFunctionCallExpression.getExpressionType())
            : "Builtin function's return type is false. " + functionName
            + " has return type " + pIastFunctionCallExpression.getExpressionType();
        List<CExpression> parameterExpressions = pIastFunctionCallExpression.getParameterExpressions();
        List<Value> parameterValues = new ArrayList<>(parameterExpressions.size());

        for (CExpression currParamExp : parameterExpressions) {
          Value newValue = currParamExp.accept(this);

          parameterValues.add(newValue);
        }

        if (BuiltinFunctions.isAbsolute(functionName)) {
          assert parameterValues.size() == 1;

          final CType parameterType = parameterExpressions.get(0).getExpressionType();
          final Value parameter = parameterValues.get(0);

          if (parameterType instanceof CSimpleType && !((CSimpleType) parameterType).isSigned()) {
            return parameter;

          } else if (parameter.isExplicitlyKnown()) {
            assert parameter.isNumericValue();
            final double absoluteValue = Math.abs(((NumericValue) parameter).doubleValue());

            // absolute value for INT_MIN is undefined behaviour, so we do not bother handling it
            // in any specific way
            return new NumericValue(absoluteValue);
          }

        } else if (BuiltinFunctions.isHugeVal(functionName)
            || BuiltinFunctions.isInfinity(functionName)) {

          assert parameterValues.isEmpty();
          if (BuiltinFunctions.isHugeValFloat(functionName)
              || BuiltinFunctions.isInfinityFloat(functionName)) {

            return new NumericValue(Float.POSITIVE_INFINITY);

          } else {
            assert BuiltinFunctions.isInfinityDouble(functionName)
                || BuiltinFunctions.isInfinityLongDouble(functionName)
                || BuiltinFunctions.isHugeValDouble(functionName)
                || BuiltinFunctions.isHugeValLongDouble(functionName)
                : " Unhandled builtin function for infinity: " + functionName;

            return new NumericValue(Double.POSITIVE_INFINITY);
          }

        } else if (BuiltinFunctions.isNaN(functionName)) {
          assert parameterValues.isEmpty();

          if (BuiltinFunctions.isNaNFloat(functionName)) {
            return new NumericValue(Float.NaN);
          } else {
            assert BuiltinFunctions.isNaNDouble(functionName)
                || BuiltinFunctions.isNaNLongDouble(functionName)
                : "Unhandled builtin function for NaN: " + functionName;

            return new NumericValue(Double.NaN);
          }
        }
      }
    }

    return Value.UnknownValue.getInstance();
  }

  private boolean isUnspecifiedType(CType pType) {
    return pType instanceof CSimpleType
        && ((CSimpleType) pType).getType() == CBasicType.UNSPECIFIED;
  }

  @Override
  protected Value evaluateCIdExpression(CIdExpression varName) {
    return evaluateAIdExpression(varName);
  }

  @Override
  protected Value evaluateJIdExpression(JIdExpression varName) {
    return evaluateAIdExpression(varName);
  }

  /** This method returns the value of a variable from the current state. */
  private Value evaluateAIdExpression(AIdExpression varName) {

    final MemoryLocation memLoc;

    if (varName.getDeclaration() != null) {
      memLoc = MemoryLocation.valueOf(varName.getDeclaration().getQualifiedName(), 0);
    } else if (!ForwardingTransferRelation.isGlobal(varName)) {
      memLoc = MemoryLocation.valueOf(getFunctionName(), varName.getName(), 0);
    } else {
      memLoc = MemoryLocation.valueOf(varName.getName(), 0);
    }

    if (state.contains(memLoc)) {
      return state.getValueFor(memLoc);
    } else {
      return Value.UnknownValue.getInstance();
    }
  }

  private Value evaluateLValue(CLeftHandSide pLValue) throws UnrecognizedCCodeException {

    MemoryLocation varLoc = evaluateMemoryLocation(pLValue);

    if (varLoc == null) {
      return Value.UnknownValue.getInstance();
    }

    if (getState().contains(varLoc)) {
      return state.getValueFor(varLoc);
    } else {
      return Value.UnknownValue.getInstance();
    }
  }

  @Override
  protected Value evaluateCFieldReference(CFieldReference pLValue) throws UnrecognizedCCodeException {
    return evaluateLValue(pLValue);
  }

  @Override
  protected Value evaluateCArraySubscriptExpression(CArraySubscriptExpression pLValue) throws UnrecognizedCCodeException {
    return evaluateLValue(pLValue);
  }

  @Override
  protected Value evaluateCPointerExpression(CPointerExpression pVarName) {
    missingPointer = true;
    return Value.UnknownValue.getInstance();
  }

  public boolean canBeEvaluated(CExpression lValue) throws UnrecognizedCCodeException {
    return evaluateMemoryLocation(lValue) != null;
  }

  public MemoryLocation evaluateMemoryLocation(CExpression lValue) throws UnrecognizedCCodeException {
    return lValue.accept(new MemoryLocationEvaluator(this));
  }

  /**
   * Returns the {@link MemoryLocation} of a struct member.
   * It is assumed that the struct of the given type begins at the given memory location.
   *
   * @param pStartLocation the start location of the struct
   * @param pMemberName the name of the member to return the memory location for
   * @param pStructType the type of the struct
   * @return the memory location of the struct member
   * @throws UnrecognizedCCodeException
   */
  public MemoryLocation evaluateRelativeMemLocForStructMember(MemoryLocation pStartLocation,
      String pMemberName, CCompositeType pStructType) throws UnrecognizedCCodeException {

    MemoryLocationEvaluator locationEvaluator = new MemoryLocationEvaluator(this);

    return locationEvaluator.getStructureFieldLocationFromRelativePoint(
        pStartLocation, pMemberName, pStructType);
  }

  private static class MemoryLocationEvaluator extends DefaultCExpressionVisitor<MemoryLocation, UnrecognizedCCodeException> {

    private final ExpressionValueVisitor evv;

    public MemoryLocationEvaluator(ExpressionValueVisitor pEvv) {
      evv = pEvv;
    }

    @Override
    protected MemoryLocation visitDefault(CExpression pExp) throws UnrecognizedCCodeException {
      return null;
    }

    @Override
    public MemoryLocation visit(CArraySubscriptExpression pIastArraySubscriptExpression)
        throws UnrecognizedCCodeException {

      CExpression arrayExpression = pIastArraySubscriptExpression.getArrayExpression();

      CType arrayExpressionType = arrayExpression.getExpressionType().getCanonicalType();

      /* A subscript Expression can also include an Array Expression.
      In that case, it is a dereference*/
      if (arrayExpressionType instanceof CPointerType) {
        evv.missingPointer = true;
        return null;
      }

      CExpression subscript = pIastArraySubscriptExpression.getSubscriptExpression();

      CType elementType = pIastArraySubscriptExpression.getExpressionType();

      MemoryLocation arrayLoc = arrayExpression.accept(this);

      if (arrayLoc == null) {
        return null;
      }

      Value subscriptValue = subscript.accept(evv);

      if (!subscriptValue.isExplicitlyKnown() || !subscriptValue.isNumericValue()) {
        return null;
      }

      long typeSize = evv.getSizeof(elementType);

      long subscriptOffset = subscriptValue.asNumericValue().longValue() * typeSize;

      if (arrayLoc.isOnFunctionStack()) {

        return MemoryLocation.valueOf(arrayLoc.getFunctionName(),
            arrayLoc.getIdentifier(),
            subscriptOffset);
      } else {

        return MemoryLocation.valueOf(arrayLoc.getIdentifier(),
            subscriptOffset);
      }
    }

    @Override
    public MemoryLocation visit(CFieldReference pIastFieldReference) throws UnrecognizedCCodeException {

      if (pIastFieldReference.isPointerDereference()) {
        evv.missingPointer = true;
        return null;
      }

      CLeftHandSide fieldOwner = (CLeftHandSide) pIastFieldReference.getFieldOwner();

      MemoryLocation memLocOfFieldOwner = fieldOwner.accept(this);

      if (memLocOfFieldOwner == null) {
        return null;
      }

      return getStructureFieldLocationFromRelativePoint(memLocOfFieldOwner, pIastFieldReference.getFieldName(),
          fieldOwner.getExpressionType());
    }

    protected MemoryLocation getStructureFieldLocationFromRelativePoint(MemoryLocation pStartLocation,
        String pFieldName, CType pOwnerType) throws UnrecognizedCCodeException {

      CType canonicalOwnerType = pOwnerType.getCanonicalType();

      Integer offset = getFieldOffset(canonicalOwnerType, pFieldName);

      if (offset == null) {
        return null;
      }

      if (pStartLocation.isOnFunctionStack()) {

        return MemoryLocation.valueOf(pStartLocation.getFunctionName(),
            pStartLocation.getIdentifier(),
            pStartLocation.getOffset() + offset);
      } else {

        return MemoryLocation.valueOf(pStartLocation.getIdentifier(),
            offset + pStartLocation.getOffset());
      }
    }

    private Integer getFieldOffset(CType ownerType, String fieldName) throws UnrecognizedCCodeException {

      if (ownerType instanceof CElaboratedType) {
        return getFieldOffset(((CElaboratedType) ownerType).getRealType(), fieldName);
      } else if (ownerType instanceof CCompositeType) {
        return getFieldOffset((CCompositeType) ownerType, fieldName);
      } else if (ownerType instanceof CPointerType) {
        evv.missingPointer = true;
        return null;
      }

      throw new AssertionError();
    }

    private Integer getFieldOffset(CCompositeType ownerType, String fieldName) {

      List<CCompositeTypeMemberDeclaration> membersOfType = ownerType.getMembers();

      int offset = 0;

      for (CCompositeTypeMemberDeclaration typeMember : membersOfType) {
        String memberName = typeMember.getName();

        if (memberName.equals(fieldName)) {
          return offset;
        }

        if (!(ownerType.getKind() == ComplexTypeKind.UNION)) {

          CType fieldType = typeMember.getType().getCanonicalType();

          offset = (int) (offset + evv.getSizeof(fieldType));
        }
      }

      return null;
    }

    @Override
    public MemoryLocation visit(CIdExpression idExp) throws UnrecognizedCCodeException {

      if (idExp.getDeclaration() != null) {
        return MemoryLocation.valueOf(idExp.getDeclaration().getQualifiedName(), 0);
      }

      boolean isGlobal = ForwardingTransferRelation.isGlobal(idExp);

      if (isGlobal) {
        return MemoryLocation.valueOf(idExp.getName(), 0);
      } else {
        return MemoryLocation.valueOf(evv.getFunctionName(), idExp.getName(), 0);
      }
    }

    @Override
    public MemoryLocation visit(CPointerExpression pPointerExpression) throws UnrecognizedCCodeException {
      evv.missingPointer = true;
      return null;
    }

    @Override
    public MemoryLocation visit(CCastExpression pE) throws UnrecognizedCCodeException {
      // TODO reinterpretations for ValueAnalysis
      return pE.getOperand().accept(this);
    }
  }

  public ValueAnalysisState getState() {
    return state;
  }
}
