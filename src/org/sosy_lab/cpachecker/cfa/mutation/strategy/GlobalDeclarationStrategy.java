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
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.FunctionCallCollector;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexTypeDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CDesignatedInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeDefDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
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
import org.sosy_lab.cpachecker.cfa.types.c.CFunctionType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CProblemType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cfa.types.c.CTypedefType;
import org.sosy_lab.cpachecker.cfa.types.c.CVoidType;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFATraversal.CompositeCFAVisitor;
import org.sosy_lab.cpachecker.util.CFATraversal.TraversalProcess;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.Pair;

public class GlobalDeclarationStrategy
    extends GenericCFAMutationStrategy<Pair<ADeclaration, String>, Pair<Integer, Pair<ADeclaration, String>>> {

  private final Collection<String> calledFunctons = new HashSet<>();
  private final Collection<String> functionTypes = new HashSet<>();
  private final Collection<String> typedefs = new HashSet<>();
  private final Collection<String> compositeTypes = new HashSet<>();
  private final Collection<String> elaboratedTypes = new HashSet<>();
  private final Collection<String> enums = new HashSet<>();
  private final Collection<String> usedVariables = new HashSet<>();

  private static class DeclarationCollectingCFAVisitor extends CFATraversal.DefaultCFAVisitor {
    private final List<ADeclaration> declarations = new ArrayList<>();

    @Override
    public TraversalProcess visitEdge(CFAEdge pEdge) {
      if (pEdge instanceof ADeclarationEdge) {
        declarations.add(((ADeclarationEdge) pEdge).getDeclaration());
      }
      return super.visitEdge(pEdge);
    }

    public List<ADeclaration> getVisitedDeclarations() {
      return declarations;
    }
  }

  private static class UsedNamesCollector extends CFATraversal.DefaultCFAVisitor {
    private final Collection<String> names = new HashSet<>();

    public Collection<String> getNames() {
      return names;
    }

    @Override
    public TraversalProcess visitEdge(CFAEdge edge) {
      Collection<CExpression> exps = new ArrayList<>();
      switch (edge.getEdgeType()) {
        case BlankEdge:
          return TraversalProcess.CONTINUE;

        case AssumeEdge:
          exps.add((CExpression) ((AssumeEdge) edge).getExpression());
          break;

        case DeclarationEdge:
          final CInitializer init =
              ((CVariableDeclaration) ((CDeclarationEdge) edge).getDeclaration()).getInitializer();
          if (init == null) {
            return TraversalProcess.CONTINUE;
          }
          exps.add(((CInitializerExpression) init).getExpression());
          // TODO local declarations can hide global names
          break;

        case StatementEdge:
          CStatement stat = ((CStatementEdge) edge).getStatement();
          if (stat instanceof AExpressionStatement) {
            exps.add(((CExpressionStatement) stat).getExpression());
          } else if (stat instanceof CExpressionAssignmentStatement) {
            exps.add(((CExpressionAssignmentStatement) stat).getLeftHandSide());
            exps.add(((CExpressionAssignmentStatement) stat).getRightHandSide());
          } else if (stat instanceof CFunctionCallStatement) {
            final CFunctionCallExpression fCall =
                ((CFunctionCallStatement) stat).getFunctionCallExpression();
            exps.add(fCall.getFunctionNameExpression());
            exps.addAll(fCall.getParameterExpressions());
          } else if (stat instanceof CFunctionCallAssignmentStatement) {
            exps.add(((CFunctionCallAssignmentStatement) stat).getLeftHandSide());
            final CFunctionCallExpression fCall =
                ((CFunctionCallAssignmentStatement) stat).getRightHandSide();
            exps.add(fCall.getFunctionNameExpression());
            exps.addAll(fCall.getParameterExpressions());
          }
          break;

        case ReturnStatementEdge:
          CExpression retval = ((CReturnStatementEdge) edge).getExpression().orNull();
          if (retval == null) {
            return TraversalProcess.CONTINUE;
          }
          exps.add(retval);
          break;

        case CallToReturnEdge:
        case FunctionCallEdge:
        case FunctionReturnEdge:
          assert false;
      }

      for (CExpression exp : exps) {
        CFAUtils.getVariableNamesOfExpression(exp).forEach(n -> names.add(n));
      }
      return TraversalProcess.CONTINUE;
    }
  }

  public GlobalDeclarationStrategy(LogManager pLogger, int pAtATime, boolean ptryAllAtFirst) {
    super(pLogger, pAtATime, ptryAllAtFirst, "Global declarations");
  }

  @Override
  protected boolean canRemove(ParseResult pParseResult, Pair<ADeclaration, String> p) {
    final String first = p.getFirst().toString().replaceAll("\\s+", " ").replaceAll(" ;", ";");
    final String second =
        p.getSecond()
            .replaceAll("\\s+", " ")
            .replaceAll(" ;", ";")
            .replaceAll("\\(void\\)", "\\(\\)")
            .replaceAll(" ,", ",")
            .replaceAll(" \\)", "\\)");

    if (!super.canRemove(pParseResult, p)) {
      logger.logf(Level.INFO, "can not remove %s", second);
      return false;
    }

    CDeclaration decl = (CDeclaration) p.getFirst();
    String name = decl.getName();

    if (decl instanceof CFunctionDeclaration) {
      logger.logf(Level.FINE, "\tit is function %s", name);
      return !calledFunctons.contains(name);

    } else if (decl instanceof CVariableDeclaration) {
      logger.logf(Level.FINE, "\tit is variable %s", name);
      return !usedVariables.contains(decl.getName());

    } else if (decl instanceof CTypeDefDeclaration) {
      logger.logf(Level.FINE, "\tit is typedef %s", name);
      return !typedefs.contains(name);

    } else if (decl instanceof CComplexTypeDeclaration) {
      logger.logf(Level.INFO, "canRemove got %s", first);
      if (!first.equals(second)) {
        logger.logf(Level.INFO, "\tstring is %s", second);
      }

      CComplexType type = ((CComplexTypeDeclaration) decl).getType();
      name = type.getName();
      if (type instanceof CCompositeType) {
        logger.logf(Level.INFO, "\tit is composite type %s", name);
        return !compositeTypes.contains(name);

      } else if (type instanceof CElaboratedType) {
        logger.logf(Level.INFO, "\tit is elaborated type %s", name);
        return !elaboratedTypes.contains(name);

      } else if (type instanceof CEnumType) {
        logger.logf(Level.INFO, "\tit is enum %s", name);
        return !enums.contains(name);

      } else {
        throw new UnsupportedOperationException(
            "Got declaration of type that is instance of " + type.getClass());
      }

    } else {
      throw new UnsupportedOperationException("Got declaration instance of " + decl.getClass());
    }
  }

  @Override
  protected Collection<Pair<ADeclaration, String>> getAllObjects(ParseResult pParseResult) {
    calledFunctons.clear();
    functionTypes.clear();
    typedefs.clear();
    compositeTypes.clear();
    elaboratedTypes.clear();
    enums.clear();
    usedVariables.clear();

    final FunctionCallCollector fCallCollector = new FunctionCallCollector();
    final DeclarationCollectingCFAVisitor declCollector = new DeclarationCollectingCFAVisitor();
    final UsedNamesCollector namesCollector = new UsedNamesCollector();
    final CompositeCFAVisitor visitor =
        new CompositeCFAVisitor(fCallCollector, declCollector, namesCollector);
    for (FunctionEntryNode startingNode : pParseResult.getFunctions().values()) {
      CFATraversal.dfs().traverseOnce(startingNode, visitor);
    }

    calledFunctons.addAll(pParseResult.getFunctions().keySet());
    for (AStatementEdge c : fCallCollector.getFunctionCalls()) {
      CFunctionDeclaration decl =
          ((CFunctionCall) c.getStatement()).getFunctionCallExpression().getDeclaration();
      if (decl != null) {
        final String name = decl.getName();
        logger.logf(Level.FINE, "saving function %s", name);
        calledFunctons.add(name);
        CFunctionType fType = decl.getType();
        saveType(fType);
      }
    }

    for (ADeclaration d : declCollector.getVisitedDeclarations()) {
      CType type = ((CVariableDeclaration) d).getType();
      saveType(type);
    }

    ListIterator<Pair<ADeclaration, String>> globals =
        pParseResult
            .getGlobalDeclarations()
            .listIterator(pParseResult.getGlobalDeclarations().size());
    while (globals.hasPrevious()) {
      CDeclaration decl = (CDeclaration) globals.previous().getFirst();
      if (decl instanceof CVariableDeclaration) {
        final String name = decl.getName();
        if (namesCollector.getNames().contains(name)) {
          logger.logf(Level.FINE, "saving global var %s", name);
          usedVariables.add(name);
        }
        if (usedVariables.contains(name)) {
          saveType(decl.getType());
          saveInit(((CVariableDeclaration) decl).getInitializer());
        }
      }
    }

    List<Pair<ADeclaration, String>> answer = new ArrayList<>();
    for (Pair<ADeclaration, String> p : pParseResult.getGlobalDeclarations()) {
      if (canRemove(pParseResult, p)) {
        answer.add(0, p);
      }
    }
    return answer;
  }

  private void saveInit(CInitializer pInit) {
    logger.logf(Level.INFO, "got %s", pInit);
    // TODO some functions might not be called, but used as field inits, etc.

    if (pInit instanceof CDesignatedInitializer) {
      logger.logf(Level.FINE, "\tit is designated initializer");
      // TODO

    } else if (pInit instanceof CInitializerExpression) {
      logger.logf(Level.FINE, "\tit is initializer expression");
      // TODO

    } else if (pInit instanceof CInitializerList) {
      logger.logf(Level.FINE, "\tit is initializer list");
      ((CInitializerList) pInit).getInitializers().forEach(i -> saveInit(i));

    } else {
      throw new UnsupportedOperationException("Got CInitializer instance of " + pInit.getClass());
    }
  }

  private void saveType(CType pType) {
    logger.logf(Level.INFO, "saveType got type %s", pType);

    if (pType instanceof CArrayType) {
      logger.logf(Level.FINE, "\tit is array");
      saveType(((CArrayType) pType).getType());

    } else if (pType instanceof CBitFieldType) {
      logger.logf(Level.FINE, "\tit is bit field");
      saveType(((CBitFieldType) pType).getType());

    } else if (pType instanceof CPointerType) {
      logger.logf(Level.FINE, "\tit is pointer");
      saveType(((CPointerType) pType).getType());

    } else if (pType instanceof CProblemType) {
      logger.logf(Level.FINE, "\tit is problem, nothing to do");

    } else if (pType instanceof CSimpleType) {
      logger.logf(Level.FINE, "\tit is simple type, nothing to do");

    } else if (pType instanceof CVoidType) {
      logger.logf(Level.FINE, "\tit is void, nothing to do");

    } else if (pType instanceof CFunctionType) {
      logger.logf(Level.FINE, "\tit is function type");
      final CFunctionType fType = ((CFunctionType) pType);
      final String name = fType.getName();
      if (name != null) {
        logger.logf(Level.FINE, "\t\tadding name %s", name);
        if (!functionTypes.add(name)) {
          return;
        }
      }
      CType rType = fType.getReturnType();
      saveType(rType);
      for (CType type : fType.getParameters()) {
        saveType(type);
      }

    } else if (pType instanceof CTypedefType) {
      logger.logf(Level.FINE, "\tit is typedef");
      final String name = ((CTypedefType) pType).getName();
      logger.logf(Level.FINE, "\t\tadding name %s", name);
      if (!typedefs.add(name)) {
        return;
      }
      saveType(((CTypedefType) pType).getRealType());

    } else if (pType instanceof CCompositeType) {
      logger.logf(Level.INFO, "\tit is composite type");
      final String name = ((CComplexType) pType).getName();
      if (name != null && !name.isEmpty()) {
        logger.logf(Level.INFO, "\tadding name %s", name);
        if (!compositeTypes.add(name)) {
          return;
        }
      }
      logger.logf(Level.INFO, "\tit is composite type");
      CCompositeType composite = (CCompositeType) pType;
      List<CCompositeTypeMemberDeclaration> members = composite.getMembers();
      logger.logf(Level.INFO, "\tmembers: %s", members.isEmpty() ? "empty" : members);
      for (CCompositeTypeMemberDeclaration member : members) {
        logger.logf(Level.INFO, "\tmember %s", member);
        saveType(member.getType());
      }

    } else if (pType instanceof CElaboratedType) {
      logger.logf(Level.INFO, "\tit is elaborated type");
      final String name = ((CElaboratedType) pType).getName();
      if (name != null && !name.isEmpty()) {
        logger.logf(Level.INFO, "\tadding name %s", name);
        if (!elaboratedTypes.add(name)) {
          return;
        }
      }
      @Nullable CComplexType realType = ((CElaboratedType) pType).getRealType();
      if (realType != null) {
        saveType(realType);
      }

    } else if (pType instanceof CEnumType) {
      logger.logf(Level.INFO, "\tit is enum type");
      final String name = ((CEnumType) pType).getName();
      if (name != null && !name.isEmpty()) {
        logger.logf(Level.INFO, "\tadding name %s", name);
        if (!enums.add(name)) {
          return;
        }
      }

    } else {
      throw new UnsupportedOperationException(
          "Got wrong CType, instanse of " + pType.getClass().getName());
    }
  }

  @Override
  protected Pair<Integer, Pair<ADeclaration, String>> getRollbackInfo(
      ParseResult pParseResult, Pair<ADeclaration, String> pObject) {
    return Pair.of(pParseResult.getGlobalDeclarations().indexOf(pObject), pObject);
  }

  @Override
  protected void removeObject(ParseResult pParseResult, Pair<ADeclaration, String> pObject) {
    List<Pair<ADeclaration, String>> prgd = pParseResult.getGlobalDeclarations();
    assert prgd.remove(pObject);
    assert !prgd.contains(pObject);
    pParseResult =
        new ParseResult(
            pParseResult.getFunctions(),
            pParseResult.getCFANodes(),
            prgd,
            pParseResult.getFileNames());
  }

  @Override
  protected void returnObject(
      ParseResult pParseResult, Pair<Integer, Pair<ADeclaration, String>> pRollbackInfo) {
    List<Pair<ADeclaration, String>> prgd = pParseResult.getGlobalDeclarations();
    assert !prgd.contains(pRollbackInfo.getSecond());
    prgd.add(pRollbackInfo.getFirst(), pRollbackInfo.getSecond());
    assert prgd.indexOf(pRollbackInfo.getSecond()) == pRollbackInfo.getFirst();
    pParseResult =
        new ParseResult(
            pParseResult.getFunctions(),
            pParseResult.getCFANodes(),
            prgd,
            pParseResult.getFileNames());
  }
}
