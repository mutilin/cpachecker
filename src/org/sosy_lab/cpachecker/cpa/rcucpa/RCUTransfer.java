/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.rcucpa;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.FileOption.Type;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.IdentifierCreator;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

@Options(prefix = "cpa.rcucpa")
public class RCUTransfer extends SingleEdgeTransferRelation{

  @Option(name = "readLock", secure = true, description = "Name of a function responsible for "
      + "acquiring the RCU read lock")
  private String readLockName = "ldv_rcu_read_lock";

  @Option(name = "readUnlock", secure = true, description = "Name of a function responsible for "
      + "releasing the RCU read lock")
  private String readUnlockName = "ldv_rcu_read_unlock";

  @Option(name = "sync", secure = true, description = "Name of a function responsible for "
      + "the handling of a grace period")
  private String sync = "ldv_synchronize_rcu";

  @Option(name = "assign", secure = true, description = "Name of a function responsible for "
      + "assignment to RCU pointers")
  private String assign = "ldv_rcu_assign_pointer";

  @Option(name = "deref", secure = true, description = "Name of a function responsible for "
      + "dereferences of RCU pointers")
  private String deref = "ldv_rcu_dereference";

  @Option(name = "fictReadLock", secure = true, description = "Name of a function marking a call "
      + "to a fictional read lock of RCU pointer")
  private String fictReadLock = "ldv_rlock_rcu";

  @Option(name = "fictReadUnlock", secure = true, description = "Name of a function marking a call "
      + "to a fictional read unlock of RCU pointer")
  private String fictReadUnlock = "ldv_runlock_rcu";

  @Option(name = "fictWriteLock", secure = true, description = "Name of a function marking a call "
      + "to a fictional write lock of RCU pointer")
  private String fictWriteLock = "ldv_wlock_rcu";

  @Option(name = "fictWriteUnlock", secure = true, description = "Name of a function marking a "
      + "call to a fictional write unlock of RCU pointer")
  private String fictWriteUnlock = "ldv_wunlock_rcu";

  @Option(name = "free", secure = true, description = "Name of a free function")
  private String free = "ldv_free";

  @Option(name = "rcuPointersFile", secure = true, description = "Name of a file containing RCU "
      + "pointers")
  @FileOption(Type.OUTPUT_FILE)
  private Path input = Paths.get("RCUPointers");

  private final LogManager logger;
  private final Set<MemoryLocation> rcuPointers;

  RCUTransfer(Configuration pConfig, LogManager pLogger)
      throws InvalidConfigurationException {
    logger = pLogger;
    pConfig.inject(this);
    rcuPointers = parseFile(input);
  }

  private Set<MemoryLocation> parseFile(Path pInput) {
    Set<MemoryLocation> result = new HashSet<>();
    try (Reader reader = Files.newBufferedReader(pInput, Charset.defaultCharset())) {
      Gson builder = new Gson();
      java.lang.reflect.Type type = new TypeToken<Set<MemoryLocation>>() {}.getType();
      result.addAll(builder.fromJson(reader, type));
      logger.log(Level.INFO, "Finished reading from file " + input);
    } catch (IOException pE) {
      logger.log(Level.WARNING, pE.getMessage());
    }
    logger.log(Level.WARNING, "result contents: " + result);
    return result;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState state, Precision precision, CFAEdge cfaEdge)
      throws CPATransferException, InterruptedException {
    RCUState result = (RCUState) state;

    logger.log(Level.ALL, "EDGE: " + cfaEdge + " " + cfaEdge.getEdgeType());

    switch (cfaEdge.getEdgeType()) {
      case DeclarationEdge:
        result = handleDeclaration(((CDeclarationEdge) cfaEdge).getDeclaration(), result,
                            cfaEdge.getPredecessor().getFunctionName());
        break;
      case StatementEdge:
        CStatement statement = ((CStatementEdge) cfaEdge).getStatement();
        if (statement instanceof CExpressionAssignmentStatement) {
          result = handleAssignmentStatement((CExpressionAssignmentStatement) statement, result,
                            cfaEdge.getPredecessor().getFunctionName());
        } else if (statement instanceof CFunctionCallAssignmentStatement) {
          result = handleFunctionCallAssignmentStatement((CFunctionCallAssignmentStatement)
              statement, result, cfaEdge.getPredecessor().getFunctionName());
        } else if (statement instanceof CFunctionCallStatement){
          result = handleFunctionCallStatement(((CFunctionCallStatement) statement).getFunctionCallExpression(),
              result, cfaEdge.getPredecessor().getFunctionName());
        }
        break;
      case FunctionCallEdge:
        CFunctionCallExpression callExpression =
            ((CFunctionCallEdge) cfaEdge).getSummaryEdge().getExpression().getFunctionCallExpression();
        result = handleFunctionCallStatement(callExpression, result, cfaEdge.getPredecessor().getFunctionName());
        break;
      case FunctionReturnEdge:
        // result = handleFunctionReturn(((CFunctionReturnEdge) cfaEdge).getPredecessor().getFunctionName(), result);
      case ReturnStatementEdge:
        break;
      case CallToReturnEdge:
      case AssumeEdge:
      case BlankEdge:
        break;
      default:
        throw new UnrecognizedCFAEdgeException(cfaEdge);
    }

    logger.log(Level.ALL, "RESULT: " + result);
    return Collections.singleton(result);
  }
/*
  private RCUState handleFunctionReturn(String pFunctionName, RCUState pState) {
    boolean rcuRelevant = pFunctionName.contains(readLockName);
    rcuRelevant |= pFunctionName.contains(readUnlockName);
    rcuRelevant |= pFunctionName.equals(fictReadLock);
    rcuRelevant |= pFunctionName.equals(fictReadUnlock);
    rcuRelevant |= pFunctionName.equals(fictWriteLock);
    rcuRelevant |= pFunctionName.equals(fictWriteUnlock);
    rcuRelevant |= pFunctionName.equals(sync);
    rcuRelevant |= pFunctionName.equals(assign);
    rcuRelevant |= pFunctionName.equals(deref);
    rcuRelevant |= pFunctionName.equals(free);

    if (!rcuRelevant) {
      logger.log(Level.ALL, "POPPING STATE. FUNC: " + pFunctionName);
      RCUState result = stateStack.pop();
      logger.log(Level.ALL, "State: " + result);
      return result;
    } else {
      return pState;
    }
  }
*/
  private RCUState handleAssignment(CExpression left, CExpression right,
                                    String functionName,
                                    RCUState state,
                                    boolean twoSided, boolean invalidates) {
    IdentifierCreator localIc = new IdentifierCreator(functionName);
    AbstractIdentifier rcuPtr, ptr;
    RCUState result = state;

    rcuPtr = left.accept(localIc);

    ptr = right.accept(localIc);

    if (rcuPointers.contains(LocationIdentifierConverter.toLocation(rcuPtr)) ||
        rcuPointers.contains(LocationIdentifierConverter.toLocation(ptr))) {

      logger.log(Level.ALL, "ASSIGN: " + rcuPtr + " " + ptr);
      logger.log(Level.ALL, "State: " + state);
      AbstractIdentifier nonTemporaryId = result.getNonTemporaryId(rcuPtr);
      AbstractIdentifier leftPtr = rcuPtr;
      if (nonTemporaryId != null) {
        leftPtr = nonTemporaryId;
      }
      nonTemporaryId = result.getNonTemporaryId(rcuPtr);
      AbstractIdentifier rightPtr = ptr;
      if (nonTemporaryId != null) {
        rightPtr = nonTemporaryId;
      }

      result = addTmpMappingIfNecessary(rcuPtr, ptr, result);
      result = addTmpMappingIfNecessary(ptr, rcuPtr, result);

      if (invalidates) {
        result = result.addToOutdated(leftPtr);
      }
      result = result.addToRelations(leftPtr, rightPtr);
      if (twoSided) {
        result = result.addToRelations(rightPtr, leftPtr);
      }
      if (!leftPtr.equals(rcuPtr)) {
        result = result.addToRelations(rcuPtr, rightPtr);
      }

    }
    return result;
  }

  private RCUState addTmpMappingIfNecessary(
      AbstractIdentifier tmpVar,
      AbstractIdentifier nonTmpVar,
      RCUState pResult) {
    if (tmpVar instanceof SingleIdentifier &&
        ((SingleIdentifier) tmpVar).getName().contains("CPAchecker_TMP")) {
      pResult = pResult.addTmpMapping(tmpVar, nonTmpVar);
    }
    return pResult;
  }

  private RCUState handleFunctionCallStatement(CFunctionCallExpression pCallExpression,
                                               RCUState state,
                                               String pFunctionName) {
    CFunctionDeclaration fd = pCallExpression.getDeclaration();
    RCUState result = state;

    if (fd != null) {
      String fName = fd.getName();

      if (fName.contains(readLockName)) {
        result = result.incRCURead();
      } else if (fName.contains(readUnlockName)) {
        result = result.decRCURead();
      } else if (fName.equals(fictReadLock)) {
        result = result.markRead();
      } else if (fName.equals(fictWriteLock)) {
        result = result.markWrite();
      } else if (fName.equals(fictReadUnlock) || fName.equals(fictWriteUnlock)) {
        result = result.clearLock();
      } else if (fName.equals(sync)) {
        result = result.fillLocal();
      } else if (fName.equals(assign)) {
        CExpression rcuPtr = pCallExpression.getParameterExpressions().get(0);
        CExpression ptr = pCallExpression.getParameterExpressions().get(1);

        result = handleAssignment(rcuPtr, ptr, pFunctionName, state, true, true);

      }/* else if ( ! fName.equals(free) && ! fName.equals(deref)){
        logger.log(Level.ALL, "1 PUSHING STATE. FUNC: " + fName);
        RCUState toPush = RCUState.copyOf(state);
        logger.log(Level.ALL, "State: " + toPush);
        stateStack.push(toPush);
      }*/
    }
    return result;
  }

  private RCUState handleFunctionCallAssignmentStatement(CFunctionCallAssignmentStatement assignment,
                                                         RCUState pResult,
                                                         String functionName) {
    // This case is covered by the normal assignment expression
    CFunctionDeclaration functionDeclaration = assignment.getFunctionCallExpression().getDeclaration();
    if (functionDeclaration != null && functionDeclaration.getName().equals(deref)) {
      return handleAssignment(assignment.getLeftHandSide(), assignment.getFunctionCallExpression()
          .getParameterExpressions().get(0), functionName, pResult, false, false);
    }
    return pResult;
  }

  private RCUState handleAssignmentStatement(CExpressionAssignmentStatement assignment,
                                         RCUState pResult,
                                         String functionName) {
    CLeftHandSide leftHandSide = assignment.getLeftHandSide();
    CExpression rightHandSide = assignment.getRightHandSide();
    if (leftHandSide instanceof CPointerExpression || leftHandSide instanceof CFieldReference ||
        rightHandSide instanceof CPointerExpression || rightHandSide instanceof CFieldReference) {
      return handleAssignment(leftHandSide, rightHandSide, functionName,
          pResult, false, false);
    }
    return pResult;
  }

  private RCUState handleDeclaration(CDeclaration pDeclaration,
                                 RCUState pResult,
                                 String pFunctionName) {
    IdentifierCreator localIc = new IdentifierCreator(pFunctionName);

    if (pDeclaration != null && pDeclaration instanceof CVariableDeclaration) {
      CVariableDeclaration var = (CVariableDeclaration) pDeclaration;
      AbstractIdentifier ail = IdentifierCreator.createIdentifier(var, pFunctionName, 0);

      if (ail != null && ail.isPointer()) {
        if (rcuPointers.contains(LocationIdentifierConverter.toLocation(ail))) {
          CInitializer initializer = ((CVariableDeclaration) pDeclaration).getInitializer();
          if (initializer != null && initializer instanceof CInitializerExpression) {
            AbstractIdentifier init =
                ((CInitializerExpression) initializer).getExpression().accept(localIc);
            pResult = addTmpMappingIfNecessary(ail, init, pResult);
            pResult = pResult.addToRelations(ail, init);
          } else {
            pResult = pResult.addToRelations(ail, null);
          }
        }
      }
    }
    return pResult;
  }

}
