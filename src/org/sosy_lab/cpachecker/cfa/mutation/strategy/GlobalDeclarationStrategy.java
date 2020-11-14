/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
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
 */
package org.sosy_lab.cpachecker.cfa.mutation.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexTypeDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CDesignatedInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSideVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclarationVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatementVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeDefDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CBitFieldType;
import org.sosy_lab.cpachecker.cfa.types.c.CComplexType;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType.CCompositeTypeMemberDeclaration;
import org.sosy_lab.cpachecker.cfa.types.c.CElaboratedType;
import org.sosy_lab.cpachecker.cfa.types.c.CEnumType;
import org.sosy_lab.cpachecker.cfa.types.c.CEnumType.CEnumerator;
import org.sosy_lab.cpachecker.cfa.types.c.CFunctionType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CProblemType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CTypeVisitor;
import org.sosy_lab.cpachecker.cfa.types.c.CTypedefType;
import org.sosy_lab.cpachecker.cfa.types.c.CVoidType;
import org.sosy_lab.cpachecker.exceptions.NoException;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFATraversal.TraversalProcess;
import org.sosy_lab.cpachecker.util.Pair;

public class GlobalDeclarationStrategy
    extends GenericCFAMutationStrategy<Pair<ADeclaration, String>, Pair<Integer, Pair<ADeclaration, String>>> {

  private final Collection<String> usedFunctions = new HashSet<>();
  // private final Collection<String> functionTypes = new HashSet<>();
  private final Collection<String> typedefs = new HashSet<>();
  private final Collection<String> compositeTypes = new HashSet<>();
  private final Collection<String> elaboratedTypes = new HashSet<>();
  private final Collection<String> enums = new HashSet<>();
  private final Collection<String> usedVariables = new HashSet<>();

  private final GlobalNamesCollector saver = new GlobalNamesCollector();

  class NameCollectingCFAVisitor extends CFATraversal.DefaultCFAVisitor {

    @Override
    public TraversalProcess visitEdge(CFAEdge edge) {
      switch (edge.getEdgeType()) {
        case BlankEdge:
          break;

        case AssumeEdge:
          ((CExpression) ((AssumeEdge) edge).getExpression()).accept(saver);
          break;

        case DeclarationEdge:
          final CVariableDeclaration decl =
              ((CVariableDeclaration) ((CDeclarationEdge) edge).getDeclaration());

          decl.getType().accept(saver);

          final CInitializer init = decl.getInitializer();
          if (init != null) {
            init.accept(saver);
          }

          break;

        case StatementEdge:
          ((CStatementEdge) edge).getStatement().accept(saver);
          break;

        case ReturnStatementEdge:
          CExpression retval = ((CReturnStatementEdge) edge).getExpression().orNull();
          if (retval != null) {
            retval.accept(saver);
          }
          break;

        case CallToReturnEdge:
        case FunctionCallEdge:
        case FunctionReturnEdge:
        default:
          assert false : "Got unexpected edge type " + edge.getEdgeType() + " (" + edge + ")";
      }

      return TraversalProcess.CONTINUE;
    }
  }

  // mostly copied from cfa.postprocessing.functions.CReferencedFunctions.CollectFunctionsVisitor
  class GlobalNamesCollector extends DefaultCExpressionVisitor<Void, NoException>
      implements CRightHandSideVisitor<Void, NoException>,
          CStatementVisitor<Void, NoException>,
          CInitializerVisitor<Void, NoException>,
          CTypeVisitor<Void, NoException> {

    @Override
    public Void visit(CIdExpression pE) {
      if (pE.getExpressionType() instanceof CFunctionType) {
        usedFunctions.add(pE.getName());

      } else if (pE.getDeclaration() == null) {
        logger.logf(Level.WARNING, "No declaration found for id expression %s", pE);

      } else if (pE.getDeclaration() instanceof CVariableDeclaration) {
        if (((CVariableDeclaration) pE.getDeclaration()).isGlobal()) {
          usedVariables.add(pE.getName());
        }

      } else {
        assert pE.getDeclaration() instanceof CParameterDeclaration
            : "decl: " + pE.getDeclaration() + ", instanceof " + pE.getDeclaration().getClass();
      }
      return null;
    }

    @Override
    public Void visit(CTypeIdExpression pE) {
      pE.getType().accept(this);
      return null;
    }

    @Override
    public Void visit(CArraySubscriptExpression pE) {
      pE.getArrayExpression().accept(this);
      pE.getSubscriptExpression().accept(this);
      return null;
    }

    @Override
    public Void visit(CBinaryExpression pE) {
      pE.getOperand1().accept(this);
      pE.getOperand2().accept(this);
      return null;
    }

    @Override
    public Void visit(CCastExpression pE) {
      pE.getOperand().accept(this);
      pE.getCastType().accept(this);
      return null;
    }

    @Override
    public Void visit(CComplexCastExpression pE) {
      pE.getOperand().accept(this);
      pE.getType().accept(this);
      return null;
    }

    @Override
    public Void visit(CFieldReference pE) {
      pE.getFieldOwner().accept(this);
      return null;
    }

    @Override
    public Void visit(CFunctionCallExpression pE) {
      if (pE.getDeclaration() == null) {
        pE.getFunctionNameExpression().accept(this);
      } else {
        usedFunctions.add(pE.getDeclaration().getName());
      }

      for (CExpression param : pE.getParameterExpressions()) {
        param.accept(this);
      }
      return null;
    }

    @Override
    public Void visit(CUnaryExpression pE) {
      pE.getOperand().accept(this);
      return null;
    }

    @Override
    public Void visit(CPointerExpression pE) {
      pE.getOperand().accept(this);
      return null;
    }

    @Override
    protected Void visitDefault(CExpression pExp) {
      return null;
    }

    @Override
    public Void visit(CInitializerExpression pInitializerExpression) {
      pInitializerExpression.getExpression().accept(this);
      return null;
    }

    @Override
    public Void visit(CInitializerList pInitializerList) {
      for (CInitializer init : pInitializerList.getInitializers()) {
        init.accept(this);
      }
      return null;
    }

    @Override
    public Void visit(CDesignatedInitializer pCStructInitializerPart) {
      pCStructInitializerPart.getRightHandSide().accept(this);
      return null;
    }

    @Override
    public Void visit(CExpressionStatement pIastExpressionStatement) {
      pIastExpressionStatement.getExpression().accept(this);
      return null;
    }

    @Override
    public Void visit(CExpressionAssignmentStatement pIastExpressionAssignmentStatement) {
      pIastExpressionAssignmentStatement.getLeftHandSide().accept(this);
      pIastExpressionAssignmentStatement.getRightHandSide().accept(this);
      return null;
    }

    @Override
    public Void visit(CFunctionCallAssignmentStatement pIastFunctionCallAssignmentStatement) {
      pIastFunctionCallAssignmentStatement.getLeftHandSide().accept(this);
      pIastFunctionCallAssignmentStatement.getRightHandSide().accept(this);
      return null;
    }

    @Override
    public Void visit(CFunctionCallStatement pIastFunctionCallStatement) {
      pIastFunctionCallStatement.getFunctionCallExpression().accept(this);
      return null;
    }

    @Override
    public Void visit(CArrayType pArrayType) {
      pArrayType.getType().accept(this);
      return null;
    }

    @Override
    public Void visit(CCompositeType pCompositeType) {
      // logger.logf(Level.INFO, "name of composite type %s saved", pCompositeType.getName());
      if (compositeTypes.add(pCompositeType.getName())) {
        for (CCompositeTypeMemberDeclaration member : pCompositeType.getMembers()) {
          member.getType().accept(this);
        }
      }
      return null;
    }

    @Override
    public Void visit(CElaboratedType pElaboratedType) {
      // logger.logf(Level.INFO, "name of elaborated type %s saved", pElaboratedType.getName());
      if (elaboratedTypes.add(pElaboratedType.getName())) {
        @Nullable CComplexType type = pElaboratedType.getRealType();
        if (type != null) {
          type.accept(this);
        }
      }
      return null;
    }

    @Override
    public Void visit(CEnumType pEnumType) {
      enums.add(pEnumType.getName());
      return null;
    }

    @Override
    public Void visit(CFunctionType pFunctionType) {
      final String ftName = pFunctionType.getName();
      if (ftName != null && !ftName.isEmpty()) {
        // it's only(?) local variables of function type
        // "Got named function type " + pFunctionType + ", but ignored the name " + ftName;
      }
      pFunctionType.getReturnType().accept(this);
      pFunctionType.getParameters().forEach(p -> p.accept(this));
      return null;
    }

    @Override
    public Void visit(CPointerType pPointerType) {
      pPointerType.getType().accept(this);
      return null;
    }

    @Override
    public Void visit(CProblemType pProblemType) {
      logger.logf(Level.WARNING, "Ignoring CProblemType");
      return null;
    }

    @Override
    public Void visit(CSimpleType pSimpleType) {
      return null;
    }

    @Override
    public Void visit(CTypedefType pTypedefType) {
      if (typedefs.add(pTypedefType.getName())) {
        pTypedefType.getRealType().accept(this);
      }
      return null;
    }

    @Override
    public Void visit(CVoidType pVoidType) {
      return null;
    }

    @Override
    public Void visit(CBitFieldType pCBitFieldType) {
      pCBitFieldType.getType().accept(this);
      return null;
    }
  }

  class IsNameSaved
      implements CSimpleDeclarationVisitor<Boolean, NoException>,
          CTypeVisitor<Boolean, NoException> {

    @Override
    public Boolean visit(CFunctionDeclaration pDecl) {
      if (usedFunctions.contains(pDecl.getName())) {
        pDecl.getType().accept(saver);
        return true;
      }
      return false;
    }

    @Override
    public Boolean visit(CComplexTypeDeclaration pDecl) {
      // logger.logf(Level.INFO, "declaration: %s\nof complex type: %s", pDecl, pDecl.getType());
      return pDecl.getType().accept(this);
    }

    @Override
    public Boolean visit(CTypeDefDeclaration pDecl) {
      if (typedefs.contains(pDecl.getName())) {
        pDecl.getType().accept(saver);
        return true;
      }
      return false;
    }

    @Override
    public Boolean visit(CVariableDeclaration pDecl) {
      if (usedVariables.contains(pDecl.getName())) {
        pDecl.getType().accept(saver);
        CInitializer init = pDecl.getInitializer();
        if (init != null) {
          init.accept(saver);
        }
        return true;
      }
      return false;
    }

    @Override
    public Boolean visit(CCompositeType pCompositeType) {
      if (compositeTypes.contains(pCompositeType.getName())) {
        for (CCompositeTypeMemberDeclaration member : pCompositeType.getMembers()) {
          member.getType().accept(saver);
        }
        return true;
      }
      return false;
    }

    @Override
    public Boolean visit(CElaboratedType pElaboratedType) {
      @Nullable CComplexType type = pElaboratedType.getRealType();

      if (!elaboratedTypes.contains(pElaboratedType.getName())) {
        assert type == null || !type.accept(this);
        return false;
      }

      if (type != null) {
        assert type.accept(this);
      } else {
        logger.logf(Level.FINE, "elaborated type %s has no real type", pElaboratedType);
      }
      return true;
    }

    @Override
    public Boolean visit(CEnumType pEnumType) {
      return enums.contains(pEnumType.getName());
    }

    @Override
    public Boolean visit(CArrayType pArrayType) throws NoException {
      assert false : pArrayType;
      return null;
    }

    @Override
    public Boolean visit(CFunctionType pFunctionType) throws NoException {
      assert false : pFunctionType;
      return null;
    }

    @Override
    public Boolean visit(CPointerType pPointerType) throws NoException {
      assert false : pPointerType;
      return null;
    }

    @Override
    public Boolean visit(CProblemType pProblemType) throws NoException {
      assert false : pProblemType;
      return null;
    }

    @Override
    public Boolean visit(CSimpleType pSimpleType) throws NoException {
      assert false : pSimpleType;
      return null;
    }

    @Override
    public Boolean visit(CTypedefType pTypedefType) throws NoException {
      assert false : pTypedefType;
      return null;
    }

    @Override
    public Boolean visit(CVoidType pVoidType) throws NoException {
      assert false : pVoidType;
      return null;
    }

    @Override
    public Boolean visit(CBitFieldType pCBitFieldType) throws NoException {
      assert false : pCBitFieldType;
      return null;
    }

    @Override
    public Boolean visit(CParameterDeclaration pDecl) throws NoException {
      assert false : pDecl;
      return null;
    }

    @Override
    public Boolean visit(CEnumerator pDecl) throws NoException {
      assert false : pDecl;
      return null;
    }
  }

  public GlobalDeclarationStrategy(Configuration pConfig, LogManager pLogger)
      throws InvalidConfigurationException {
    super(pConfig, pLogger, "Global declarations");
  }

  @Override
  protected Collection<Pair<ADeclaration, String>> getAllObjects(ParseResult pParseResult) {
    usedFunctions.clear();
    // functionTypes.clear();
    typedefs.clear();
    compositeTypes.clear();
    elaboratedTypes.clear();
    enums.clear();
    usedVariables.clear();

    usedFunctions.addAll(pParseResult.getFunctions().keySet());
    final NameCollectingCFAVisitor vis = new NameCollectingCFAVisitor();
    for (FunctionEntryNode startingNode : pParseResult.getFunctions().values()) {
      CFATraversal.dfs().traverseOnce(startingNode, vis);
    }

    IsNameSaved isNameSaved = new IsNameSaved();
    List<Pair<ADeclaration, String>> answer = new ArrayList<>();

    ListIterator<Pair<ADeclaration, String>> globals =
        pParseResult
            .getGlobalDeclarations()
            .listIterator(pParseResult.getGlobalDeclarations().size());


    while (globals.hasPrevious()) {
      Pair<ADeclaration, String> pair = globals.previous();
      CDeclaration decl = (CDeclaration) pair.getFirst();
      boolean isNeeded = decl.accept(isNameSaved);
      if (!isNeeded) {
        answer.add(pair);
      }
      logger.logf(
          logDetails,
          "Global declaration %s:\n%s\nname: %s\ntype: %s",
          (isNeeded ? "remaines" : "added for removing"),
          decl,
          decl.getName(),
          decl.getType());
    }

    return answer;
  }

  @Override
  protected Pair<Integer, Pair<ADeclaration, String>> removeObject(
      ParseResult pParseResult, Pair<ADeclaration, String> pObject) {
    List<Pair<ADeclaration, String>> prgd = pParseResult.getGlobalDeclarations();
    final int index = prgd.indexOf(pObject);
    logger.logf(logObjects, "removing global [%d]: %s", index, pObject.getSecond());
    assert prgd.remove(pObject);
    assert !prgd.contains(pObject);
    pParseResult =
        new ParseResult(
            pParseResult.getFunctions(),
            pParseResult.getCFANodes(),
            prgd,
            pParseResult.getFileNames());
    return Pair.of(index, pObject);
  }

  @Override
  protected void returnObject(
      ParseResult pParseResult, Pair<Integer, Pair<ADeclaration, String>> pRollbackInfo) {
    List<Pair<ADeclaration, String>> prgd = pParseResult.getGlobalDeclarations();
    int index = pRollbackInfo.getFirst();
    Pair<ADeclaration, String> pair = pRollbackInfo.getSecond();
    logger.logf(logObjects, "returning global [%d]: %s", index, pair.getSecond());
    assert !prgd.contains(pRollbackInfo.getSecond());
    prgd.add(index, pair);
    assert prgd.indexOf(pRollbackInfo.getSecond()) == pRollbackInfo.getFirst();
    pParseResult =
        new ParseResult(
            pParseResult.getFunctions(),
            pParseResult.getCFANodes(),
            prgd,
            pParseResult.getFileNames());
  }
}
