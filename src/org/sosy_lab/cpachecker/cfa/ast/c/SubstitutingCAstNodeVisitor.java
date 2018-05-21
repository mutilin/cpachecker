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
package org.sosy_lab.cpachecker.cfa.ast.c;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import org.sosy_lab.cpachecker.cfa.types.c.CEnumType;

public class SubstitutingCAstNodeVisitor implements CAstNodeVisitor<CAstNode, RuntimeException> {
  private SubstituteProvider sp;

  public SubstitutingCAstNodeVisitor(SubstituteProvider pSubstituteProvider) {
    this.sp = (Preconditions.checkNotNull(pSubstituteProvider));
  }

  @Nullable
  private CAstNode findSubstitute(CAstNode pNode) {
    return this.sp.findSubstitute(pNode);
  }

  private <T> T firstNotNull(T pExpr1, T pExpr2) {
    if (pExpr1 != null) {
      return pExpr1;
    }
    if (pExpr2 != null) {
      return pExpr2;
    }
    return null;
  }

  @Override
  public CAstNode visit(CArrayDesignator pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    CExpression oldSubEx = pNode.getSubscriptExpression();
    CExpression newSubEx = (CExpression) oldSubEx.accept(this);
    if (oldSubEx != newSubEx) {
      return new CArrayDesignator(oldSubEx.getFileLocation(), newSubEx);
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CArrayRangeDesignator pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    CExpression oldCeil = pNode.getCeilExpression();
    CExpression newCeil = (CExpression) pNode.getCeilExpression().accept(this);

    CExpression oldFloor = pNode.getFloorExpression();
    CExpression newFloor = (CExpression) pNode.getFloorExpression().accept(this);
    if ((oldCeil != newCeil) || (newFloor != oldFloor)) {
      return new CArrayRangeDesignator(
          pNode.getFileLocation(),
          firstNotNull(newFloor, oldFloor),
          firstNotNull(newCeil, oldCeil));
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CFieldDesignator pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CInitializerList pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    boolean initChanged = false;
    List<CInitializer> newInitializerList =
        Lists.newArrayListWithExpectedSize(pNode.getInitializers().size());
    for (CInitializer oldInit : pNode.getInitializers()) {
      CInitializer newInit = (CInitializer) oldInit.accept(this);
      if (newInit != oldInit) {
        initChanged = true;
      }
      newInitializerList.add(newInit);
    }
    if (initChanged) {
      return new CInitializerList(pNode.getFileLocation(), newInitializerList);
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CReturnStatement pNode) throws RuntimeException {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CAstNode visit(CDesignatedInitializer pNode) throws RuntimeException {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CAstNode visit(CInitializerExpression pNode) throws RuntimeException {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CAstNode visit(CFunctionCallExpression pNode) throws RuntimeException {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CAstNode visit(CBinaryExpression pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    CExpression oldOp1 = pNode.getOperand1();
    CExpression newOp1 = (CExpression) pNode.getOperand1().accept(this);

    CExpression oldOp2 = pNode.getOperand2();
    CExpression newOp2 = (CExpression) pNode.getOperand2().accept(this);
    if ((oldOp1 != newOp1) || (oldOp2 != newOp2)) {
      return new CBinaryExpression(
          pNode.getFileLocation(),
          pNode.getExpressionType(),
          pNode.getCalculationType(),
          firstNotNull(newOp1, oldOp1),
          firstNotNull(newOp2, oldOp2),
          pNode.getOperator());
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CCastExpression pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    CExpression oldOp1 = pNode.getOperand();
    CExpression newOp1 = (CExpression) pNode.getOperand().accept(this);
    if (oldOp1 != newOp1) {
      return new CCastExpression(pNode.getFileLocation(), pNode.getExpressionType(), newOp1);
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CTypeIdExpression pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CUnaryExpression pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    CExpression oldOp1 = pNode.getOperand();
    CExpression newOp1 = (CExpression) pNode.getOperand().accept(this);
    if (oldOp1 != newOp1) {
      return new CUnaryExpression(
          pNode.getFileLocation(),
          pNode.getExpressionType(),
          firstNotNull(newOp1, oldOp1),
          pNode.getOperator());
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CArraySubscriptExpression pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    CExpression oldOp1 = pNode.getArrayExpression();
    CExpression newOp1 = (CExpression) pNode.getArrayExpression().accept(this);

    CExpression oldOp2 = pNode.getSubscriptExpression();
    CExpression newOp2 = (CExpression) pNode.getSubscriptExpression().accept(this);
    if (oldOp1 != newOp1) {
      return new CArraySubscriptExpression(
          pNode.getFileLocation(),
          pNode.getExpressionType(),
          firstNotNull(newOp1, oldOp1),
          firstNotNull(newOp2, oldOp2));
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CComplexCastExpression pNode) throws RuntimeException {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CAstNode visit(CFieldReference pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CIdExpression pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CPointerExpression pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    CExpression oldOp1 = pNode.getOperand();
    CExpression newOp1 = (CExpression) pNode.getOperand().accept(this);
    if (oldOp1 != newOp1) {
      return new CPointerExpression(
          pNode.getFileLocation(), pNode.getExpressionType(), firstNotNull(newOp1, oldOp1));
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CCharLiteralExpression pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CFloatLiteralExpression pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CImaginaryLiteralExpression pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CIntegerLiteralExpression pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CStringLiteralExpression pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CAddressOfLabelExpression pNode) throws RuntimeException {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CAstNode visit(CParameterDeclaration pNode) throws RuntimeException {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CAstNode visit(CFunctionDeclaration pNode) throws RuntimeException {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CAstNode visit(CComplexTypeDeclaration pNode) throws RuntimeException {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CAstNode visit(CTypeDefDeclaration pNode) throws RuntimeException {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CAstNode visit(CVariableDeclaration pNode) throws RuntimeException {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CAstNode visit(CExpressionAssignmentStatement pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    CLeftHandSide oldLeft = pNode.getLeftHandSide();
    CLeftHandSide newLeft = (CLeftHandSide) pNode.getLeftHandSide().accept(this);

    CExpression oldRight = pNode.getRightHandSide();
    CExpression newRight = (CExpression) pNode.getRightHandSide().accept(this);
    if ((oldRight != newRight) || (oldLeft != newLeft)) {
      return new CExpressionAssignmentStatement(
          pNode.getFileLocation(),
          firstNotNull(newLeft, oldLeft),
          firstNotNull(newRight, oldRight));
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CExpressionStatement pNode) throws RuntimeException {
    CAstNode result = findSubstitute(pNode);
    if (result != null) {
      return result;
    }
    CExpression oldRight = pNode.getExpression();
    CExpression newRight = (CExpression) pNode.getExpression().accept(this);
    if (oldRight != newRight) {
      return new CExpressionStatement(pNode.getFileLocation(), newRight);
    }
    return pNode;
  }

  @Override
  public CAstNode visit(CFunctionCallAssignmentStatement pNode) throws RuntimeException {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CAstNode visit(CFunctionCallStatement pNode) throws RuntimeException {
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public CAstNode visit(CEnumType.CEnumerator pCEnumerator) {
    throw new RuntimeException("Not yet implemented");
  }

  public abstract static interface SubstituteProvider {
    @Nullable
    public abstract CAstNode findSubstitute(CAstNode paramCAstNode);
  }
}
