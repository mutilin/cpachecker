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

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.postprocessing.function.ThreadCreateTransformer.ThreadFinder;
import org.sosy_lab.cpachecker.util.CFATraversal;

@Options
public class ThreadCreateStrategy extends GenericCFAMutationStrategy<CFAEdge, CFAEdge> {

  @Option(
      secure = true,
      name = "analysis.threadOperationsTransform",
      description =
          "Replace thread creation operations with a special function calls"
              + "so, any analysis can go through the function")
  private boolean enableThreadOperationsInstrumentation = false;

  @Option(
      secure = true,
      name = "cfa.threads.threadCreate",
      description = "A name of thread_create function")
  private String threadCreate = "pthread_create";

  @Option(
      secure = true,
      name = "cfa.threads.threadSelfCreate",
      description = "A name of thread_create_N function")
  private String threadCreateN = "pthread_create_N";

  /*
   * @Option( secure = true, name = "cfa.threads.threadJoin", description =
   * "A name of thread_join function") private String threadJoin = "pthread_join";
   *
   * @Option( secure = true, name = "cfa.threads.threadSelfJoin", description =
   * "A name of thread_join_N function") private String threadJoinN = "pthread_join_N";
   */

  public ThreadCreateStrategy(Configuration pConfig, LogManager pLogger)
      throws InvalidConfigurationException {
    super(pConfig, pLogger, "Thread creations");
    pConfig.inject(this);
    if (!enableThreadOperationsInstrumentation) {
      throw new InvalidConfigurationException("ThreadCreateStrategy is useless with disabled thread operations instrumentation");
    }
  }

  @Override
  protected Collection<CFAEdge> getAllObjects(ParseResult pParseResult) {
    String dummy = "dummy_thread_join_function";
    ThreadFinder vis = new ThreadFinder(threadCreate, threadCreateN, dummy, dummy);
    for (CFANode entry : pParseResult.getFunctions().values()) {
      CFATraversal.dfs().traverseOnce(entry, vis);
    }

    return vis.getThreadCreates();
  }

  @Override
  protected CFAEdge removeObject(ParseResult pParseResult, CFAEdge pEdge) {
    CFunctionCall statement = (CFunctionCall) ((CStatementEdge) pEdge).getStatement();
    CFunctionCallExpression tCallExp = statement.getFunctionCallExpression();
    CStatement fCallSt = prepareFunctionCallStatement(pParseResult, pEdge, tCallExp);
    logger.logf(logObjects, "replacing %s with %s", tCallExp, fCallSt);

    CFANode predecessor = pEdge.getPredecessor();
    CFANode successor = pEdge.getSuccessor();
    FileLocation fileLocation = pEdge.getFileLocation();
    String rawStatement = pEdge.getRawStatement();

    CStatementEdge newEdge =
        new CStatementEdge(rawStatement, fCallSt, fileLocation, predecessor, successor);

    disconnectEdge(pEdge);
    connectEdge(newEdge);
    return pEdge;
  }

  private CFunctionCallStatement prepareFunctionCallStatement(
      ParseResult pParseResult, CFAEdge pEdge, CFunctionCallExpression pCall) {
    List<CExpression> args = pCall.getParameterExpressions();
    if (args.size() != 4) {
      throw new UnsupportedOperationException("More arguments expected: " + pCall);
    }

    CExpression calledFunction = args.get(2);
    CIdExpression functionNameExpression = getFunctionName(calledFunction);
    List<CExpression> functionParameters = Lists.newArrayList(args.get(3));
    String newThreadName = functionNameExpression.getName();
    CFunctionEntryNode entryNode =
        (CFunctionEntryNode) pParseResult.getFunctions().get(newThreadName);
    if (entryNode == null) {
      throw new UnsupportedOperationException(
          "Can not find the body of function " + newThreadName + "(), full line: " + pEdge);
    }

    CFunctionDeclaration functionDeclaration = entryNode.getFunctionDefinition();
    FileLocation pFileLocation = pEdge.getFileLocation();

    CFunctionCallExpression fCallExp =
        new CFunctionCallExpression(
            pFileLocation,
            functionDeclaration.getType().getReturnType(),
            functionNameExpression,
            functionParameters,
            functionDeclaration);
    return new CFunctionCallStatement(pFileLocation, fCallExp);
  }

  @Override
  protected void returnObject(ParseResult pParseResult, CFAEdge pRollbackInfo) {
    CFANode predecessor = pRollbackInfo.getPredecessor();
    CFANode successor = pRollbackInfo.getSuccessor();
    logger.logf(logObjects, "returning %s", pRollbackInfo);

    disconnectEdge(predecessor.getEdgeTo(successor));
    connectEdge(pRollbackInfo);
  }

  private CIdExpression getFunctionName(CExpression fName) {
    if (fName instanceof CIdExpression) {
      return (CIdExpression) fName;
    } else if (fName instanceof CUnaryExpression) {
      return getFunctionName(((CUnaryExpression) fName).getOperand());
    } else if (fName instanceof CCastExpression) {
      return getFunctionName(((CCastExpression) fName).getOperand());
    } else {
      throw new UnsupportedOperationException("Unsupported expression in pthread_create: " + fName);
    }
  }
}
